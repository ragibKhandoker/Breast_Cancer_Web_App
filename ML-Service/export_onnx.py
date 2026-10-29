"""Export the trained model to ONNX so Spring Boot can run it with ONNX Runtime on the server's CPU.

Run: python export_onnx.py           (after train.py and evaluate.py)  -> weights/model.onnx
     python export_onnx.py --demo    untrained placeholder, so the site can start before a model exists

The graph takes RGB pixels in [0, 1], shape [1, 3, IMAGE_HEIGHT, IMAGE_WIDTH], and returns
[benign, malignant] probabilities; normalisation and softmax are inside it, so Java only resizes and scales.

Metadata the backend reads: version, trained, classes, threshold, image_height, image_width and metrics (the
JSON from evaluate.py). Export stops if metrics.json was not measured on this exact model.pt, so the website can
never show numbers that belong to a different model.
"""
import argparse
import inspect
import json
import sys

import onnx
import torch

from evaluate import file_sha256
from model import CLASSES, IMAGE_HEIGHT, IMAGE_WIDTH, METRICS_PATH, WEIGHTS_PATH, build_model, load_trained

ONNX_PATH = WEIGHTS_PATH.with_suffix(".onnx")


class ProbabilityOutput(torch.nn.Module):
    def __init__(self, model):
        super().__init__()
        self.model = model

    def forward(self, image):
        return self.model(image, probabilities=True)


def main():
    # The PyTorch exporter prints emoji, which a Windows console cannot encode.
    for stream in (sys.stdout, sys.stderr):
        stream.reconfigure(errors="replace")
    parser = argparse.ArgumentParser()
    parser.add_argument("--demo", action="store_true", help="export an untrained placeholder model")
    args = parser.parse_args()

    if args.demo:
        model, version, threshold, metrics = build_model(init="imagenet").eval(), "untrained-demo", 0.5, None
    else:
        if not WEIGHTS_PATH.exists():
            raise SystemExit(f"{WEIGHTS_PATH} not found. Run train.py first (or --demo for a placeholder).")
        if not METRICS_PATH.exists():
            raise SystemExit(f"{METRICS_PATH} not found. Run evaluate.py first.")
        metrics = json.loads(METRICS_PATH.read_text())
        if metrics.get("model_sha256") != file_sha256(WEIGHTS_PATH):
            raise SystemExit("metrics.json was measured on a different model.pt. Run evaluate.py again.")
        model, checkpoint = load_trained()
        version, threshold = checkpoint["version"], checkpoint["threshold"]

    ONNX_PATH.parent.mkdir(exist_ok=True)
    # Keep the weights inside the one .onnx file; older PyTorch versions (as on Kaggle) always do and lack the flag.
    options = {"external_data": False} if "external_data" in inspect.signature(torch.onnx.export).parameters else {}
    torch.onnx.export(
        ProbabilityOutput(model).eval(),
        torch.rand(1, 3, IMAGE_HEIGHT, IMAGE_WIDTH),
        ONNX_PATH,
        input_names=["image"],
        output_names=["probabilities"],
        **options,
    )

    onnx_model = onnx.load(ONNX_PATH)
    props = {
        "version": version,
        "trained": str(not args.demo).lower(),
        "classes": ",".join(CLASSES),
        "threshold": f"{threshold:.6f}",
        "image_height": str(IMAGE_HEIGHT),
        "image_width": str(IMAGE_WIDTH),
    }
    if metrics:
        props["metrics"] = json.dumps(metrics, separators=(",", ":"))
    for key, value in props.items():
        onnx_model.metadata_props.add(key=key, value=value)
    onnx.save(onnx_model, ONNX_PATH)
    print(f"exported {ONNX_PATH} (version={version}, trained={not args.demo}, threshold={threshold:.3f})")


if __name__ == "__main__":
    main()
