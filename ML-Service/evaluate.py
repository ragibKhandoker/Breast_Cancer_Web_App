"""Measure a model on the held-out CBIS-DDSM test set (patients never seen in training or validation).

Run:   python evaluate.py                -> weights/metrics.json for weights/model.pt (from train.py)
       python evaluate.py --hf-baseline  -> how the unmodified Hugging Face model does on the same images

metrics.json is what the website shows users (accuracy, precision, recall, specificity, F1, ROC-AUC, overall and
for CC and MLO separately, with 95% intervals). export_onnx.py embeds it in the model file, so the numbers
always belong to the model that is running.
"""
import argparse
import hashlib
import json
from datetime import datetime, timezone

import numpy as np
import torch
from torch.utils.data import DataLoader

import metrics
from data_loading import MammogramDataset
from model import METRICS_PATH, WEIGHTS_DIR, WEIGHTS_PATH, load_pretrained_checkpoint, load_trained

TEST_SET = {
    "name": "CBIS-DDSM full mammograms, official test split",
    "source": "TCIA CBIS-DDSM (CC BY 3.0) via Hugging Face dbaek111/CBIS-DDSM_1024 + FLARE25-Agent-Xray/CBIS-DDSM labels",
    "patient_disjoint": True,
    "note": "Patients who also appear in the training split were removed. Every image has a biopsy-proven "
            "benign or malignant finding; normal screening images are not part of CBIS-DDSM.",
}


def file_sha256(path):
    digest = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


@torch.no_grad()
def predict(model, dataset, device, batch_size=16, workers=4, amp=False):
    model.to(device).eval()
    loader = DataLoader(dataset, batch_size=batch_size, num_workers=workers)
    scores = []
    for x, _ in loader:
        with torch.autocast(device.type, enabled=amp and device.type == "cuda"):
            scores += model(x.to(device), probabilities=True)[:, 1].float().cpu().tolist()
    scores = np.array(scores)
    if not np.isfinite(scores).all():
        raise SystemExit("The model produced NaN scores; it is broken and must not be used. Retrain it.")
    return scores


def hf_baseline(device):
    """The published 3-class model as it is: 224 px input, P(malignant) as the score."""
    from torchvision import models

    checkpoint = load_pretrained_checkpoint()
    backbone = models.resnet50()
    backbone.fc = torch.nn.Linear(backbone.fc.in_features, checkpoint["num_classes"])
    backbone.load_state_dict(checkpoint["model_state"])
    mean = torch.tensor(checkpoint["normalization_mean"]).view(1, 3, 1, 1)
    std = torch.tensor(checkpoint["normalization_std"]).view(1, 3, 1, 1)
    size = checkpoint["img_size"]
    names = checkpoint["class_names"]
    dataset = MammogramDataset("test")
    backbone.to(device).eval()
    probabilities = []
    with torch.no_grad():
        for x, _ in DataLoader(dataset, batch_size=32, num_workers=4):
            x = torch.nn.functional.interpolate(x, size=(size, size), mode="bilinear", antialias=True)
            probabilities.append(torch.softmax(backbone(((x - mean) / std).to(device)), 1).cpu())
    probabilities = torch.cat(probabilities).numpy()
    top = [names[i] for i in probabilities.argmax(1)]
    result = metrics.report(dataset.labels, probabilities[:, names.index("malignant")], dataset.views,
                            dataset.patients, 0.5)
    result["predicted_class_share"] = {n: top.count(n) / len(top) for n in names}
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--hf-baseline", action="store_true")
    args = parser.parse_args()
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")

    if args.hf_baseline:
        result = hf_baseline(device)
        out = WEIGHTS_DIR / "hf_baseline_metrics.json"
        out.write_text(json.dumps({"test_set": TEST_SET, **result}, indent=2))
        print(json.dumps(result, indent=2))
        print(f"wrote {out}")
        return

    model, checkpoint = load_trained()
    dataset = MammogramDataset("test")
    scores = predict(model, dataset, device)
    result = metrics.report(dataset.labels, scores, dataset.views, dataset.patients, checkpoint["threshold"])
    payload = {
        "model_version": checkpoint["version"],
        "model_sha256": file_sha256(WEIGHTS_PATH),
        "evaluated_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "test_set": TEST_SET,
        "validation": checkpoint.get("validation"),
        **result,
    }
    METRICS_PATH.write_text(json.dumps(payload, indent=2))
    overall = result["overall"]
    print(json.dumps({k: overall[k] for k in ("accuracy", "precision", "recall", "specificity", "f1", "roc_auc")}, indent=2))
    for view, m in result["by_view"].items():
        print(f"{view}: acc={m['accuracy']:.3f} recall={m['recall']:.3f} spec={m['specificity']:.3f} auc={m['roc_auc']:.3f}")
    print(f"wrote {METRICS_PATH}")


if __name__ == "__main__":
    main()
