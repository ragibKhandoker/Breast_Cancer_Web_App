"""Build kaggle_train.ipynb: one notebook that trains, tests and exports the model on a free Kaggle GPU.

Run: python make_kaggle_notebook.py      (after changing any of the scripts below, so the notebook matches them)

The notebook carries copies of this folder's scripts and runs them in order. Nothing is trained on this computer.
"""
import json
from pathlib import Path

ROOT = Path(__file__).parent
SCRIPTS = ["model.py", "download_pretrained.py", "prepare_cbis.py", "data_loading.py", "metrics.py",
           "evaluate.py", "train.py", "export_onnx.py"]


def markdown(text):
    return {"cell_type": "markdown", "metadata": {}, "source": text.strip().splitlines(keepends=True)}


def code(text):
    return {"cell_type": "code", "metadata": {}, "execution_count": None, "outputs": [],
            "source": text.strip().splitlines(keepends=True)}


def main():
    cells = [markdown("""
# BreastCare: train the mammogram model on Kaggle

**Before running:** in the right-hand panel set *Accelerator* to **GPU T4 x2** and switch **Internet on**
(Kaggle asks for phone verification once). Then *Run All*. It takes roughly 30-60 minutes.

**What it does**
1. Downloads the starting model (ResNet50 mammography classifier, `umairinayat/medical-models`, MIT) and
   CBIS-DDSM full mammograms (CC and MLO views, CC BY 3.0) from Hugging Face, checking their SHA-256.
2. Splits the images **by patient** into train / validation / test, so no patient is in two splits.
3. Measures the unmodified starting model on the test patients (for comparison only).
4. Fine-tunes on the training patients; the best epoch and the decision threshold are chosen on validation.
5. Measures the trained model once on the untouched test patients: accuracy, precision, recall, specificity,
   F1, ROC-AUC, overall and for CC and MLO, with 95% intervals.
6. Exports `model.onnx` with those numbers inside, and checks that ONNX gives the same answers as PyTorch.

**Afterwards:** download `breastcare_model.zip` from the *Output* panel, unzip it into `ML-Service/weights/`
of the project (replacing `model.onnx`) and restart the backend. The website then runs this model on the
server's CPU and shows its test results to users.
""")]
    cells.append(code("""
import os, pathlib
WORK = pathlib.Path('/kaggle/working/ML-Service') if os.path.isdir('/kaggle') else pathlib.Path('ML-Service-run')
WORK.mkdir(parents=True, exist_ok=True)
os.chdir(WORK)
print(os.getcwd())
"""))
    for script in SCRIPTS:
        cells.append(code(f"%%writefile {script}\n" + (ROOT / script).read_text(encoding="utf-8")))
    cells.append(code("""
!pip install -q "onnx>=1.16" "onnxscript>=0.1" "onnxruntime>=1.18"
import torch
assert torch.cuda.is_available(), "No GPU: set Accelerator to GPU T4 x2 in the notebook settings"
print(torch.__version__, torch.cuda.get_device_name(0))
"""))
    cells.append(code("!python download_pretrained.py && python prepare_cbis.py"))
    cells.append(code("# For comparison only: the published model as it is, on the same test patients.\n"
                      "!python evaluate.py --hf-baseline | tail -n 25"))
    cells.append(code("!python train.py 2>&1 | tee weights/train.log"))
    cells.append(code("!python evaluate.py && python export_onnx.py"))
    cells.append(code("""
# The exported model must give the same probabilities as PyTorch on real test images.
import numpy as np, onnxruntime as ort, torch
from data_loading import MammogramDataset
from model import load_trained
model, _ = load_trained()
session = ort.InferenceSession('weights/model.onnx', providers=['CPUExecutionProvider'])
dataset = MammogramDataset('test')
worst = 0.0
for i in range(0, len(dataset), max(1, len(dataset) // 20)):
    x, _ = dataset[i]
    expected = model(x[None], probabilities=True).detach().numpy()
    actual = session.run(None, {'image': x[None].numpy()})[0]
    worst = max(worst, float(np.abs(expected - actual).max()))
print('largest difference', worst)
assert worst < 1e-3, 'ONNX export does not match PyTorch'
"""))
    cells.append(code("""
import json, zipfile
m = json.load(open('weights/metrics.json'))
o = m['overall']
print(f"test patients {m['patients']}, images {o['images']} ({o['malignant']} malignant)")
for k in ('accuracy', 'precision', 'recall', 'specificity', 'f1', 'roc_auc'):
    print(f"{k:12s} {o[k]:.3f}   95% CI {o.get('ci95', {}).get(k, '')}")
for view, v in m['by_view'].items():
    print(view, {k: round(v[k], 3) for k in ('accuracy', 'recall', 'specificity', 'roc_auc')})
with zipfile.ZipFile('/kaggle/working/breastcare_model.zip' if os.path.isdir('/kaggle') else 'breastcare_model.zip', 'w') as z:
    for name in ('model.onnx', 'metrics.json', 'hf_baseline_metrics.json', 'training_history.json', 'train.log'):
        if (WORK / 'weights' / name).exists():
            z.write(WORK / 'weights' / name, name)
print('Download breastcare_model.zip from the Output panel and unzip it into ML-Service/weights/')

# Kaggle keeps everything in /kaggle/working as output; drop the 1.3 GB of data and the duplicate weights so the
# download is just the zip.
import shutil
for leftover in (WORK / 'data', WORK / 'weights' / 'pretrained', WORK / 'weights' / 'model.pt', WORK / 'weights' / 'model.onnx'):
    if leftover.is_dir():
        shutil.rmtree(leftover)
    elif leftover.exists():
        leftover.unlink()
"""))
    notebook = {
        "cells": cells,
        "metadata": {"kernelspec": {"display_name": "Python 3", "language": "python", "name": "python3"},
                     "language_info": {"name": "python"}},
        "nbformat": 4,
        "nbformat_minor": 5,
    }
    (ROOT / "kaggle_train.ipynb").write_text(json.dumps(notebook, indent=1), encoding="utf-8")
    print(f"wrote {ROOT / 'kaggle_train.ipynb'} ({len(cells)} cells)")


if __name__ == "__main__":
    main()
