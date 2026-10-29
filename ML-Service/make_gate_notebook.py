"""Build kaggle_gate.ipynb: trains the "is this a mammogram?" gate (train_gate.py) on a Kaggle GPU.

Run: python make_gate_notebook.py
Kaggle settings: GPU T4, Internet on, and the datasets listed in train_gate.SOURCES attached.
Output: gate_model.zip (gate.onnx, gate_metrics.json).
"""
import json
from pathlib import Path

from make_kaggle_notebook import code, markdown

ROOT = Path(__file__).parent


def main():
    cells = [markdown("""
# BreastCare: train the mammogram gate

Teaches a small network to tell mammograms from everything else (photos, grey cartoons, drawings, chest and
dental X-rays, ultrasound, documents), so the website refuses non-mammogram uploads. First checks how it does on
kinds of images it never saw, then trains the final gate on everything. Download `gate_model.zip` afterwards.
""")]
    cells.append(code("""
import os, pathlib
WORK = pathlib.Path('/kaggle/working/ML-Service')
WORK.mkdir(parents=True, exist_ok=True)
os.chdir(WORK)
"""))
    cells.append(code("%%writefile train_gate.py\n" + (ROOT / "train_gate.py").read_text(encoding="utf-8")))
    cells.append(code('!pip install -q "onnx>=1.16" "onnxruntime>=1.18"\n!python train_gate.py'))
    cells.append(code("""
import shutil, zipfile
out = WORK / 'weights' / 'gate'
with zipfile.ZipFile('/kaggle/working/gate_model.zip', 'w', zipfile.ZIP_DEFLATED) as z:
    for path in out.rglob('*'):
        if path.is_file():
            z.write(path, path.relative_to(out))
shutil.rmtree(WORK / 'weights')
print('done: download gate_model.zip')
"""))
    notebook = {"cells": cells,
                "metadata": {"kernelspec": {"display_name": "Python 3", "language": "python", "name": "python3"},
                             "language_info": {"name": "python"}},
                "nbformat": 4, "nbformat_minor": 5}
    (ROOT / "kaggle_gate.ipynb").write_text(json.dumps(notebook, indent=1), encoding="utf-8")
    print(f"wrote {ROOT / 'kaggle_gate.ipynb'} ({len(cells)} cells)")


if __name__ == "__main__":
    main()
