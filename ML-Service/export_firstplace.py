"""Export fold 0 of the RSNA 2023 1st-place model (dangnh0611, MIT) to ONNX for the backend. Runs on Kaggle.

Graph: input 1 x 1 x 2048 x 1024 float pixels 0-255 (the cropped, resized and padded breast, see
evaluate_firstplace.firstplace_input), output P(cancer) of shape (1, 1). The grey channel is copied to 3 channels
and ImageNet-normalised inside the graph.

Also writes Java test fixtures (weights/firstplace/fixtures): a real INbreast mammogram at a quarter of its size, the
Otsu crop box, the 2048 x 1024 model input, and the ONNX probability for it.
"""
import json
from pathlib import Path

import cv2
import numpy as np
import onnxruntime as ort
import torch

from evaluate_firstplace import OUT, firstplace_input, inbreast, load_folds, otsu_box


class Fold0(torch.nn.Module):
    def __init__(self, model, mean, std):
        super().__init__()
        self.model = model
        self.register_buffer("mean", mean)
        self.register_buffer("std", std)

    def forward(self, x):
        x = (x.expand(-1, 3, -1, -1) - self.mean) / self.std
        return torch.sigmoid(self.model(x))


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    folds, mean, std = load_folds(torch.device("cpu"), only_first=True)
    module = Fold0(folds[0], mean.cpu(), std.cpu()).eval()
    path = OUT / "firstplace_fold0.onnx"
    example = torch.rand(1, 1, 2048, 1024) * 255
    torch.onnx.export(module, example, path, input_names=["image"], output_names=["output"], dynamo=False,
                      opset_version=17)
    session = ort.InferenceSession(str(path), providers=["CPUExecutionProvider"])
    with torch.inference_mode():
        parity = float(np.abs(session.run(None, {"image": example.numpy()})[0] - module(example).numpy()).max())
    print("onnx vs torch", parity)
    assert parity < 1e-4

    fixtures = OUT / "fixtures"
    fixtures.mkdir(exist_ok=True)
    row = next(r for r in inbreast() if r["birads"] >= 5)
    gray = cv2.imread(row["path"], cv2.IMREAD_GRAYSCALE)
    small = cv2.resize(gray, (gray.shape[1] // 4, gray.shape[0] // 4), interpolation=cv2.INTER_AREA)
    cv2.imwrite(str(fixtures / "mammo.png"), small)
    box = otsu_box(small)
    x = firstplace_input(small, torch.device("cpu"))[:, :1]
    cv2.imwrite(str(fixtures / "mammo_input.png"), x[0, 0].numpy().astype(np.uint8))
    probability = float(session.run(None, {"image": x.numpy().astype(np.float32)})[0][0, 0])
    (fixtures / "expected.json").write_text(json.dumps({"otsu_box": list(map(int, box)), "probability": probability}, indent=2))
    print("fixture", box, probability)


if __name__ == "__main__":
    main()
