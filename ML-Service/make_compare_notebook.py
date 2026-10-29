"""Build kaggle_compare.ipynb: compares the cancer models on VinDr-Mammo (compare_screening.py) on a Kaggle GPU.

Run: python make_compare_notebook.py
Kaggle settings: GPU T4, Internet on, datasets shantanughosh/vindr-mammogram-dataset-dicom-to-png,
truthisneverlinear/vindr-mammo-annotations and ragibahsanrafi/breastcare-resnet-cbis attached.
"""
import json
from pathlib import Path

from make_kaggle_notebook import code, markdown

ROOT = Path(__file__).parent
SCRIPTS = ["metrics.py", "mammoscreen_pipeline.py", "evaluate_mammoscreen.py", "compare_screening.py"]
DATASETS = ["shantanughosh/vindr-mammogram-dataset-dicom-to-png", "truthisneverlinear/vindr-mammo-annotations",
            "ragibahsanrafi/breastcare-resnet-cbis"]


def main():
    cells = [markdown("# BreastCare: compare cancer models on VinDr-Mammo (screening-style test)")]
    cells.append(code("import os, pathlib\nWORK = pathlib.Path('/kaggle/working/ML-Service')\n"
                      "WORK.mkdir(parents=True, exist_ok=True)\nos.chdir(WORK)"))
    for script in SCRIPTS:
        cells.append(code(f"%%writefile {script}\n" + (ROOT / script).read_text(encoding="utf-8")))
    cells.append(code('!pip install -q "transformers==4.47.0" "onnxruntime>=1.18" timm albumentations\n'
                      "!python compare_screening.py"))
    notebook = {"cells": cells,
                "metadata": {"kernelspec": {"display_name": "Python 3", "language": "python", "name": "python3"},
                             "language_info": {"name": "python"}},
                "nbformat": 4, "nbformat_minor": 5}
    (ROOT / "kaggle_compare.ipynb").write_text(json.dumps(notebook, indent=1), encoding="utf-8")
    print(f"wrote {ROOT / 'kaggle_compare.ipynb'} ({len(cells)} cells)")


if __name__ == "__main__":
    main()
