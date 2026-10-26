"""Fine-tune the ImageNet-pretrained EfficientNet-B0 on mammograms.

Expected dataset layout (e.g. CBIS-DDSM converted to PNG):
    data/train/benign/*.png     data/train/malignant/*.png
    data/val/benign/*.png       data/val/malignant/*.png

Run: python train.py --data data --epochs 15
Saves the best model (by validation ROC-AUC) to weights/model.pt.
Then run export_onnx.py to produce weights/model.onnx for the backend.
"""
import argparse
from collections import Counter

import torch
from sklearn.metrics import roc_auc_score
from torch.utils.data import DataLoader
from torchvision import datasets, transforms

from model import CLASSES, IMAGE_SIZE, WEIGHTS_PATH, build_model, eval_transform

train_transform = transforms.Compose([
    transforms.Resize((IMAGE_SIZE, IMAGE_SIZE)),
    transforms.RandomHorizontalFlip(),
    transforms.RandomVerticalFlip(),
    transforms.RandomRotation(15),
    transforms.ColorJitter(brightness=0.2, contrast=0.2),
    transforms.ToTensor(),
    transforms.Normalize([0.485, 0.456, 0.406], [0.229, 0.224, 0.225]),
])


def evaluate(model, loader, device):
    model.eval()
    labels, scores = [], []
    with torch.no_grad():
        for x, y in loader:
            probs = torch.softmax(model(x.to(device)), dim=1)[:, 1]
            scores += probs.cpu().tolist()
            labels += y.tolist()
    accuracy = sum((s >= 0.5) == bool(l) for s, l in zip(scores, labels)) / len(labels)
    return roc_auc_score(labels, scores), accuracy


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--data", default="data")
    parser.add_argument("--epochs", type=int, default=15)
    parser.add_argument("--batch-size", type=int, default=16)
    parser.add_argument("--lr", type=float, default=1e-4)
    args = parser.parse_args()

    train_ds = datasets.ImageFolder(f"{args.data}/train", transform=train_transform)
    val_ds = datasets.ImageFolder(f"{args.data}/val", transform=eval_transform)
    assert train_ds.classes == CLASSES, f"Folders must be named {CLASSES}, got {train_ds.classes}"

    train_loader = DataLoader(train_ds, batch_size=args.batch_size, shuffle=True, num_workers=2)
    val_loader = DataLoader(val_ds, batch_size=args.batch_size, num_workers=2)

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    model = build_model(pretrained_backbone=True).to(device)

    # Weight classes inversely to frequency, since malignant cases are usually rarer.
    counts = Counter(train_ds.targets)
    class_weights = torch.tensor([len(train_ds) / (2 * counts[i]) for i in range(len(CLASSES))])
    criterion = torch.nn.CrossEntropyLoss(weight=class_weights.to(device))
    optimizer = torch.optim.AdamW(model.parameters(), lr=args.lr, weight_decay=1e-4)
    scheduler = torch.optim.lr_scheduler.CosineAnnealingLR(optimizer, T_max=args.epochs)

    best_auc = 0.0
    WEIGHTS_PATH.parent.mkdir(exist_ok=True)
    for epoch in range(1, args.epochs + 1):
        model.train()
        total_loss = 0.0
        for x, y in train_loader:
            x, y = x.to(device), y.to(device)
            optimizer.zero_grad()
            loss = criterion(model(x), y)
            loss.backward()
            optimizer.step()
            total_loss += loss.item() * x.size(0)
        scheduler.step()

        auc, acc = evaluate(model, val_loader, device)
        print(f"epoch {epoch}: loss={total_loss / len(train_ds):.4f} val_auc={auc:.4f} val_acc={acc:.4f}")
        if auc > best_auc:
            best_auc = auc
            torch.save({"state_dict": model.state_dict(), "version": f"efficientnet_b0-auc{auc:.3f}"}, WEIGHTS_PATH)
            print(f"  saved {WEIGHTS_PATH}")

    print(f"best val ROC-AUC: {best_auc:.4f}")
    print("next: python export_onnx.py, then restart the Spring Boot backend")


if __name__ == "__main__":
    main()
