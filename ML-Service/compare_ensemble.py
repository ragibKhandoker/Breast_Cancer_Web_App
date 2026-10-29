"""Test a combination of the two cancer models on both test sets. Runs on Kaggle (GPU).

Models: the fine-tuned ResNet50 (ragibahsanrafi/breastcare-resnet-cbis) and mammoscreen net0 (ianpan/mammoscreen).
Combination: logistic regression on the two models' logits, fitted ONLY on training images (CBIS-DDSM training
split + VinDr-Mammo training split); its threshold is chosen on those same training images.

Test sets (never used for fitting):
  CBIS-DDSM official test split, patients also in training removed   biopsy-proven benign vs malignant
  VinDr-Mammo official test split                                    BI-RADS 4-5 vs BI-RADS 1-3 (and vs 1 only)

Output: weights/ensemble/ensemble_metrics.json (coefficients, threshold, metrics) and per-image scores.
"""
import json
import random
import time
from pathlib import Path

import numpy as np
import onnxruntime as ort
import torch
from PIL import Image
from sklearn.linear_model import LogisticRegression

import metrics
from compare_screening import find, pick, vindr
from evaluate_mammoscreen import CROP_REPO, CROP_REV, SCREEN_REPO, SCREEN_REV, CancerNet, CropNet, load_cases, read_gray
from mammoscreen_pipeline import NETS, crop_box, crop_input, net_input

OUT = Path("weights/ensemble")
EPS = 1e-6


def logit(p):
    p = np.clip(np.asarray(p, dtype=np.float64), EPS, 1 - EPS)
    return np.log(p / (1 - p))


def cbis():
    import glob
    root = Path(glob.glob("/kaggle/input/**/csv/dicom_info.csv", recursive=True)[0]).parent.parent
    images = load_cases(root)
    train_patients = {i["patient_id"] for i in images if i["split"] == "Training"}
    test = [i for i in images if i["split"] == "Test" and i["patient_id"] not in train_patients]
    train = random.Random(7).sample([i for i in images if i["split"] == "Training"], 500)
    as_rows = lambda items: [{"path": i["path"], "study": i["patient_id"], "side": i["side"], "view": i["view"],
                              "label": i["label"]} for i in items]
    return as_rows(train), as_rows(test)


class Scorer:
    def __init__(self, device):
        from transformers import AutoModel
        self.device = device
        crop_model = AutoModel.from_pretrained(CROP_REPO, revision=CROP_REV, trust_remote_code=True).eval()
        screen = AutoModel.from_pretrained(SCREEN_REPO, revision=SCREEN_REV, trust_remote_code=True).eval()
        self.crop = CropNet(crop_model).to(device).eval()
        self.net0 = CancerNet(screen.net0).to(device).eval()
        options = ort.SessionOptions()
        options.intra_op_num_threads = 4
        self.resnet = ort.InferenceSession(str(find("resnet_cbis.onnx")), options, providers=["CPUExecutionProvider"])

    @torch.inference_mode()
    def __call__(self, path):
        gray = read_gray(path)
        coords = self.crop(torch.from_numpy(crop_input(gray)).to(self.device))[0].float().cpu().numpy()
        x, y, w, h = crop_box(coords, *gray.shape[:2])
        mammo = float(self.net0(torch.from_numpy(net_input(gray[y:y + h, x:x + w], NETS[0])).to(self.device))[0, 0])
        small = np.asarray(Image.fromarray(gray).resize((512, 512), Image.BILINEAR), dtype=np.float32) / 255.0
        resnet = float(self.resnet.run(None, {"image": np.repeat(small[None, None], 3, axis=1)})[0][0, 1])
        return resnet, mammo


def main():
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    rng = random.Random(3)
    OUT.mkdir(parents=True, exist_ok=True)
    cbis_train, cbis_test = cbis()
    rows = vindr()
    for r in rows:
        r["label"] = int(r["birads"] >= 4)
    vindr_test = pick(rows, "test", rng, 900)
    tune_all = pick(rows, "training", rng, 600)
    vindr_train = [r for r in tune_all if r["birads"] >= 4][:250] + [r for r in tune_all if r["birads"] < 4][:600]
    sets = {"cbis_train": cbis_train, "vindr_train": vindr_train, "cbis_test": cbis_test, "vindr_test": vindr_test}
    print({k: (len(v), sum(r["label"] for r in v)) for k, v in sets.items()})

    scorer = Scorer(device)
    scores = {}
    for name, part in sets.items():
        started = time.time()
        scores[name] = np.array([scorer(r["path"]) for r in part])
        print(f"scored {name} in {time.time() - started:.0f}s", flush=True)

    train_x = np.vstack([logit(scores["cbis_train"]), logit(scores["vindr_train"])])
    train_y = np.array([r["label"] for r in cbis_train + vindr_train])
    # Each training set counts equally, whatever its size.
    weight = np.concatenate([np.full(len(cbis_train), 0.5 / len(cbis_train)), np.full(len(vindr_train), 0.5 / len(vindr_train))])
    lr = LogisticRegression(C=1.0).fit(train_x, train_y, sample_weight=weight * len(train_y))
    combined = {k: lr.predict_proba(logit(v))[:, 1] for k, v in scores.items()}
    candidates = {"resnet": {k: v[:, 0] for k, v in scores.items()},
                  "mammoscreen_net0": {k: v[:, 1] for k, v in scores.items()},
                  "combined": combined}

    def train_threshold(values):
        # Balanced across the two training sets: pick on their union, weighted equally by repeating the smaller.
        reps = max(1, round(len(vindr_train) / len(cbis_train)))
        y = [r["label"] for r in cbis_train] * reps + [r["label"] for r in vindr_train]
        v = np.concatenate([np.tile(values["cbis_train"], reps), values["vindr_train"]])
        return metrics.best_threshold(y, v)

    results = {}
    for name, values in candidates.items():
        threshold = train_threshold(values)
        results[name] = {"threshold": threshold}
        for test in ("cbis_test", "vindr_test"):
            part = sets[test]
            keep = list(range(len(part)))
            results[name][test] = metrics.report([part[i]["label"] for i in keep], values[test][keep],
                                                 [part[i]["view"] for i in keep], [part[i]["study"] for i in keep], threshold)
            if test == "vindr_test":
                normal = [i for i, r in enumerate(part) if r["birads"] in (1, 4, 5)]
                results[name]["vindr_test_vs_normal_only"] = metrics.report(
                    [part[i]["label"] for i in normal], values[test][normal], [part[i]["view"] for i in normal],
                    [part[i]["study"] for i in normal], threshold)
        for test in ("cbis_test", "vindr_test", "vindr_test_vs_normal_only"):
            o = results[name][test]["overall"]
            ci = o["ci95"]["roc_auc"]
            print(f"{name:17s} {test:26s} auc {o['roc_auc']:.3f} [{ci[0]:.3f}-{ci[1]:.3f}] sens {o['recall']:.3f} "
                  f"spec {o['specificity']:.3f} acc {o['accuracy']:.3f} (thr {threshold:.4f})")

    (OUT / "ensemble_metrics.json").write_text(json.dumps({
        "combination": {"features": ["logit(resnet P(malignant))", "logit(mammoscreen net0 P(cancer))"],
                        "coefficients": lr.coef_[0].tolist(), "intercept": float(lr.intercept_[0]),
                        "fitted_on": "CBIS-DDSM training sample (500) + VinDr-Mammo training sample, equal weight"},
        "results": results}, indent=2))
    (OUT / "scores.json").write_text(json.dumps({k: {"scores": scores[k].tolist(),
                                                     "labels": [r["label"] for r in sets[k]],
                                                     "views": [r["view"] for r in sets[k]],
                                                     "studies": [r["study"] for r in sets[k]]} for k in sets}))
    print("wrote", OUT, "coefficients", lr.coef_[0].tolist(), "intercept", float(lr.intercept_[0]))


if __name__ == "__main__":
    main()
