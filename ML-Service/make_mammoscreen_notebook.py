"""Build kaggle_mammoscreen.ipynb: tests ianpan/mammoscreen on CBIS-DDSM and exports it to ONNX, on a Kaggle GPU.

Run: python make_mammoscreen_notebook.py

Kaggle settings: Accelerator GPU T4, Internet on, and add the dataset
awsaf49/cbis-ddsm-breast-cancer-image-dataset. Output: mammoscreen_model.zip (ONNX files, test metrics, fixtures).
"""
import json
from pathlib import Path

from make_kaggle_notebook import code, markdown

ROOT = Path(__file__).parent
SCRIPTS = ["metrics.py", "mammoscreen_pipeline.py", "evaluate_mammoscreen.py"]


def main():
    cells = [markdown("""
# BreastCare: test and export ianpan/mammoscreen

Needs **GPU T4**, **Internet on**, and the dataset **awsaf49/cbis-ddsm-breast-cancer-image-dataset** attached.
Scores the CBIS-DDSM test patients with the mammoscreen ensemble (and smaller combinations of its nets),
exports the nets to ONNX for the BreastCare backend, and writes test metrics and Java test fixtures.
Download `mammoscreen_model.zip` from the Output panel afterwards.
""")]
    cells.append(code("""
import os, pathlib
WORK = pathlib.Path('/kaggle/working/ML-Service')
WORK.mkdir(parents=True, exist_ok=True)
os.chdir(WORK)
"""))
    for script in SCRIPTS:
        cells.append(code(f"%%writefile {script}\n" + (ROOT / script).read_text(encoding="utf-8")))
    cells.append(code("""
# The model's own code needs the transformers version it was saved with (newer 5.x breaks custom models).
!pip install -q "transformers==4.47.0" "onnx>=1.16" "onnxruntime>=1.18" timm albumentations
import torch, timm, transformers, cv2
print(torch.__version__, timm.__version__, transformers.__version__, cv2.__version__)
assert torch.cuda.is_available(), "No GPU: set Accelerator to GPU T4"
"""))
    cells.append(code("!python evaluate_mammoscreen.py"))
    cells.append(code("""
import shutil, zipfile
out = WORK / 'weights' / 'mammoscreen'
with zipfile.ZipFile('/kaggle/working/mammoscreen_model.zip', 'w', zipfile.ZIP_DEFLATED) as z:
    for path in out.rglob('*'):
        if path.is_file():
            z.write(path, path.relative_to(out))
shutil.rmtree(WORK / 'weights')
print('done: download mammoscreen_model.zip')
"""))
    notebook = {
        "cells": cells,
        "metadata": {"kernelspec": {"display_name": "Python 3", "language": "python", "name": "python3"},
                     "language_info": {"name": "python"}},
        "nbformat": 4,
        "nbformat_minor": 5,
    }
    (ROOT / "kaggle_mammoscreen.ipynb").write_text(json.dumps(notebook, indent=1), encoding="utf-8")
    print(f"wrote {ROOT / 'kaggle_mammoscreen.ipynb'} ({len(cells)} cells)")


if __name__ == "__main__":
    main()
