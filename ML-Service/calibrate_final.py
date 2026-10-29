"""Combine the website's current estimate with the RSNA 1st-place model (fold 0) into one probability.

Input: weights/firstplace/inbreast_comparison.json (per-image scores on INbreast, from evaluate_firstplace.py).
Every other public labelled dataset is inside the 1st-place model's training data, so INbreast is the only fair
place to fit and test the combination. To keep the test honest it is done by patient-split cross-validation: the
combination is fitted on half of the patients and scored on the other half, both ways, and every image's reported
score comes from a fit that never saw its patient. That is repeated with 20 different random halves.

Labels: BI-RADS 5-6 (6 = biopsy-proven cancer) = malignant, BI-RADS 1-3 = benign; BI-RADS 4 is reported separately.
The deployed combination is then fitted on all INbreast images (weights/final/final.json).
"""
import json
from pathlib import Path

import numpy as np
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import roc_auc_score

import metrics

SOURCE = Path("weights/firstplace/inbreast_comparison.json")
OUT = Path("weights/final")
EPS = 1e-6
REPEATS = 20


def logit(p):
    p = np.clip(np.asarray(p, dtype=np.float64), EPS, 1 - EPS)
    return np.log(p / (1 - p))


def main():
    d = json.loads(SOURCE.read_text())["scores"]
    rows = d["rows"]
    x = np.column_stack([logit(d["current_combination"]), logit(d["firstplace_fold0"])])
    birads = np.array([r["birads"] for r in rows])
    studies = np.array([r["study"] for r in rows])
    views = np.array([r["view"] for r in rows])
    fit_mask = birads != 4
    y = (birads >= 5).astype(int)

    patients = np.unique(studies)
    oof = np.zeros((REPEATS, len(rows)))
    for repeat in range(REPEATS):
        rng = np.random.default_rng(repeat)
        half = set(rng.permutation(patients)[: len(patients) // 2])
        part = np.array([s in half for s in studies])
        for train in (part, ~part):
            lr = LogisticRegression().fit(x[train & fit_mask], y[train & fit_mask])
            oof[repeat, ~train] = lr.predict_proba(x[~train])[:, 1]

    def summary(task_mask, positive):
        labels = positive[task_mask].astype(int)
        aucs = [roc_auc_score(labels, oof[r, task_mask]) for r in range(REPEATS)]
        accs = [((oof[r, task_mask] >= 0.5) == labels).mean() for r in range(REPEATS)]
        # Full report (with 95% intervals over patients) for the first repeat; the spread over repeats alongside.
        report = metrics.report(labels, oof[0, task_mask], views[task_mask], studies[task_mask], 0.5)
        report["repeats"] = {"auc_mean": float(np.mean(aucs)), "auc_range": [float(min(aucs)), float(max(aucs))],
                             "accuracy_mean": float(np.mean(accs)), "accuracy_range": [float(min(accs)), float(max(accs))]}
        return report

    task_a = summary(birads != 4, birads >= 5)
    task_b = summary(np.ones(len(rows), bool), birads >= 4)
    for name, r in (("BI-RADS 5-6 vs 1-3", task_a), ("BI-RADS 4-6 vs 1-3", task_b)):
        o = r["overall"]
        print(f"{name}: auc {o['roc_auc']:.3f} acc {o['accuracy']:.3f} sens {o['recall']:.3f} spec {o['specificity']:.3f} "
              f"| over {REPEATS} splits: auc {r['repeats']['auc_mean']:.3f} {np.round(r['repeats']['auc_range'], 3)}, "
              f"acc {r['repeats']['accuracy_mean']:.3f}")

    final = LogisticRegression().fit(x[fit_mask], y[fit_mask])
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "final.json").write_text(json.dumps({
        "version": "final: (resnet50-cbis + mammoscreen-net0) + rsna-1st-place-convnext-fold0",
        "threshold": 0.5,
        "decision_rule": "malignant when P(malignant) >= 50% (the larger of the two percentages), otherwise benign",
        "coefficients": final.coef_[0].tolist(),
        "intercept": float(final.intercept_[0]),
        "features": ["logit(current combined P)", "logit(1st-place fold0 P)"],
        "logit_eps": EPS,
        "fitted_on": "all INbreast images except BI-RADS 4 (BI-RADS 5-6 malignant, 1-3 benign)",
        "tests": [
            {"key": "inbreast_a", "name": "INbreast (Portugal, digital), patients not used for fitting",
             "labels": "BI-RADS 5-6 (6 = biopsy-proven) vs BI-RADS 1-3",
             "note": f"Patient-split cross-validation, {REPEATS} repeats; no model was trained on INbreast.", **task_a},
            {"key": "inbreast_b", "name": "INbreast, including BI-RADS 4 as positive",
             "labels": "BI-RADS 4-6 vs BI-RADS 1-3",
             "note": "BI-RADS 4 is suspicious; many such findings are benign at biopsy.", **task_b},
        ],
    }, indent=2))
    print("coefficients", final.coef_[0].tolist(), "intercept", float(final.intercept_[0]), "->", OUT / "final.json")


if __name__ == "__main__":
    main()
