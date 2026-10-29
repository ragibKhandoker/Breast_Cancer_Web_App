"""Compare the cancer models on a screening-style test: VinDr-Mammo, official test split. Runs on Kaggle (GPU).

Why: CBIS-DDSM only has biopsied lesions (benign vs malignant), but website users upload routine mammograms,
most of them normal. VinDr-Mammo (Vietnam, digital, 20,000 images) has normal and suspicious breasts and was not
used to train either model. Its labels are radiologist BI-RADS, not biopsy: BI-RADS 4-5 (suspicious for cancer)
is the positive class.
  task A: BI-RADS 4-5 vs BI-RADS 1 (normal)
  task B: BI-RADS 4-5 vs BI-RADS 1-3 (everything not suspicious)

Models: the fine-tuned ResNet50 (Kaggle dataset ragibahsanrafi/breastcare-resnet-cbis) and ianpan/mammoscreen
(3 nets, and net0 alone). Thresholds: each model's own, and one chosen on a sample of VinDr *training* images.
Output: weights/compare/screening_comparison.json
"""
import csv
import glob
import json
import random
import time
from collections import defaultdict
from pathlib import Path

import numpy as np
import onnxruntime as ort
import torch
from PIL import Image

import metrics
from evaluate_mammoscreen import CROP_REPO, CROP_REV, SCREEN_REPO, SCREEN_REV, CancerNet, CropNet, read_gray, score
from mammoscreen_pipeline import NETS

OUT = Path("weights/compare")
NORMAL_SAMPLE = 900
RESNET_THRESHOLD = 0.2108  # from its validation set (metadata of resnet_cbis.onnx)
MAMMOSCREEN_THRESHOLD = 0.0127  # model card: 94.3% sensitivity on RSNA


def find(pattern):
    hits = glob.glob(f"/kaggle/input/**/{pattern}", recursive=True)
    if not hits:
        raise SystemExit(f"missing {pattern}: attach the VinDr datasets and breastcare-resnet-cbis")
    return Path(hits[0])


def vindr():
    images = find("images_png")
    rows = []
    with open(find("breast-level_annotations.csv"), newline="", encoding="utf-8") as f:
        for r in csv.DictReader(f):
            path = images / r["study_id"] / (r["image_id"] + ".png")
            if path.exists():
                rows.append({"path": str(path), "study": r["study_id"], "side": r["laterality"],
                             "view": r["view_position"], "birads": int(r["breast_birads"].split()[-1]),
                             "split": r["split"]})
    return rows


def pick(rows, split, rng, normals):
    part = [r for r in rows if r["split"] == split]
    chosen = [r for r in part if r["birads"] >= 2]
    ones = [r for r in part if r["birads"] == 1]
    rng.shuffle(ones)
    return chosen + ones[:normals]


def resnet_scores(rows):
    """The ResNet's exact preprocessing (ML-Service/model.py): grayscale, Pillow BILINEAR 512 x 512, [0, 1]."""
    session = ort.InferenceSession(str(find("resnet_cbis.onnx")), providers=["CPUExecutionProvider"])
    out = []
    for r in rows:
        img = Image.open(r["path"]).convert("L").resize((512, 512), Image.BILINEAR)
        x = np.repeat((np.asarray(img, dtype=np.float32) / 255.0)[None, None], 3, axis=1)
        out.append(float(session.run(None, {"image": x})[0][0, 1]))
    return np.array(out)


def evaluate(rows, values, threshold, task):
    keep = [i for i, r in enumerate(rows) if r["birads"] >= 4 or (r["birads"] == 1 if task == "A" else True)]
    sub = [rows[i] for i in keep]
    labels = [int(r["birads"] >= 4) for r in sub]
    vals = values[keep]
    result = metrics.report(labels, vals, [r["view"] for r in sub], [r["study"] for r in sub], threshold)
    groups = defaultdict(list)
    for r, v in zip(sub, vals):
        groups[(r["study"], r["side"])].append((r, v))
    b_labels, b_values, b_studies = [], [], []
    for (study, _), members in groups.items():
        b_labels.append(int(max(m[0]["birads"] for m in members) >= 4))
        b_values.append(float(np.mean([m[1] for m in members])))
        b_studies.append(study)
    result["per_breast"] = {**metrics.at_threshold(b_labels, b_values, threshold),
                            "ci95": metrics.patient_bootstrap(b_labels, b_values, b_studies, threshold)}
    return result


def main():
    from transformers import AutoModel

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    rng = random.Random(3)
    OUT.mkdir(parents=True, exist_ok=True)
    rows = vindr()
    test = pick(rows, "test", rng, NORMAL_SAMPLE)
    tune_all = pick(rows, "training", rng, 600)
    tune = [r for r in tune_all if r["birads"] >= 4][:200] + [r for r in tune_all if r["birads"] < 4][:600]
    print(f"test {len(test)} images, {sum(r['birads'] >= 4 for r in test)} BI-RADS 4-5; threshold set {len(tune)}")

    crop_model = AutoModel.from_pretrained(CROP_REPO, revision=CROP_REV, trust_remote_code=True).eval()
    screen = AutoModel.from_pretrained(SCREEN_REPO, revision=SCREEN_REV, trust_remote_code=True).eval()
    crop_net = CropNet(crop_model).to(device).eval()
    nets = {s["name"]: CancerNet(getattr(screen, s["name"])).to(device).eval() for s in NETS}

    check = []
    for r in test[:8]:
        gray = read_gray(r["path"])
        with torch.inference_mode():
            x, y, w, h = crop_model(torch.from_numpy(crop_model.preprocess(gray)).float()[None, None].to(device),
                                    torch.tensor([gray.shape[:2]]).to(device))[0].cpu().numpy()
            official = float(screen({"img": gray[y:y + h, x:x + w]}, device=str(device))["cancer"].reshape(-1)[0])
        ours = float(np.mean(list(score(r["path"], crop_net, nets, device).values())))
        check.append(abs(official - ours))
    print(f"mammoscreen preprocessing vs model card code: largest difference {max(check):.5f}")

    started = time.time()
    per_net = {"test": [score(r["path"], crop_net, nets, device) for r in test],
               "tune": [score(r["path"], crop_net, nets, device) for r in tune]}
    print(f"mammoscreen scored in {time.time() - started:.0f}s")
    started = time.time()
    resnet = {"test": resnet_scores(test), "tune": resnet_scores(tune)}
    print(f"resnet scored in {time.time() - started:.0f}s")

    models = {
        "resnet_cbis": (resnet, RESNET_THRESHOLD),
        "mammoscreen_ensemble3": ({k: np.array([np.mean(list(s.values())) for s in v]) for k, v in per_net.items()},
                                  MAMMOSCREEN_THRESHOLD),
        "mammoscreen_net0": ({k: np.array([s["net0"] for s in v]) for k, v in per_net.items()}, MAMMOSCREEN_THRESHOLD),
    }
    results = {}
    tune_labels = [int(r["birads"] >= 4) for r in tune]
    for name, (values, own_threshold) in models.items():
        tuned = metrics.best_threshold(tune_labels, values["tune"])
        results[name] = {"own_threshold": own_threshold, "vindr_tuned_threshold": tuned}
        for label, threshold in (("at_own_threshold", own_threshold), ("at_vindr_tuned_threshold", tuned)):
            results[name][label] = {task: evaluate(test, values["test"], threshold, task) for task in ("A", "B")}
        for label in ("at_own_threshold", "at_vindr_tuned_threshold"):
            for task in ("A", "B"):
                o = results[name][label][task]["overall"]
                p = results[name][label][task]["per_breast"]
                ci = o["ci95"]["roc_auc"]
                print(f"{name:22s} {label:25s} task {task}: auc {o['roc_auc']:.3f} [{ci[0]:.3f}-{ci[1]:.3f}] "
                      f"sens {o['recall']:.3f} spec {o['specificity']:.3f} | per breast auc {p['roc_auc']:.3f} "
                      f"sens {p['recall']:.3f} spec {p['specificity']:.3f}")
    (OUT / "screening_comparison.json").write_text(json.dumps({
        "test_set": {"name": "VinDr-Mammo official test split", "images": len(test),
                     "positives": "BI-RADS 4-5 (radiologist suspicion, not biopsy)",
                     "normals_sampled": NORMAL_SAMPLE, "dataset": "shantanughosh/vindr-mammogram-dataset-dicom-to-png"},
        "mammoscreen_preprocessing_check": max(check),
        "results": results}, indent=2))
    print("wrote", OUT / "screening_comparison.json")


if __name__ == "__main__":
    main()
