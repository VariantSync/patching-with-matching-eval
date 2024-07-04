from enum import Enum


class Patcher(Enum):
    MPatch = "mpatch"
    UnixPatch = "unix_patch"
    GitApply = "git_apply"

    def __str__(self):
        return self.value

    def __repr__(self):
        return f"{self.__class__.__name__}.{self.name}"


class PatchResult:
    def __init__(self, json_object):
        self.dataset = json_object.get("dataset")
        self.run_id = json_object.get("runID")
        self.cherry_id = json_object.get("cherry")
        self.target_id = json_object.get("target")
        self.num_actual_vs_expected = int(json_object.get(
            "normalActualVsExpected"))
        self.num_changes_total = int(json_object.get("lineNormal"))
        self.num_changes_applied = int(
            json_object.get("lineSuccessNormal"))
        self.outcome_classification = OutcomeClassification(
            json_object.get("normalResult"))
        self.patch_duration = float(json_object.get("patchDuration"))
        self.patch_is_trivial = bool(json_object.get("patchIsTrivial"))

    def __str__(self):
        return (
            f"PatchResult(dataset={self.dataset}, runID={
                self.run_id}, cherry={self.cherry_id}, "
            f"target={self.target_id}, normalActualVsExpected={
                self.num_actual_vs_expected}, "
            f"lineNormal={self.num_changes_total}, lineSuccessNormal={
                self.num_changes_applied}, "
            f"normalResult={self.outcome_classification}, patchDuration={
                self.patch_duration}, "
            f"patchIsTrivial={self.patch_is_trivial})"
        )

    def __repr__(self):
        return (
            f"PatchResult(dataset={repr(self.dataset)}, runID={
                repr(self.run_id)}, cherry={repr(self.cherry_id)}, "
            f"target={repr(self.target_id)}, normalActualVsExpected={
                repr(self.num_actual_vs_expected)}, "
            f"lineNormal={repr(self.num_changes_total)}, lineSuccessNormal={
                repr(self.num_changes_applied)}, "
            f"normalResult={repr(self.outcome_classification)}, patchDuration={
                repr(self.patch_duration)}, "
            f"patchIsTrivial={repr(self.patch_is_trivial)})"
        )


class OutcomeClassification:
    def __init__(self, json_object):
        self.applied_correctly = int(json_object.get("applied"))
        self.applied_invalid = int(json_object.get("invalid"))
        self.applied_wrong_location = int(json_object.get("wrongLocation"))
        self.missing = int(json_object.get("missing"))
        self.filtered_correctly = int(json_object.get("filteredCorrectly"))
        self.filtered_incorrectly = int(
            json_object.get("filteredIncorrectly"))
        self.mitigated_invalid = int(json_object.get("mitigatedInvalid"))
        self.mitigated_missing = int(json_object.get("mitigatedMissing"))
        self.edit_distance = int(json_object.get("editDistance"))
