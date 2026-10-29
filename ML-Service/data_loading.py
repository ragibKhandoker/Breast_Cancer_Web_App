"""CBIS-DDSM manifest (from prepare_cbis.py) as PyTorch datasets."""
import csv
from pathlib import Path

import torch
from torch.utils.data import Dataset
from torchvision import transforms

from model import IMAGE_HEIGHT, IMAGE_WIDTH, load_grayscale, to_input

DATA_DIR = Path(__file__).parent / "data" / "cbis"


def read_manifest(split, data_dir=DATA_DIR):
    manifest = data_dir / "manifest.csv"
    if not manifest.exists():
        raise SystemExit(f"{manifest} not found. Run: python prepare_cbis.py")
    with open(manifest, newline="", encoding="utf-8") as f:
        return [r for r in csv.DictReader(f) if r["split"] == split]


# Mild changes a real mammogram can show: either breast side, small positioning and exposure differences.
_augment = transforms.Compose([
    transforms.RandomHorizontalFlip(),
    transforms.RandomAffine(degrees=10, translate=(0.04, 0.04), scale=(0.92, 1.08)),
    transforms.ColorJitter(brightness=0.15, contrast=0.15),
])


class MammogramDataset(Dataset):
    def __init__(self, split, train=False, data_dir=DATA_DIR):
        self.rows = read_manifest(split, data_dir)
        self.data_dir = data_dir
        self.train = train

    def __len__(self):
        return len(self.rows)

    def __getitem__(self, i):
        row = self.rows[i]
        image = load_grayscale(self.data_dir / row["file"])
        if self.train:
            image = _augment(image.resize((IMAGE_WIDTH, IMAGE_HEIGHT)))
        return to_input(image), torch.tensor(int(row["label"]))

    @property
    def labels(self):
        return [int(r["label"]) for r in self.rows]

    @property
    def views(self):
        return [r["view"] for r in self.rows]

    @property
    def patients(self):
        return [r["patient_id"] for r in self.rows]
