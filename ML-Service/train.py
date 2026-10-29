"""Fine-tune the mammography ResNet50 on full CBIS-DDSM mammograms (CC and MLO views).

Run once on a GPU - the easy way is the Kaggle notebook (kaggle_train.ipynb, see README), which runs all of this:
    python download_pretrained.py     # starting weights from Hugging Face
    python prepare_cbis.py            # data/cbis: images + patient-disjoint train/val/test manifest
    python train.py                   # -> weights/model.pt (best epoch by validation ROC-AUC)
    python evaluate.py                # -> weights/metrics.json, measured on the untouched test patients
    python export_onnx.py             # -> weights/model.onnx with the metrics inside, for the backend

Training is a one-time developer step. The website only runs the exported model.onnx on the server's CPU, so
visitors need no GPU.

The test split is never read here. The epoch is chosen by validation ROC-AUC, and the decision threshold by
validation balanced accuracy, so the test numbers in metrics.json are a fair estimate.
"""
import argparse
import json
import random
import time

import numpy as np
import torch
from torch.utils.data import DataLoader

import metrics
from data_loading import MammogramDataset
from evaluate import predict
from model import IMAGE_HEIGHT, IMAGE_WIDTH, WEIGHTS_DIR, WEIGHTS_PATH, build_model


def seed_everything(seed):
    random.seed(seed)
    np.random.seed(seed)
    torch.manual_seed(seed)
    torch.cuda.manual_seed_all(seed)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--epochs", type=int, default=20)
    parser.add_argument("--batch-size", type=int, default=16)
    parser.add_argument("--accumulate", type=int, default=1, help="gradient steps per update (effective batch)")
    parser.add_argument("--no-amp", action="store_true",
                        help="train in fp32; needed on GTX 16xx cards, whose fp16 produces NaN")
    parser.add_argument("--lr", type=float, default=1e-4)
    parser.add_argument("--weight-decay", type=float, default=1e-4)
    parser.add_argument("--patience", type=int, default=6, help="stop after this many epochs without a better val AUC")
    parser.add_argument("--init", choices=["huggingface", "imagenet"], default="huggingface")
    parser.add_argument("--workers", type=int, default=4)
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()
    seed_everything(args.seed)

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    if device.type == "cpu":
        raise SystemExit("No CUDA GPU found. Train on Kaggle (kaggle_train.ipynb) instead of this computer.")
    torch.backends.cudnn.benchmark = True
    amp = not args.no_amp
    train_ds = MammogramDataset("train", train=True)
    val_ds = MammogramDataset("val")
    print(f"train {len(train_ds)} images, val {len(val_ds)} images, device {device}")

    loader = DataLoader(train_ds, batch_size=args.batch_size, shuffle=True, num_workers=args.workers,
                        persistent_workers=args.workers > 0, drop_last=True)
    model = build_model(init=args.init).to(device)

    labels = np.array(train_ds.labels)
    # Weight the classes so the smaller one counts as much as the larger one.
    weights = torch.tensor([len(labels) / (2 * (labels == c).sum()) for c in (0, 1)], dtype=torch.float32)
    criterion = torch.nn.CrossEntropyLoss(weight=weights.to(device), label_smoothing=0.05)
    optimizer = torch.optim.AdamW(model.parameters(), lr=args.lr, weight_decay=args.weight_decay)
    updates_per_epoch = len(loader) // args.accumulate
    scheduler = torch.optim.lr_scheduler.OneCycleLR(optimizer, max_lr=args.lr, epochs=args.epochs,
                                                    steps_per_epoch=updates_per_epoch, pct_start=0.1)
    scaler = torch.amp.GradScaler(enabled=amp)

    WEIGHTS_DIR.mkdir(exist_ok=True)
    history, best_auc, stale = [], -1.0, 0
    for epoch in range(1, args.epochs + 1):
        started = time.time()
        model.train()
        total, seen = 0.0, 0
        optimizer.zero_grad(set_to_none=True)
        for step, (x, y) in enumerate(loader, 1):
            x = x.to(device, non_blocking=True)
            y = y.to(device, non_blocking=True)
            with torch.autocast(device.type, enabled=amp):
                loss = criterion(model(x), y)
            if not torch.isfinite(loss):
                raise SystemExit("Loss became NaN/inf. On a GTX 16xx card use --no-amp; otherwise lower --lr.")
            scaler.scale(loss / args.accumulate).backward()
            if step % args.accumulate == 0 and step // args.accumulate <= updates_per_epoch:
                scaler.step(optimizer)
                scaler.update()
                optimizer.zero_grad(set_to_none=True)
                scheduler.step()
            total += loss.item() * len(y)
            seen += len(y)

        scores = predict(model, val_ds, device, workers=args.workers, amp=amp)
        threshold = metrics.best_threshold(val_ds.labels, scores)
        val = metrics.at_threshold(val_ds.labels, scores, threshold)
        history.append({"epoch": epoch, "train_loss": total / seen, "val": val, "threshold": threshold})
        print(f"epoch {epoch:2d}  loss {total / seen:.4f}  val auc {val['roc_auc']:.4f}  acc {val['accuracy']:.3f}  "
              f"recall {val['recall']:.3f}  spec {val['specificity']:.3f}  thr {threshold:.3f}  "
              f"({time.time() - started:.0f}s)", flush=True)

        if val["roc_auc"] > best_auc:
            best_auc, stale = val["roc_auc"], 0
            torch.save({
                "state_dict": model.state_dict(),
                "version": f"resnet50-cbis-{args.init}-e{epoch}-valauc{val['roc_auc']:.3f}",
                "threshold": threshold,
                "validation": {"epoch": epoch, **val},
                "init": args.init,
                "image_size": [IMAGE_HEIGHT, IMAGE_WIDTH],
                "args": vars(args),
            }, WEIGHTS_PATH)
            print(f"  saved {WEIGHTS_PATH}")
        else:
            stale += 1
            if stale >= args.patience:
                print(f"no improvement for {args.patience} epochs, stopping")
                break

    (WEIGHTS_DIR / "training_history.json").write_text(json.dumps(history, indent=2))
    print(f"best val ROC-AUC {best_auc:.4f}. Next: python evaluate.py, then python export_onnx.py")


if __name__ == "__main__":
    main()
