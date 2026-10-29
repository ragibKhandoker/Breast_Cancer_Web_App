"""Download and organise CBIS-DDSM full mammograms for training and evaluation.

Images:   dbaek111/CBIS-DDSM_1024 on Hugging Face - 3,086 full CBIS-DDSM mammograms (CC and MLO), 8-bit PNG,
          long side 1024 px, named by DICOM series UID.
Labels:   FLARE25-Agent-Xray/CBIS-DDSM dataset_detailed.csv - patient id, breast, view, pathology and the official
          CBIS-DDSM train/test split for every image, joined on the series UID.
Licence:  CBIS-DDSM is CC BY 3.0 (TCIA, https://doi.org/10.7937/K9/TCIA.2016.7O02S9CY); cite it. Research and
          education only.

Run:      python prepare_cbis.py              -> data/cbis/images/*.png and data/cbis/manifest.csv

Splits are by patient, so no patient's images are on both sides:
  test  = the official CBIS-DDSM test set, minus patients who also appear in the official training set
  val   = about 15% of the remaining training patients (used to pick the epoch and the decision threshold)
  train = the rest
Labels: MALIGNANT -> malignant; BENIGN and BENIGN_WITHOUT_CALLBACK -> benign.
"""
import argparse
import csv
import hashlib
import random
import urllib.request
import zipfile
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).parent
RAW = ROOT / "data" / "raw"
OUT = ROOT / "data" / "cbis"
SOURCES = {
    "CBIS-DDSM_full_1024.zip": (
        "https://huggingface.co/datasets/dbaek111/CBIS-DDSM_1024/resolve/main/CBIS-DDSM_full_1024.zip",
        "123239b7f68c3a309b33784aff0a7f91ff9200edc5a2f76c4507ce96cc7c0e53",
    ),
    "dataset_detailed.csv": (
        "https://huggingface.co/datasets/FLARE25-Agent-Xray/CBIS-DDSM/resolve/main/dataset_detailed.csv",
        "bbe8a5f28dacaac065d68720e3f2cb0732e8a190438e0275fe978a4388a6cdd4",
    ),
}
VAL_FRACTION = 0.15
SEED = 20261028


def sha256(path):
    digest = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def fetch(name):
    url, expected = SOURCES[name]
    target = RAW / name
    if not target.exists():
        print(f"downloading {name} ...")
        RAW.mkdir(parents=True, exist_ok=True)
        urllib.request.urlretrieve(url, target)
    if sha256(target) != expected:
        raise SystemExit(f"{target}: checksum mismatch. Delete it and run again.")
    return target


def dicom_uids(original_path):
    # "Mass-Training_P_00001_LEFT_CC/<study uid>/<series uid>/000000.dcm"; the PNGs are named by one of the two.
    return original_path.strip().split("/")[1:3]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--zip", type=Path, help="already downloaded CBIS-DDSM_full_1024.zip")
    parser.add_argument("--csv", type=Path, help="already downloaded dataset_detailed.csv")
    args = parser.parse_args()
    for path, name in ((args.zip, "CBIS-DDSM_full_1024.zip"), (args.csv, "dataset_detailed.csv")):
        if path:
            RAW.mkdir(parents=True, exist_ok=True)
            if not (RAW / name).exists():
                (RAW / name).write_bytes(path.read_bytes())

    meta = {}
    with open(fetch("dataset_detailed.csv"), newline="", encoding="utf-8") as f:
        for row in csv.DictReader(f):
            for uid in dicom_uids(row["original_path"]):
                meta[uid] = row

    images = OUT / "images"
    images.mkdir(parents=True, exist_ok=True)
    records = []
    with zipfile.ZipFile(fetch("CBIS-DDSM_full_1024.zip")) as archive:
        for info in archive.infolist():
            if info.is_dir() or not info.filename.endswith(".png"):
                continue
            split, folder, filename = info.filename.split("/")
            uid = filename.rsplit("_", 1)[0]
            row = meta.get(uid)
            if row is None:
                raise SystemExit(f"no metadata for {info.filename}")
            label = 1 if row["pathology"] == "MALIGNANT" else 0
            if label != (folder == "cancer") or split != row["split"]:
                raise SystemExit(f"label or split disagrees with the metadata for {info.filename}")
            target = images / f"{uid}.png"
            if not target.exists():
                target.write_bytes(archive.read(info))
            records.append({
                "file": f"images/{uid}.png",
                "patient_id": row["patient_id"],
                "breast": row["breast"],
                "view": row["view"],
                "abnormality": row["abnormality_type"],
                "pathology": row["pathology"],
                "label": label,
                "official_split": split,
            })

    train_patients = {r["patient_id"] for r in records if r["official_split"] == "train"}
    dropped = {r["patient_id"] for r in records if r["official_split"] == "test"} & train_patients

    # Validation: whole patients, drawn separately from patients with and without a malignant image.
    by_patient = defaultdict(list)
    for r in records:
        if r["official_split"] == "train":
            by_patient[r["patient_id"]].append(r)
    rng = random.Random(SEED)
    val_patients = set()
    for has_cancer in (True, False):
        group = sorted(p for p, rs in by_patient.items() if any(r["label"] for r in rs) == has_cancer)
        rng.shuffle(group)
        val_patients.update(group[: round(len(group) * VAL_FRACTION)])

    for r in records:
        if r["official_split"] == "test":
            r["split"] = "excluded" if r["patient_id"] in dropped else "test"
        else:
            r["split"] = "val" if r["patient_id"] in val_patients else "train"

    with open(OUT / "manifest.csv", "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=list(records[0]))
        writer.writeheader()
        writer.writerows(sorted(records, key=lambda r: (r["split"], r["patient_id"], r["file"])))

    print(f"{len(records)} images -> {OUT / 'manifest.csv'}")
    for split in ("train", "val", "test", "excluded"):
        rs = [r for r in records if r["split"] == split]
        patients = {r["patient_id"] for r in rs}
        views = {v: sum(r["view"] == v for r in rs) for v in ("CC", "MLO")}
        print(f"  {split:8s} {len(rs):5d} images  {len(patients):4d} patients  malignant {sum(r['label'] for r in rs):4d}  {views}")
    sides = [{r["patient_id"] for r in records if r["split"] == s} for s in ("train", "val", "test")]
    assert not (sides[0] & sides[1] or sides[0] & sides[2] or sides[1] & sides[2]), "a patient is in two splits"
    print("  no patient appears in more than one split")


if __name__ == "__main__":
    main()
