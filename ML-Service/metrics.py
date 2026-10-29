"""Classification metrics for benign (0) / malignant (1) scores, shared by train.py and evaluate.py."""
import numpy as np
from sklearn.metrics import roc_auc_score


def at_threshold(labels, scores, threshold):
    """Accuracy, precision, recall (sensitivity), specificity, F1, ROC-AUC and the confusion counts."""
    labels = np.asarray(labels, dtype=int)
    scores = np.asarray(scores, dtype=float)
    predicted = (scores >= threshold).astype(int)
    tp = int(((predicted == 1) & (labels == 1)).sum())
    tn = int(((predicted == 0) & (labels == 0)).sum())
    fp = int(((predicted == 1) & (labels == 0)).sum())
    fn = int(((predicted == 0) & (labels == 1)).sum())

    def ratio(a, b):
        return a / b if b else 0.0

    precision = ratio(tp, tp + fp)
    recall = ratio(tp, tp + fn)
    return {
        "images": int(len(labels)),
        "malignant": int(labels.sum()),
        "benign": int(len(labels) - labels.sum()),
        "accuracy": ratio(tp + tn, len(labels)),
        "precision": precision,
        "recall": recall,  # sensitivity: share of malignant images the model flags
        "specificity": ratio(tn, tn + fp),  # share of benign images the model clears
        "f1": ratio(2 * precision * recall, precision + recall),
        "roc_auc": float(roc_auc_score(labels, scores)) if 0 < labels.sum() < len(labels) else None,
        "confusion_matrix": {"true_positive": tp, "false_positive": fp, "true_negative": tn, "false_negative": fn},
    }


def best_threshold(labels, scores):
    """The threshold with the highest balanced accuracy (Youden's J = sensitivity + specificity - 1).

    Chosen on the validation set only, never on the test set, so the test numbers stay honest.
    """
    labels = np.asarray(labels, dtype=int)
    scores = np.asarray(scores, dtype=float)
    best, best_j = 0.5, -1.0
    for threshold in np.unique(np.round(scores, 4)):
        m = at_threshold(labels, scores, threshold)
        j = m["recall"] + m["specificity"] - 1
        if j > best_j:
            best, best_j = float(threshold), j
    return best


def patient_bootstrap(labels, scores, patients, threshold, rounds=1000, seed=0):
    """95% intervals for accuracy, recall, specificity and AUC, resampling whole patients.

    A patient's CC and MLO images are not independent, so resampling images would make the intervals too narrow.
    """
    labels = np.asarray(labels, dtype=int)
    scores = np.asarray(scores, dtype=float)
    patients = np.asarray(patients)
    groups = [np.flatnonzero(patients == p) for p in np.unique(patients)]
    rng = np.random.default_rng(seed)
    keys = ("accuracy", "recall", "specificity", "roc_auc")
    samples = {k: [] for k in keys}
    for _ in range(rounds):
        index = np.concatenate([groups[i] for i in rng.integers(0, len(groups), len(groups))])
        m = at_threshold(labels[index], scores[index], threshold)
        for k in keys:
            if m[k] is not None:
                samples[k].append(m[k])
    return {k: [float(np.percentile(v, 2.5)), float(np.percentile(v, 97.5))] for k, v in samples.items() if v}


def report(labels, scores, views, patients, threshold, bootstrap=True):
    """Overall metrics plus the same metrics for CC and MLO images separately."""
    labels, scores, views = np.asarray(labels), np.asarray(scores), np.asarray(views)
    result = {"threshold": threshold, "overall": at_threshold(labels, scores, threshold), "by_view": {}}
    if bootstrap:
        result["overall"]["ci95"] = patient_bootstrap(labels, scores, patients, threshold)
    for view in ("CC", "MLO"):
        mask = views == view
        if mask.any():
            result["by_view"][view] = at_threshold(labels[mask], scores[mask], threshold)
            if bootstrap:
                result["by_view"][view]["ci95"] = patient_bootstrap(
                    labels[mask], scores[mask], np.asarray(patients)[mask], threshold)
    result["patients"] = int(len(set(patients)))
    return result
