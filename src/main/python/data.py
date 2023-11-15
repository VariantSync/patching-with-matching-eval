
class PatchStrategy:
    def __init__(self, name: str):
        self.name = name

        self.commit_patches = 0
        self.commit_success = 0

        self.file = 0
        self.file_success = 0

        self.line = 0
        self.line_success = 0

        self.applied = 0
        self.invalid = 0
        self.wrong_location = 0
        self.missing = 0
        self.filtered_correctly = 0
        self.filtered_incorrectly = 0
        self.mitigated_invalid = 0
        self.mitigated_missing = 0

    def tp(self) -> int:
        return self.applied

    def fp(self) -> int:
        return self.invalid

    def tn(self) -> int:
        return self.filtered_correctly

    def fn(self) -> int:
        return self.missing + self.wrong_location + self.filtered_incorrectly

    def num_commit_failures(self) -> int:
        return self.commit_patches - self.commit_success

    def num_patch_failures(self) -> int:
        return self.file - self.file_success

    def num_line_failures(self) -> int:
        return self.line - self.line_success

    def total(self) -> int:
        """
        Calculates the total count of all classified changes.

        Returns:
            int: The total count of all classified changes.
        """
        return (self.applied + self.invalid
                + self.wrong_location + self.missing
                + self.filtered_correctly + self.filtered_incorrectly
                + self.mitigated_invalid + self.mitigated_missing)

    def normed_applied(self) -> float:
        return self.normalize(self.applied)

    def normed_invalid(self) -> float:
        return self.normalize(self.invalid)

    def normed_wrong_location(self) -> float:
        return self.normalize(self.wrong_location)

    def normed_missing(self) -> float:
        return self.normalize(self.missing)

    def normed_filtered_correctly(self) -> float:
        return self.normalize(self.filtered_correctly)

    def normed_filtered_incorrectly(self) -> float:
        return self.normalize(self.filtered_incorrectly)

    def normed_mitigated_missing(self) -> float:
        return self.normalize(self.mitigated_missing)

    def normed_mitigated_invalid(self) -> float:
        return self.normalize(self.mitigated_invalid)

    def precision(self) -> float:
        if (self.tp() + self.fp()) == 0:
            return 1.
        return float(self.tp()) / float(self.tp() + self.fp())

    def recall(self) -> float:
        if (self.tp() + self.fn()) == 0:
            return 1.
        return float(self.tp()) / float(self.tp() + self.fn())

    def tpr(self):
        return self.recall()

    def tnr(self):
        if (self.fp() + self.tn()) == 0:
            return 1.
        return float(self.tn()) / float(self.fp() + self.tn())

    def balanced_accuracy(self) -> float:
        return (self.tpr() + self.tnr()) / 2.

    def normalize(self, value: int) -> float:
        return 100. * (float(value) / (float(self.total())))


class Experiment:
    def __init__(self):
        self.normal = PatchStrategy("normal")
        self.filtered = PatchStrategy("filtered")
