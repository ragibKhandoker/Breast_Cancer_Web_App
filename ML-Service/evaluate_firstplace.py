"""Test the RSNA 2023 1st-place mammography model on INbreast, next to the model the website uses now. Kaggle GPU.

1st place: dangnh0611 (MIT, github.com/dangnh0611/kaggle_rsna_breast_cancer), Kaggle dataset
dangnh0611/rsna-breast-cancer-detection-best-ckpts: 4 ConvNeXt-small folds (timm convnext_small.fb_in22k_ft_in1k_384,
1 output, sigmoid), input 2048 x 1024 x 3, pixels 0-255 normalised with ImageNet mean/std * 255.
Their preprocessing (src/submit/submit.py): crop the breast (YOLOX, falling back to Otsu thresholding - the Otsu
path is used here), resize keeping the aspect ratio (bilinear) and pad centred with black to 2048 x 1024.

Why INbreast: the 1st-place model was trained on RSNA + VinDr-Mammo + MiniDDSM (DDSM, which contains CBIS-DDSM) +
CMMD + CDD-CESM + BMCD, so our CBIS-DDSM and VinDr tests are inside its training data. INbreast (Portugal, digital,
410 images, 115 patients) is in neither model's training data. Labels are radiologist BI-RADS:
  A: BI-RADS 5-6 (6 = biopsy-proven cancer) vs BI-RADS 1-3          B: BI-RADS 4-6 vs BI-RADS 1-3
Output: weights/firstplace/inbreast_comparison.json
"""
import argparse
import glob
import json
import time
from pathlib import Path

import cv2
import numpy as np
import pandas as pd
import pydicom
import torch
import torch.nn.functional as F
from pydicom.pixels import apply_voi_lut

import metrics

OUT = Path("weights/firstplace")
CACHE = Path("/kaggle/temp/inbreast_png")
SIZE = (2048, 1024)


def find(pattern):
    hits = sorted(glob.glob(f"/kaggle/input/**/{pattern}", recursive=True))
    if not hits:
        raise SystemExit(f"missing {pattern}")
    return hits


def inbreast():
    """8-bit PNGs of every INbreast DICOM (VOI LUT applied, as the 1st-place code does) and their BI-RADS."""
    table = pd.read_excel(find("INbreast.xls")[0])
    table = table[pd.to_numeric(table["File Name"], errors="coerce").notna()]
    birads = {str(int(r["File Name"])): str(r["Bi-Rads"]).strip() for _, r in table.iterrows()}
    CACHE.mkdir(parents=True, exist_ok=True)
    rows = []
    for dcm_path in find("AllDICOMs/*.dcm"):
        name = Path(dcm_path).name
        file_id, patient, _, side, view = name.split("_")[:5]
        if file_id not in birads:
            continue
        png = CACHE / f"{file_id}.png"
        if not png.exists():
            dcm = pydicom.dcmread(dcm_path)
            arr = apply_voi_lut(dcm.pixel_array, dcm).astype(np.float32)
            if dcm.PhotometricInterpretation == "MONOCHROME1":
                arr = arr.max() - arr
            arr = (arr - arr.min()) / max(arr.max() - arr.min(), 1e-6) * 255
            cv2.imwrite(str(png), arr.astype(np.uint8))
        grade = int(birads[file_id][0])
        rows.append({"path": str(png), "study": patient, "side": side, "view": "MLO" if view == "ML" else view,
                     "birads": grade})
    return rows


def otsu_box(img):
    """The 1st-place fallback crop (src/roi_det/roi_extract.py, extract_roi_otsu)."""
    blur = cv2.GaussianBlur(img, (5, 5), 0)
    _, binary = cv2.threshold(blur, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)
    contours, _ = cv2.findContours(binary, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    if not contours:
        return 0, 0, img.shape[1], img.shape[0]
    x, y, w, h = cv2.boundingRect(max(contours, key=cv2.contourArea))
    if w * h < 0.04 * img.shape[0] * img.shape[1]:
        return 0, 0, img.shape[1], img.shape[0]
    return x, y, w, h


def firstplace_input(gray, device):
    x, y, w, h = otsu_box(gray)
    crop = torch.from_numpy(gray[y:y + h, x:x + w].astype(np.float32)).to(device)
    ratio = min(SIZE[0] / crop.shape[0], SIZE[1] / crop.shape[1])
    resized = F.interpolate(crop[None, None], mode="bilinear", scale_factor=ratio, recompute_scale_factor=True)[0, 0]
    padded = torch.zeros(SIZE, device=device)
    top, left = (SIZE[0] - resized.shape[0]) // 2, (SIZE[1] - resized.shape[1]) // 2
    padded[top:top + resized.shape[0], left:left + resized.shape[1]] = resized
    return padded.round().clamp(0, 255)[None, None].expand(-1, 3, -1, -1)


def load_folds(device, only_first=False):
    import argparse as ap
    import timm
    # timm training checkpoints also store their args and the best metric (a numpy scalar). Allow exactly those
    # plain types so weights_only loading still refuses anything that could run code.
    # The checkpoints were saved with numpy 1, so the pickle names numpy.core.*; numpy 2 keeps it in numpy._core.
    scalar = np._core.multiarray.scalar if hasattr(np, "_core") else np.core.multiarray.scalar
    torch.serialization.add_safe_globals([ap.Namespace, (scalar, "numpy.core.multiarray.scalar"), np.dtype,
                                          np.dtypes.Float64DType, np.dtypes.Float32DType, np.dtypes.Int64DType])
    folds = []
    for path in sorted(find("best_convnext_fold_*.pth.tar"))[:1 if only_first else None]:
        model = timm.create_model("convnext_small.fb_in22k_ft_in1k_384", num_classes=1, in_chans=3, pretrained=False)
        checkpoint = torch.load(path, map_location="cpu", weights_only=True)
        state = checkpoint.get("state_dict_ema") or checkpoint.get("state_dict") or checkpoint
        state = {k.replace("module.", "", 1): v for k, v in state.items()}
        model.load_state_dict(state)
        folds.append(model.eval().to(device))
        print("loaded", Path(path).name, "ema" if "state_dict_ema" in checkpoint else "")
    cfg = timm.data.resolve_data_config({}, model=folds[0])
    mean = torch.tensor(cfg["mean"], device=device).view(1, 3, 1, 1) * 255
    std = torch.tensor(cfg["std"], device=device).view(1, 3, 1, 1) * 255
    return folds, mean, std


def report(rows, values, task, threshold):
    keep = [i for i, r in enumerate(rows) if r["birads"] <= 3 or (r["birads"] >= 5 if task == "A" else r["birads"] >= 4)]
    sub = [rows[i] for i in keep]
    labels = [int(r["birads"] >= (5 if task == "A" else 4)) for r in sub]
    return metrics.report(labels, np.asarray(values)[keep], [r["view"] for r in sub], [r["study"] for r in sub], threshold)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--with-current", action="store_true", help="also score the website's current combination")
    args = parser.parse_args()
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    OUT.mkdir(parents=True, exist_ok=True)
    rows = inbreast()
    print(f"INbreast: {len(rows)} images, {len({r['study'] for r in rows})} patients, BI-RADS counts",
          {g: sum(r['birads'] == g for r in rows) for g in range(1, 7)})

    folds, mean, std = load_folds(device)
    per_fold = []
    with torch.inference_mode():
        for r in rows:
            x = (firstplace_input(cv2.imread(r["path"], cv2.IMREAD_GRAYSCALE), device) - mean) / std
            per_fold.append([float(torch.sigmoid(f(x))[0, 0]) for f in folds])
    per_fold = np.array(per_fold)
    candidates = {"firstplace_4folds": per_fold.mean(1), "firstplace_fold0": per_fold[:, 0]}

    if args.with_current:
        from compare_ensemble import Scorer, logit
        manifest = json.load(open(find("ensemble.json")[0]))
        scorer = Scorer(device)
        current = np.array([scorer(r["path"]) for r in rows])
        z = manifest["coefficients"][0] * logit(current[:, 0]) + manifest["coefficients"][1] * logit(current[:, 1]) + manifest["intercept"]
        candidates["current_combination"] = 1 / (1 + np.exp(-z))

    results = {}
    for name, values in candidates.items():
        results[name] = {}
        for task in ("A", "B"):
            # AUC does not depend on a threshold; sensitivity/specificity are shown at 50% (the website's rule).
            results[name][task] = report(rows, values, task, 0.5)
            o = results[name][task]["overall"]
            ci = o["ci95"].get("roc_auc", [float("nan")] * 2)
            print(f"{name:20s} task {task}: auc {o['roc_auc']:.3f} [{ci[0]:.3f}-{ci[1]:.3f}] "
                  f"sens {o['recall']:.3f} spec {o['specificity']:.3f} ({o['malignant']} pos / {o['benign']} neg)")

    cpu = folds[0].float().cpu().eval()
    x = torch.rand(1, 3, *SIZE)
    with torch.inference_mode():
        cpu(x)
        started = time.time()
        cpu(x)
    seconds = round(time.time() - started, 2)
    print(f"one fold on the Kaggle CPU: {seconds}s per image")

    (OUT / "inbreast_comparison.json").write_text(json.dumps({
        "test_set": {"name": "INbreast (Portugal, digital)", "images": len(rows), "patients": len({r['study'] for r in rows}),
                     "labels": "radiologist BI-RADS; 6 = biopsy-proven cancer",
                     "note": "Not in the training data of either model."},
        "kaggle_cpu_seconds_one_fold": seconds,
        "results": results,
        "scores": {"rows": rows, **{k: np.asarray(v).tolist() for k, v in candidates.items()}},
    }, indent=1))
    print("wrote", OUT / "inbreast_comparison.json")


if __name__ == "__main__":
    main()
