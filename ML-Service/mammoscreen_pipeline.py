"""Preprocessing for the ianpan/mammoscreen model, written so the Java backend can repeat it exactly.

Model:   https://huggingface.co/ianpan/mammoscreen (Apache-2.0), commit b619a889a8214f3b3f9e7427c2603608a44e6d0a.
         Ensemble of 3 EfficientNetV2-S nets for screening mammography (cancer probability), trained on
         CBIS-DDSM then the RSNA Screening Mammography challenge. Author's RSNA holdout AUC: 0.945.
Cropper: https://huggingface.co/ianpan/mammo-crop (Apache-2.0), commit bec2e302d47176d743a86d276d8402f876ad0f9f.

Steps (same as the model card, without albumentations, so nothing depends on a library version):
  1. 8-bit grayscale image.
  2. Crop to the breast: resize to 256 x 256 (OpenCV INTER_LINEAR), the crop net returns normalised x, y, w, h.
  3. For each net: nets 0 and 1 pad the width with black to their aspect ratio, then resize to their size;
     net 2 scales the longest side to 1536 and pads the rest with black, centred.
  4. Each net takes float pixels 0-255 (shape 1 x 1 x H x W; normalisation is inside the exported graph) and
     returns P(cancer). The result is the mean over the nets used.
"""
import cv2
import numpy as np

CROP_SIZE = 256
NETS = [
    {"name": "net0", "height": 2048, "width": 1024, "pad_to_aspect": True},
    {"name": "net1", "height": 1920, "width": 1280, "pad_to_aspect": True},
    {"name": "net2", "height": 1536, "width": 1536, "pad_to_aspect": False},
]
MIN_CROP_FRACTION = 0.1  # a crop smaller than this share of the image is treated as a failed crop


def resize_linear(img, height, width):
    return cv2.resize(img, (width, height), interpolation=cv2.INTER_LINEAR)


def crop_input(gray):
    """1 x 1 x 256 x 256 float32 for the crop net."""
    return resize_linear(gray, CROP_SIZE, CROP_SIZE).astype(np.float32)[None, None]


def crop_box(coords, height, width):
    """Pixel box from the crop net's normalised x, y, w, h (as the model card's .int() rescaling)."""
    x = int(coords[0] * width)
    y = int(coords[1] * height)
    w = int(coords[2] * width)
    h = int(coords[3] * height)
    x, y = max(0, min(x, width - 1)), max(0, min(y, height - 1))
    w, h = min(w, width - x), min(h, height - y)
    if w < MIN_CROP_FRACTION * width or h < MIN_CROP_FRACTION * height:
        return 0, 0, width, height
    return x, y, w, h


def pad_to_aspect_ratio(img, aspect_ratio):
    h, w = img.shape[:2]
    if h / w > aspect_ratio:
        new_w = round(h / aspect_ratio)
        left = (new_w - w) // 2
        img = np.pad(img, ((0, 0), (left, new_w - w - left)), mode="constant", constant_values=0)
    return img


def longest_max_size(img, max_size):
    h, w = img.shape[:2]
    scale = max_size / float(max(h, w))
    return resize_linear(img, round(h * scale), round(w * scale))


def pad_if_needed(img, height, width):
    h, w = img.shape[:2]
    top, left = max(0, (height - h) // 2), max(0, (width - w) // 2)
    return np.pad(img, ((top, max(0, height - h - top)), (left, max(0, width - w - left))),
                  mode="constant", constant_values=0)


def net_input(cropped, spec):
    """1 x 1 x H x W float32 (0-255) for one ensemble net."""
    if spec["pad_to_aspect"]:
        img = pad_to_aspect_ratio(cropped, spec["height"] / spec["width"])
        img = resize_linear(img, spec["height"], spec["width"])
    else:
        img = pad_if_needed(longest_max_size(cropped, spec["height"]), spec["height"], spec["width"])
    return img.astype(np.float32)[None, None]
