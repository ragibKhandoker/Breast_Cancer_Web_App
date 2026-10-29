"""Train the "is this a mammogram?" gate that runs before any analysis. Runs on Kaggle (GPU).

Run inside kaggle_gate.ipynb (built by make_gate_notebook.py) with the datasets listed in SOURCES attached.

The backend refuses an upload unless this gate says it is a mammogram, so grey cartoons, photos, chest or dental
X-rays, ultrasound, drawings and documents never reach the cancer model.

Honesty check first: a model is trained WITHOUT some categories (cartoons, sketches, and the MIAS mammograms) and
tested on them, to measure how it does on kinds of images it has never seen. The final gate is then trained on
everything. Both results go into gate_metrics.json.

Preprocessing (repeated in Java, OnnxPredictor/PillowResize): 8-bit grayscale -> Pillow BILINEAR resize to
224 x 224 -> float 0-255, shape 1x1x224x224. Normalisation is inside the exported graph.
Output: weights/gate/gate.onnx (P(mammogram)), gate_metrics.json.
"""
import glob
import json
import random
import re
import time
from datetime import datetime, timezone
from pathlib import Path

import numpy as np
import torch
from PIL import Image, ImageDraw, ImageFilter, ImageOps
from torch.utils.data import DataLoader, Dataset
from torchvision import models, transforms

SIZE = 224
OUT = Path("weights/gate")
SEED = 11
# (Kaggle dataset, label 1 = mammogram, category, how many images to use, path filter, smallest file in bytes)
SOURCES = [
    # CBIS-DDSM's jpeg folder also holds lesion crops and binary ROI masks; only full mammograms are > 500 KB.
    ("awsaf49/cbis-ddsm-breast-cancer-image-dataset", 1, "mammo_cbis", 2500, r"/jpeg/.*\.jpg$", 500_000),
    ("theoviel/rsna-breast-cancer-512-pngs", 1, "mammo_rsna", 3000, r"\.png$", 0),
    ("shantanughosh/vindr-mammogram-dataset-dicom-to-png", 1, "mammo_vindr", 2500, r"\.(png|jpg)$", 0),
    ("nhanvi/mias-png", 1, "mammo_mias", 400, r"mdb\d+\.png$", 0),
    ("paultimothymooney/chest-xray-pneumonia", 0, "chest_xray", 1500, r"^(?!.*__MACOSX).*\.jpe?g$", 0),
    ("aryashah2k/breast-ultrasound-images-dataset", 0, "ultrasound", 800, r"\(\d+\)\.png$", 0),
    ("imtkaggleteam/dental-opg-xray-dataset", 0, "dental_xray", 800, r"\.(jpe?g|png)$", 0),
    ("jhoward/imagenette-160-px", 0, "photo", 2500, r"\.(jpe?g|png)$", 0),  # a .tgz, unpacked first
    ("balabaskar/tom-and-jerry-image-classification", 0, "cartoon", 1500, r"\.(jpe?g|png)$", 0),
    ("wanghaohan/imagenetsketch", 0, "sketch", 1500, r"\.(jpe?g|png)$", 0),
    ("naderabdelghany/iam-handwritten-forms-dataset", 0, "document", 600, r"\.(png|jpe?g)$", 0),
]
CACHE = Path("/kaggle/temp/gate_cache") if Path("/kaggle").exists() else Path("gate_cache")
CACHE_SIZE = 512  # longest side of the cached copy
HELD_OUT = {"cartoon", "sketch", "mammo_mias"}  # never seen by the check model
SYNTHETIC = 1200  # gradients, noise, shapes, blank pages, text


def find_root(slug):
    name = slug.split("/")[1]
    for candidate in [f"/kaggle/input/{name}", *glob.glob(f"/kaggle/input/*{name}*"), *glob.glob(f"/kaggle/input/**/{name}", recursive=True)]:
        if Path(candidate).is_dir():
            return Path(candidate)
    raise SystemExit(f"Attach the Kaggle dataset {slug}")


def unpack_archives(root):
    """Datasets shipped as .tgz (imagenette) are unpacked once into the cache folder."""
    import tarfile
    archives = glob.glob(str(root / "*.tgz")) + glob.glob(str(root / "*.tar.gz"))
    if not archives:
        return root
    target = CACHE / "unpacked" / root.name
    if not target.exists():
        for archive in archives:
            with tarfile.open(archive) as tar:
                tar.extractall(target, filter="data")
    return target


def collect(rng):
    items = []
    for slug, label, category, limit, pattern, min_bytes in SOURCES:
        regex = re.compile(pattern, re.I)
        root = unpack_archives(find_root(slug))
        files = sorted(p for p in glob.glob(str(root / "**" / "*"), recursive=True)
                       if regex.search(p.replace("\\", "/")) and Path(p).is_file() and Path(p).stat().st_size >= min_bytes)
        rng.shuffle(files)
        items += [(f, label, category) for f in files[:limit]]
        print(f"{category:12s} {min(len(files), limit):5d} of {len(files)}")
    items += [(f"synthetic:{i}", 0, "synthetic") for i in range(SYNTHETIC)]
    return items


def synthetic(index):
    rng = random.Random(index)
    kind = index % 5
    img = Image.new("L", (rng.randint(200, 900), rng.randint(200, 900)), rng.randint(0, 255))
    draw = ImageDraw.Draw(img)
    if kind == 0:  # gradient, left-right or top-bottom
        w, h = img.size
        ramp = np.linspace(rng.randint(0, 120), rng.randint(130, 255), w if rng.random() < 0.5 else h)
        arr = np.tile(ramp, (h, 1)) if len(ramp) == w else np.tile(ramp[:, None], (1, w))
        img = Image.fromarray(arr.astype(np.uint8))
    elif kind == 1:  # noise
        img = Image.fromarray(np.random.default_rng(index).integers(0, 256, img.size[::-1], dtype=np.uint8))
    elif kind == 2:  # shapes, like a simple drawing
        for _ in range(rng.randint(3, 15)):
            box = sorted([rng.randint(0, img.size[0]), rng.randint(0, img.size[0])])
            boy = sorted([rng.randint(0, img.size[1]), rng.randint(0, img.size[1])])
            (draw.ellipse if rng.random() < 0.5 else draw.rectangle)([box[0], boy[0], box[1], boy[1]],
                                                                     fill=rng.randint(0, 255), outline=rng.randint(0, 255))
    elif kind == 3:  # text page
        img = Image.new("L", img.size, rng.randint(200, 255))
        draw = ImageDraw.Draw(img)
        for y in range(10, img.size[1], 22):
            draw.text((10, y), "".join(rng.choice("abcdefghij klmnop qrstuv 0123") for _ in range(60)), fill=rng.randint(0, 80))
    else:  # bright blob on black (looks a little like a breast, but is not)
        img = Image.new("L", img.size, 0)
        draw = ImageDraw.Draw(img)
        w, h = img.size
        draw.ellipse([-w // 2, h // 8, w // 2 + rng.randint(0, w // 2), h - h // 8], fill=rng.randint(120, 230))
        img = img.filter(ImageFilter.GaussianBlur(rng.randint(2, 20)))
    return img


def load(path):
    if path.startswith("synthetic:"):
        return synthetic(int(path.split(":")[1]))
    with Image.open(path) as img:
        img.draft("L", (CACHE_SIZE, CACHE_SIZE))  # JPEG: decode at reduced size, much faster
        return ImageOps.exif_transpose(img).convert("L")


def _cache_one(job):
    index, path = job
    target = CACHE / f"{index}.png"
    if not target.exists():
        try:
            img = load(path)
            img.thumbnail((CACHE_SIZE, CACHE_SIZE), Image.BILINEAR)
            img.save(target)
        except Exception:
            return None
    return str(target)


def cache(items):
    """Decode every image once into a small grayscale PNG; the big originals are too slow to re-read each epoch."""
    from multiprocessing import Pool
    CACHE.mkdir(parents=True, exist_ok=True)
    started = time.time()
    jobs = [(i, path) for i, (path, _, _) in enumerate(items) if not path.startswith("synthetic:")]
    with Pool(4) as pool:
        cached = dict(zip((j[0] for j in jobs), pool.map(_cache_one, jobs, chunksize=32)))
    kept = [(cached.get(i, path), label, category) for i, (path, label, category) in enumerate(items)
            if path.startswith("synthetic:") or cached.get(i)]
    print(f"cached {len(kept)} images ({len(items) - len(kept)} unreadable dropped) in {time.time() - started:.0f}s")
    return kept


def to_input(img):
    """The exact backend preprocessing: grayscale -> Pillow BILINEAR 224 x 224 -> float 0-255."""
    return torch.from_numpy(np.asarray(img.resize((SIZE, SIZE), Image.BILINEAR), dtype=np.float32)).unsqueeze(0)


augment = transforms.Compose([
    transforms.RandomResizedCrop(SIZE * 2, scale=(0.6, 1.0), ratio=(0.4, 1.6)),
    transforms.RandomHorizontalFlip(),
    transforms.RandomRotation(10),
    transforms.ColorJitter(brightness=0.3, contrast=0.3),
])


class GateDataset(Dataset):
    def __init__(self, items, train):
        self.items, self.train = items, train

    def __len__(self):
        return len(self.items)

    def __getitem__(self, i):
        path, label, _ = self.items[i]
        try:
            img = load(path)
        except Exception:
            img, label = synthetic(i), 0
        if self.train:
            img = augment(img)
        return to_input(img), torch.tensor(float(label))


class Gate(torch.nn.Module):
    """MobileNetV3-small; input 1x1x224x224 float 0-255, output P(mammogram) of shape (1, 1)."""

    def __init__(self):
        super().__init__()
        self.net = models.mobilenet_v3_small(weights=models.MobileNet_V3_Small_Weights.IMAGENET1K_V1)
        self.net.classifier[3] = torch.nn.Linear(self.net.classifier[3].in_features, 1)
        self.register_buffer("mean", torch.tensor([0.485, 0.456, 0.406]).view(1, 3, 1, 1))
        self.register_buffer("std", torch.tensor([0.229, 0.224, 0.225]).view(1, 3, 1, 1))

    def logits(self, x):
        return self.net((x.expand(-1, 3, -1, -1) / 255.0 - self.mean) / self.std).squeeze(1)

    def forward(self, x):
        return torch.sigmoid(self.logits(x)).unsqueeze(1)


def split(items, rng, fraction=0.15):
    items = items[:]
    rng.shuffle(items)
    n = int(len(items) * fraction)
    return items[n:], items[:n]


def train(items, device, epochs=6):
    model = Gate().to(device)
    loader = DataLoader(GateDataset(items, True), batch_size=64, shuffle=True, num_workers=4, drop_last=True)
    positives = sum(i[1] for i in items)
    criterion = torch.nn.BCEWithLogitsLoss(pos_weight=torch.tensor((len(items) - positives) / max(positives, 1), device=device))
    optimizer = torch.optim.AdamW(model.parameters(), lr=1e-3, weight_decay=1e-4)
    scheduler = torch.optim.lr_scheduler.OneCycleLR(optimizer, max_lr=1e-3, epochs=epochs, steps_per_epoch=len(loader))
    for epoch in range(epochs):
        model.train()
        started, total = time.time(), 0.0
        for x, y in loader:
            optimizer.zero_grad()
            loss = criterion(model.logits(x.to(device)), y.to(device))
            loss.backward()
            optimizer.step()
            scheduler.step()
            total += loss.item()
        print(f"  epoch {epoch + 1}: loss {total / len(loader):.4f} ({time.time() - started:.0f}s)", flush=True)
    return model.eval()


@torch.inference_mode()
def scores(model, items, device):
    out = []
    for x, _ in DataLoader(GateDataset(items, False), batch_size=128, num_workers=4):
        out += model(x.to(device))[:, 0].cpu().tolist()
    return np.array(out)


def by_category(items, values, threshold):
    result = {}
    for category in sorted({i[2] for i in items}):
        mask = np.array([i[2] == category for i in items])
        label = next(i[1] for i in items if i[2] == category)
        accepted = float((values[mask] >= threshold).mean())
        result[category] = {"images": int(mask.sum()), "is_mammogram": bool(label),
                            "accepted": accepted, "correct": accepted if label else 1 - accepted}
    return result


def pick_threshold(items, values, keep=0.99):
    """Lowest threshold that still accepts at least 99% of real mammograms (validation only)."""
    positive = np.sort(values[np.array([i[1] == 1 for i in items])])
    return float(min(0.5, positive[int((1 - keep) * len(positive))]))


def main():
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    rng = random.Random(SEED)
    torch.manual_seed(SEED)
    OUT.mkdir(parents=True, exist_ok=True)
    items = cache(collect(rng))

    print("1) check model: trained without", sorted(HELD_OUT))
    seen = [i for i in items if i[2] not in HELD_OUT]
    unseen = [i for i in items if i[2] in HELD_OUT]
    seen_train, seen_test = split(seen, rng)
    check = train(seen_train, device)
    check_scores = scores(check, seen_test, device)
    threshold = pick_threshold(seen_test, check_scores)
    check_result = {
        "threshold": threshold,
        "seen_categories_test": by_category(seen_test, check_scores, threshold),
        "unseen_categories": by_category(unseen, scores(check, unseen, device), threshold),
    }
    for name, part in (("seen", "seen_categories_test"), ("unseen", "unseen_categories")):
        for category, r in check_result[part].items():
            print(f"  {name:6s} {category:12s} correct {r['correct']:.3f} ({r['images']} images)")

    print("2) final gate: trained on every category")
    final_train, final_test = split(items, rng)
    gate = train(final_train, device)
    final_scores = scores(gate, final_test, device)
    threshold = pick_threshold(final_test, final_scores)
    final_result = {"threshold": threshold, "test": by_category(final_test, final_scores, threshold)}
    for category, r in final_result["test"].items():
        print(f"  final  {category:12s} correct {r['correct']:.3f} ({r['images']} images)")

    gate = gate.float().cpu().eval()
    path = OUT / "gate.onnx"
    torch.onnx.export(gate, torch.rand(1, 1, SIZE, SIZE) * 255, path, input_names=["image"], output_names=["output"],
                      dynamo=False, opset_version=17)
    import onnxruntime as ort
    session = ort.InferenceSession(str(path), providers=["CPUExecutionProvider"])
    x = torch.rand(1, 1, SIZE, SIZE) * 255
    with torch.inference_mode():
        parity = float(np.abs(session.run(None, {"image": x.numpy()})[0] - gate(x).numpy()).max())
    assert parity < 1e-4, parity
    (OUT / "gate_metrics.json").write_text(json.dumps({
        "evaluated_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "input": {"size": SIZE, "resize": "Pillow BILINEAR", "range": "0-255 grayscale"},
        "threshold": threshold,
        "sources": [{"dataset": s[0], "category": s[2], "is_mammogram": bool(s[1])} for s in SOURCES],
        "check_model_unseen_categories": check_result,
        "final": final_result,
        "onnx_parity": parity,
    }, indent=2))
    print(f"wrote {OUT} (threshold {threshold:.4f}, onnx parity {parity:.2e})")


if __name__ == "__main__":
    main()
