"""Download the published mammography classifier used as the starting point for training.

Source: https://huggingface.co/umairinayat/medical-models (MIT licence),
        disease_models/mammography/classification/best_classifier.pt (ResNet50, benign/malignant/normal).
Run:    python download_pretrained.py      -> weights/pretrained/best_classifier.pt (+ the author's metrics.json)

The file is checked against the SHA-256 Hugging Face publishes, and is later loaded with
torch.load(weights_only=True), so it can only contain tensors, never code.

Note: the author's reported 99.6% accuracy comes from a random split of heavily augmented lesion crops and is not
used anywhere in this project. Only evaluate.py's results on held-out full mammograms are shown to users.
"""
import hashlib
import urllib.request

from model import PRETRAINED_PATH

BASE = "https://huggingface.co/umairinayat/medical-models/resolve/main/disease_models/mammography/classification"
FILES = {
    "best_classifier.pt": "9e5d4f6be6d9ccc9f7eefa5ea0c05959b8de28423fd3c98fdd64750b0be3db2b",
    "metrics.json": None,  # the author's own numbers, kept for reference only
}


def sha256(path):
    digest = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main():
    PRETRAINED_PATH.parent.mkdir(parents=True, exist_ok=True)
    for name, expected in FILES.items():
        target = PRETRAINED_PATH.parent / name
        if not target.exists():
            print(f"downloading {name} ...")
            urllib.request.urlretrieve(f"{BASE}/{name}", target)
        if expected and sha256(target) != expected:
            target.unlink()
            raise SystemExit(f"{name}: checksum mismatch, file deleted. Try again.")
        print(f"ok {target}")


if __name__ == "__main__":
    main()
