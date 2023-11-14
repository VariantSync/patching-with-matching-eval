import numpy


class PatchStrategy:
    def __init__(self, name):
        self.name = name

        self.tp = 0
        self.fp = 0
        self.tn = 0
        self.fn = 0

        self.commitPatches = 0
        self.commitSuccess = 0

        self.file = 0
        self.fileSuccess = 0

        self.line = 0
        self.lineSuccess = 0

        self.applied = 0
        self.invalid = 0
        self.wrongLocation = 0
        self.missing = 0
        self.filteredCorrectly = 0
        self.filteredIncorrectly = 0
        self.mitigatedInvalid = 0
        self.mitigatedMissing = 0

    def getNumCommitFailures(self):
        return self.commitPatches - self.commitSuccess

    def getNumFilePatchFailures(self):
        return self.file - self.fileSuccess

    def getNumLinePatchFailures(self):
        return self.line - self.lineSuccess

    def total(self):
        return (self.applied + self.invalid + self.wrongLocation + self.missing
                + self.filteredCorrectly + self.filteredIncorrectly
                + self.mitigatedInvalid + self.mitigatedMissing)

    def normed_applied(self) -> float:
        return self.normalize(self.applied)

    def normed_invalid(self) -> float:
        return self.normalize(self.invalid)

    def normed_wrongLocation(self) -> float:
        return self.normalize(self.wrongLocation)

    def normed_missing(self) -> float:
        return self.normalize(self.missing)

    def normed_filteredCorrectly(self) -> float:
        return self.normalize(self.filteredCorrectly)

    def normed_filteredIncorrectly(self) -> float:
        return self.normalize(self.filteredIncorrectly)

    def normed_mitigatedMissing(self) -> float:
        return self.normalize(self.mitigatedMissing)

    def normed_mitigatedInvalid(self) -> float:
        return self.normalize(self.mitigatedInvalid)

    def precision(self) -> float:
        if (self.tp + self.fp) == 0:
            return 0.
        return float(self.tp) / float(self.tp + self.fp)

    def recall(self) -> float:
        if (self.tp + self.fn) == 0:
            return 0.
        return float(self.tp) / float(self.tp + self.fn)

    def tpr(self):
        return self.recall()

    def tnr(self):
        if (self.fp + self.tn) == 0:
            return 0.
        return float(self.tn) / float(self.fp + self.tn)

    def balanced_accuracy(self) -> float:
        return (self.tpr() + self.tnr()) / 2.

    def normalize(self, value: int) -> float:
        return 100. * (float(value) / (float(self.total())))


class Experiment:
    def __init__(self):
        self.normal = PatchStrategy("normal")
        self.filtered = PatchStrategy("filtered")
