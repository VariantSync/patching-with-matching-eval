import numpy as np


def calculate_precision_recall(tp, fp, fn):
    precision = tp / float(tp + fp) if (tp + fp) > 0 else np.nan
    recall = tp / float(tp + fn) if (tp + fn) > 0 else np.nan
    return precision, recall


def calculate_f1_score(precision, recall):
    return (
        2 * (precision * recall) / (precision + recall)
        if (precision + recall) > 0
        else np.nan
    )
