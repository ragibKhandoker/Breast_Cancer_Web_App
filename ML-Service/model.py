"""Model definition and preprocessing shared by train.py, evaluate.py and export_onnx.py.

The network is ResNet50. Training starts from the mammography classifier published at
https://huggingface.co/umairinayat/medical-models (disease_models/mammography/classification, MIT licence),
whose backbone has already seen mammograms, and is fine-tuned on full CBIS-DDSM mammograms (CC and MLO views).

Preprocessing must match the Spring Boot backend (OnnxPredictor.java) exactly, so both sides do the same thing:
grayscale -> bilinear resize to IMAGE_HEIGHT x IMAGE_WIDTH -> copy to 3 channels -> pixels in [0, 1].
The training images (CBIS-DDSM_1024) were squashed to a square from their portrait originals, so uploads are
squashed to a square the same way rather than padded. ImageNet normalisation and softmax live inside the
network (NormalizedClassifier), so the exported ONNX graph is the very module that was trained and evaluated.
"""
from pathlib import Path

import torch
from PIL import Image
from torchvision import models
from torchvision.transforms import functional as F

CLASSES = ["benign", "malignant"]
IMAGE_HEIGHT = 512
IMAGE_WIDTH = 512
ROOT = Path(__file__).parent
WEIGHTS_DIR = ROOT / "weights"
WEIGHTS_PATH = WEIGHTS_DIR / "model.pt"
METRICS_PATH = WEIGHTS_DIR / "metrics.json"
PRETRAINED_PATH = WEIGHTS_DIR / "pretrained" / "best_classifier.pt"

IMAGENET_MEAN = [0.485, 0.456, 0.406]
IMAGENET_STD = [0.229, 0.224, 0.225]


class NormalizedClassifier(torch.nn.Module):
    """Takes RGB pixels in [0, 1]; returns logits (training) or probabilities (probabilities=True)."""

    def __init__(self, backbone: torch.nn.Module):
        super().__init__()
        self.backbone = backbone
        self.register_buffer("mean", torch.tensor(IMAGENET_MEAN).view(1, 3, 1, 1))
        self.register_buffer("std", torch.tensor(IMAGENET_STD).view(1, 3, 1, 1))

    def forward(self, image: torch.Tensor, probabilities: bool = False) -> torch.Tensor:
        logits = self.backbone((image - self.mean) / self.std)
        return torch.softmax(logits, dim=1) if probabilities else logits


def build_model(init: str = "huggingface") -> NormalizedClassifier:
    """ResNet50 with a benign/malignant head.

    init="huggingface": backbone from the published mammography checkpoint; the new 2-class head starts from
    that model's benign and malignant rows. init="imagenet": plain ImageNet weights. init="none": random
    (only used to rebuild a model before loading a saved state dict).
    """
    backbone = models.resnet50(weights=models.ResNet50_Weights.IMAGENET1K_V2 if init == "imagenet" else None)
    backbone.fc = torch.nn.Linear(backbone.fc.in_features, len(CLASSES))
    if init == "huggingface":
        checkpoint = load_pretrained_checkpoint()
        state = checkpoint["model_state"]
        names = checkpoint["class_names"]
        rows = [names.index(name) for name in CLASSES]
        state = {**state, "fc.weight": state["fc.weight"][rows], "fc.bias": state["fc.bias"][rows]}
        backbone.load_state_dict(state)
    return NormalizedClassifier(backbone)


def load_pretrained_checkpoint() -> dict:
    """The Hugging Face checkpoint, loaded as plain tensors (weights_only: no pickled code is run)."""
    if not PRETRAINED_PATH.exists():
        raise SystemExit(f"{PRETRAINED_PATH} not found. Run: python download_pretrained.py")
    return torch.load(PRETRAINED_PATH, map_location="cpu", weights_only=True)


def load_trained(path: Path = WEIGHTS_PATH) -> tuple[NormalizedClassifier, dict]:
    """A model saved by train.py and its checkpoint (version, threshold, ...)."""
    checkpoint = torch.load(path, map_location="cpu", weights_only=True)
    model = build_model(init="none")
    model.load_state_dict(checkpoint["state_dict"])
    return model.eval(), checkpoint


def load_grayscale(path) -> Image.Image:
    return Image.open(path).convert("L")


def to_input(image: Image.Image) -> torch.Tensor:
    """The exact inference preprocessing: grayscale PIL image -> [3, H, W] tensor in [0, 1]."""
    resized = image.resize((IMAGE_WIDTH, IMAGE_HEIGHT), Image.BILINEAR)
    return F.to_tensor(resized).expand(3, -1, -1).contiguous()
