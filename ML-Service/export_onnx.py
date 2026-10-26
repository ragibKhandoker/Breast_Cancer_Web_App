"""Export the model to ONNX so Spring Boot can run it with ONNX Runtime.

Run: python export_onnx.py
Reads weights/model.pt if it exists (from train.py); otherwise exports an untrained demo model.
Writes weights/model.onnx.

The exported graph takes RGB pixels in [0, 1], shape [1, 3, 512, 512], and does the ImageNet
normalisation and softmax itself, so the Java side only has to resize and scale the image.
"""
import onnx
import torch

from model import CLASSES, IMAGE_SIZE, WEIGHTS_PATH, build_model

ONNX_PATH = WEIGHTS_PATH.with_suffix(".onnx")


class ExportWrapper(torch.nn.Module):
    def __init__(self, model):
        super().__init__()
        self.model = model
        self.register_buffer("mean", torch.tensor([0.485, 0.456, 0.406]).view(1, 3, 1, 1))
        self.register_buffer("std", torch.tensor([0.229, 0.224, 0.225]).view(1, 3, 1, 1))

    def forward(self, image):
        return torch.softmax(self.model((image - self.mean) / self.std), dim=1)


def main():
    trained = WEIGHTS_PATH.exists()
    model = build_model(pretrained_backbone=not trained)
    version = "untrained-demo"
    if trained:
        checkpoint = torch.load(WEIGHTS_PATH, map_location="cpu")
        model.load_state_dict(checkpoint["state_dict"])
        version = checkpoint.get("version", "efficientnet_b0")

    wrapper = ExportWrapper(model).eval()
    ONNX_PATH.parent.mkdir(exist_ok=True)
    torch.onnx.export(
        wrapper,
        torch.rand(1, 3, IMAGE_SIZE, IMAGE_SIZE),
        ONNX_PATH,
        input_names=["image"],
        output_names=["probabilities"],
        external_data=False,
    )

    # Metadata the backend reads to label results and to warn when the model is untrained.
    onnx_model = onnx.load(ONNX_PATH)
    for key, value in {"version": version, "trained": str(trained).lower(), "classes": ",".join(CLASSES)}.items():
        onnx_model.metadata_props.add(key=key, value=value)
    onnx.save(onnx_model, ONNX_PATH)
    print(f"exported {ONNX_PATH} (version={version}, trained={trained})")


if __name__ == "__main__":
    main()
