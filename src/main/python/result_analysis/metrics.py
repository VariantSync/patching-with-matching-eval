
def calculate_precision_recall(tp, fp, fn):
    precision = tp / float(tp + fp) if (tp + fp) > 0 else 0
    recall = tp / float(tp + fn) if (tp + fn) > 0 else 0
    return precision, recall
