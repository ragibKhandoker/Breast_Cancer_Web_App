"""Test ianpan/mammoscreen on CBIS-DDSM test patients and export it to ONNX for the backend. Runs on Kaggle (GPU).

Run inside kaggle_mammoscreen.ipynb (built by make_mammoscreen_notebook.py), with the Kaggle dataset
awsaf49/cbis-ddsm-breast-cancer-image-dataset attached (full-resolution CBIS-DDSM JPEGs, original aspect ratio).

Writes to weights/mammoscreen/:
  crop.onnx, net0.onnx, net1.onnx, net2.onnx   exported models (input 1x1xHxW float 0-255, output P(cancer))
  mammoscreen_metrics.json                     test results for each combination of nets, overall/CC/MLO, and
                                               per breast (CC+MLO averaged), with 95% intervals
  fixtures/                                    images and expected outputs for the Java parity tests

Test set: the official CBIS-DDSM test split minus patients who also appear in the training split (the same rule
as prepare_cbis.py). Labels: MALIGNANT -> 1, BENIGN / BENIGN_WITHOUT_CALLBACK -> 0. The decision threshold for
each combination is chosen on a sample of CBIS-DDSM training images, never on the test images.

Caveat recorded in the metrics: mammoscreen was pretrained on CBIS-DDSM, and its card does not say whether the
test split was held out, so these CBIS numbers may be optimistic. Its own RSNA holdout AUC is 0.945.
"""
import csv
import glob
import json
import os
import random
import re
import time
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path

import cv2
import numpy as np
import torch

import metrics
from mammoscreen_pipeline import NETS, crop_box, crop_input, net_input

SCREEN_REPO, SCREEN_REV = "ianpan/mammoscreen", "b619a889a8214f3b3f9e7427c2603608a44e6d0a"
CROP_REPO, CROP_REV = "ianpan/mammo-crop", "bec2e302d47176d743a86d276d8402f876ad0f9f"
OUT = Path("weights/mammoscreen")
COMBOS = {"ensemble3": ["net0", "net1", "net2"], "net0+net1": ["net0", "net1"], "net0": ["net0"]}
THRESHOLD_SAMPLE = 400
KEY = re.compile(r"^(Mass|Calc)-(Training|Test)_(P_\d+)_(LEFT|RIGHT)_(CC|MLO)$")


def find_dataset_root():
    for candidate in glob.glob("/kaggle/input/**/csv/dicom_info.csv", recursive=True):
        return Path(candidate).parent.parent
    raise SystemExit("Attach the Kaggle dataset awsaf49/cbis-ddsm-breast-cancer-image-dataset")


def load_cases(root):
    """One record per full mammogram, found through the series UID in the case CSVs' image file path.

    (The dataset's dicom_info.csv leaves SeriesDescription blank for most Calc-Test images, so it cannot be
    used to find full mammograms.)
    """
    files = {}
    with open(root / "csv" / "dicom_info.csv", newline="", encoding="utf-8") as f:
        for row in csv.DictReader(f):
            files[row["SeriesInstanceUID"]] = root / "jpeg" / Path(row["image_path"]).relative_to("CBIS-DDSM/jpeg")
    images = {}
    for path in sorted(glob.glob(str(root / "csv" / "*_case_description_*_set.csv"))):
        with open(path, newline="", encoding="utf-8") as f:
            for row in csv.DictReader(f):
                folder, _study, series = row["image file path"].strip().split("/")[:3]
                match = KEY.match(folder)
                if not match or series not in files:
                    raise SystemExit(f"cannot find the full image for {folder}")
                kind, split, patient, side, view = match.groups()
                label = 1 if row["pathology"] == "MALIGNANT" else 0
                if folder in images:
                    images[folder]["label"] = max(images[folder]["label"], label)
                    continue
                images[folder] = {"key": folder, "path": str(files[series]), "patient_id": patient, "side": side,
                                  "view": view, "kind": kind, "split": split, "label": label}
    return list(images.values())


class CropNet(torch.nn.Module):
    def __init__(self, model):
        super().__init__()
        self.model = model

    def forward(self, x):  # 1x1x256x256, 0-255 -> normalised xywh
        return self.model(x)


class CancerNet(torch.nn.Module):
    def __init__(self, net):
        super().__init__()
        self.net = net

    def forward(self, x):  # 1x1xHxW, 0-255 -> P(cancer), shape (1, 1)
        x = self.net.normalize(x)
        features = self.net.pooling(self.net.backbone(x)).flatten(1)
        return self.net.linear(features)[:, :1].sigmoid()


def read_gray(path):
    img = cv2.imread(path, cv2.IMREAD_GRAYSCALE)
    if img is None:
        raise RuntimeError(f"cannot read {path}")
    return img


@torch.inference_mode()
def score(image_path, crop_net, nets, device):
    gray = read_gray(image_path)
    coords = crop_net(torch.from_numpy(crop_input(gray)).to(device))[0].float().cpu().numpy()
    x, y, w, h = crop_box(coords, *gray.shape[:2])
    cropped = gray[y:y + h, x:x + w]
    return {spec["name"]: float(nets[spec["name"]](torch.from_numpy(net_input(cropped, spec)).to(device))[0, 0])
            for spec in NETS}


def combo_scores(per_net, names):
    return np.array([np.mean([s[n] for n in names]) for s in per_net])


def breast_level(images, scores):
    groups = defaultdict(list)
    for image, s in zip(images, scores):
        groups[(image["patient_id"], image["side"], image["kind"])].append((image, s))
    labels, values, patients = [], [], []
    for (patient, _, _), members in groups.items():
        if {m[0]["view"] for m in members} == {"CC", "MLO"}:
            labels.append(max(m[0]["label"] for m in members))
            values.append(float(np.mean([m[1] for m in members])))
            patients.append(patient)
    return labels, values, patients


def main():
    from transformers import AutoModel

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    OUT.mkdir(parents=True, exist_ok=True)
    root = find_dataset_root()
    images = load_cases(root)
    train_patients = {i["patient_id"] for i in images if i["split"] == "Training"}
    test = [i for i in images if i["split"] == "Test" and i["patient_id"] not in train_patients]
    sample = random.Random(7).sample([i for i in images if i["split"] == "Training"], THRESHOLD_SAMPLE)
    print(f"test {len(test)} images ({sum(i['label'] for i in test)} malignant), threshold sample {len(sample)}")

    crop_model = AutoModel.from_pretrained(CROP_REPO, revision=CROP_REV, trust_remote_code=True).eval()
    screen = AutoModel.from_pretrained(SCREEN_REPO, revision=SCREEN_REV, trust_remote_code=True).eval()
    crop_net = CropNet(crop_model).to(device).eval()
    nets = {spec["name"]: CancerNet(getattr(screen, spec["name"])).to(device).eval() for spec in NETS}

    # 1. Our preprocessing must give the same answer as the model's own (albumentations) code.
    check, check_error = [], None
    try:
        for image in test[:8]:
            gray = read_gray(image["path"])
            with torch.inference_mode():
                coords = crop_model(torch.from_numpy(crop_model.preprocess(gray)).float()[None, None].to(device),
                                    torch.tensor([gray.shape[:2]]).to(device))[0].cpu().numpy()
                x, y, w, h = coords
                official = float(screen({"img": gray[y:y + h, x:x + w]}, device=str(device))["cancer"].reshape(-1)[0])
            ours = float(np.mean(list(score(image["path"], crop_net, nets, device).values())))
            check.append(abs(official - ours))
        print(f"preprocessing check: largest difference from the model card's code {max(check):.5f}")
    except Exception as e:  # the card's code depends on albumentations' API; our own pipeline does not
        check_error = f"{type(e).__name__}: {e}"
        print("preprocessing check could not run:", check_error)

    # 2. Score every image with every net.
    started = time.time()
    test_scores = [score(i["path"], crop_net, nets, device) for i in test]
    sample_scores = [score(i["path"], crop_net, nets, device) for i in sample]
    print(f"scored {len(test) + len(sample)} images in {time.time() - started:.0f}s")

    results = {}
    labels = [i["label"] for i in test]
    for combo, names in COMBOS.items():
        threshold = metrics.best_threshold([i["label"] for i in sample], combo_scores(sample_scores, names))
        values = combo_scores(test_scores, names)
        report = metrics.report(labels, values, [i["view"] for i in test], [i["patient_id"] for i in test], threshold)
        b_labels, b_values, b_patients = breast_level(test, values)
        report["per_breast_cc_mlo"] = {
            **metrics.at_threshold(b_labels, b_values, threshold),
            "ci95": metrics.patient_bootstrap(b_labels, b_values, b_patients, threshold),
        }
        results[combo] = {"nets": names, **report}
        o = report["overall"]
        print(f"{combo:10s} auc {o['roc_auc']:.3f} recall {o['recall']:.3f} spec {o['specificity']:.3f} "
              f"acc {o['accuracy']:.3f} | per breast auc {report['per_breast_cc_mlo']['roc_auc']:.3f}")

    # 3. Export to ONNX and check it matches PyTorch.
    parity = {}
    exports = [("crop", crop_net, (1, 1, 256, 256))] + [
        (spec["name"], nets[spec["name"]], (1, 1, spec["height"], spec["width"])) for spec in NETS]
    import onnxruntime as ort
    for name, module, shape in exports:
        module = module.float().cpu().eval()
        example = torch.rand(*shape) * 255
        path = OUT / f"{name}.onnx"
        torch.onnx.export(module, example, path, input_names=["image"], output_names=["output"], dynamo=False,
                          opset_version=17)
        session = ort.InferenceSession(str(path), providers=["CPUExecutionProvider"])
        with torch.inference_mode():
            expected = module(example).numpy()
        parity[name] = float(np.abs(session.run(None, {"image": example.numpy()})[0] - expected).max())
    print("onnx vs torch", parity)
    assert max(parity.values()) < 1e-3, "ONNX export does not match PyTorch"

    # 4. Fixtures for the Java tests: a smaller copy of a real test mammogram, and what each step must produce.
    fixtures = OUT / "fixtures"
    fixtures.mkdir(exist_ok=True)
    picked = next(i for i in test if i["label"] == 1)
    gray = read_gray(picked["path"])
    small = cv2.resize(gray, (gray.shape[1] // 4, gray.shape[0] // 4), interpolation=cv2.INTER_AREA)
    cv2.imwrite(str(fixtures / "mammo.png"), small)
    cv2.imwrite(str(fixtures / "mammo_crop256.png"), cv2.resize(small, (256, 256), interpolation=cv2.INTER_LINEAR))
    sessions = {name: ort.InferenceSession(str(OUT / f"{name}.onnx"), providers=["CPUExecutionProvider"])
                for name, _, _ in exports}
    from mammoscreen_pipeline import crop_input as ci, crop_box as cb, net_input as ni
    coords = sessions["crop"].run(None, {"image": ci(small)})[0][0]
    box = cb(coords, *small.shape[:2])
    cropped = small[box[1]:box[1] + box[3], box[0]:box[0] + box[2]]
    expected = {spec["name"]: float(sessions[spec["name"]].run(None, {"image": ni(cropped, spec)})[0][0, 0]) for spec in NETS}
    cv2.imwrite(str(fixtures / "mammo_net0_input.png"), ni(cropped, NETS[0])[0, 0].astype(np.uint8))
    cv2.imwrite(str(fixtures / "mammo_net2_input.png"), ni(cropped, NETS[2])[0, 0].astype(np.uint8))
    (fixtures / "expected.json").write_text(json.dumps({"crop_coords": coords.tolist(), "crop_box": list(box),
                                                        "probabilities": expected}, indent=2))

    # 5. CPU speed of one image (Kaggle CPU; the laptop is measured separately).
    timing = {}
    for name, _, shape in exports:
        x = (np.random.rand(*shape) * 255).astype(np.float32)
        sessions[name].run(None, {"image": x})
        t = time.time()
        for _ in range(3):
            sessions[name].run(None, {"image": x})
        timing[name] = round((time.time() - t) / 3, 3)
    print("kaggle cpu seconds per image", timing)

    (OUT / "mammoscreen_metrics.json").write_text(json.dumps({
        "model": {"repo": SCREEN_REPO, "revision": SCREEN_REV, "crop_repo": CROP_REPO, "crop_revision": CROP_REV,
                  "licence": "Apache-2.0", "author_rsna_holdout_auc": 0.945},
        "evaluated_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "test_set": {
            "name": "CBIS-DDSM full mammograms, official test split",
            "source": "TCIA CBIS-DDSM (CC BY 3.0) via Kaggle awsaf49/cbis-ddsm-breast-cancer-image-dataset",
            "patient_disjoint": True,
            "note": "Patients also in the training split removed. Every image has a biopsy-proven benign or "
                    "malignant finding. mammoscreen was pretrained on CBIS-DDSM and its card does not say whether "
                    "this test split was held out, so these numbers may be optimistic.",
        },
        "threshold_sample": f"{THRESHOLD_SAMPLE} CBIS-DDSM training images",
        "preprocessing_max_difference": max(check) if check else None,
        "preprocessing_check_error": check_error,
        "onnx_parity": parity,
        "kaggle_cpu_seconds": timing,
        "combinations": results,
    }, indent=2))
    print(f"wrote {OUT}")


if __name__ == "__main__":
    main()
