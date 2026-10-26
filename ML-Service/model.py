"""Model definition and preprocessing shared by train.py and export_onnx.py."""
from pathlib import Path

import torch
from torchvision import models, transforms

CLASSES = ["benign", "malignant"]
IMAGE_SIZE = 512
WEIGHTS_PATH = Path(__file__).parent / "weights" / "model.pt"

# ImageNet normalisation, because the backbone was pretrained on ImageNet.
eval_transform = transforms.Compose([
    transforms.Resize((IMAGE_SIZE, IMAGE_SIZE)),
    transforms.ToTensor(),
    transforms.Normalize([0.485, 0.456, 0.406], [0.229, 0.224, 0.225]),
])


def build_model(pretrained_backbone: bool = True) -> torch.nn.Module:
    """EfficientNet-B0 pretrained on ImageNet with a 2-class head (benign / malignant)."""
    weights = models.EfficientNet_B0_Weights.IMAGENET1K_V1 if pretrained_backbone else None
    model = models.efficientnet_b0(weights=weights)
    in_features = model.classifier[1].in_features
    model.classifier[1] = torch.nn.Linear(in_features, len(CLASSES))
    return model
