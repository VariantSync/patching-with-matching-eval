from enum import Enum


class Patcher(Enum):
    MPatch = "mpatch"
    UnixPatch = "unix_patch"
    GitApply = "git_apply"
    GitCherry = "git_cherry"

    def __str__(self):
        return self.value

    def __repr__(self):
        return f"{self.__class__.__name__}.{self.name}"

    def nice_name(self):
        return {
            Patcher.MPatch: "PwM",
            Patcher.UnixPatch: "Unix Patch",
            Patcher.GitApply: "Git Apply",
            Patcher.GitCherry: "Git Cherry-Pick",
        }[self]


class Repository:
    def __init__(self, id: int, name: str, language: str):
        self.id = id
        self.name = name
        self.language = language

    def __str__(self):
        return (
            f"Repository(id={self.id}, name='{self.name}', language='{self.language}')"
        )


class PatchResult:
    def __init__(self, json_object):
        self.dataset = json_object.get("dataset").rsplit(".", 1)[0]
        self.run_id = json_object.get("runID")
        self.cherry_id = json_object.get("cherry")
        self.target_id = json_object.get("target")
        self.num_actual_vs_expected = int(json_object.get("normalActualVsExpected"))
        self.num_changes_total = int(json_object.get("lineNormal"))
        self.num_changes_applied = int(json_object.get("lineSuccessNormal"))
        self.outcome_classification = OutcomeClassification(
            json_object.get("normalResult")
        )
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
    def initialize_empty(self):
        self.applied_correctly = 0
        self.applied_invalid = 0
        self.applied_wrong_location = 0
        self.missing = 0
        self.filtered_correctly = 0
        self.filtered_incorrectly = 0
        self.mitigated_invalid = 0
        self.mitigated_missing = 0
        self.edit_distance = 0
        self.fully_correct = 0
        self.num_results = 0

    def __init__(self, json_object=None):
        if json_object is None:
            self.initialize_empty()
            return

        self.applied_correctly = int(json_object.get("applied"))
        self.applied_invalid = int(json_object.get("invalid"))
        self.applied_wrong_location = int(json_object.get("wrongLocation"))
        self.missing = int(json_object.get("missing"))
        self.filtered_correctly = int(json_object.get("filteredCorrectly"))
        self.filtered_incorrectly = int(json_object.get("filteredIncorrectly"))
        self.mitigated_invalid = int(json_object.get("mitigatedInvalid"))
        self.mitigated_missing = int(json_object.get("mitigatedMissing"))
        self.edit_distance = int(json_object.get("editDistance"))
        self.num_results = 1
        if self.num_incorrect() == 0:
            self.fully_correct = 1
        else:
            self.fully_correct = 0

    def tp(self) -> int:
        return self.applied_correctly + self.mitigated_missing

    def fp(self) -> int:
        return self.applied_invalid + self.applied_wrong_location

    def tn(self) -> int:
        return self.filtered_correctly + self.mitigated_invalid

    def fn(self) -> int:
        return self.missing + self.applied_wrong_location + self.filtered_incorrectly

    def num_correct(self) -> int:
        return self.tp() + self.tn()

    def num_incorrect(self) -> int:
        return self.fp() + self.fn()

    def add_result(self, other):
        self.applied_correctly += other.applied_correctly
        self.applied_invalid += other.applied_invalid
        self.applied_wrong_location += other.applied_wrong_location
        self.missing += other.missing
        self.filtered_correctly += other.filtered_correctly
        self.filtered_incorrectly += other.filtered_incorrectly
        self.mitigated_invalid += other.mitigated_invalid
        self.mitigated_missing += other.mitigated_missing
        self.edit_distance += other.edit_distance
        self.fully_correct += other.fully_correct
        self.num_results += other.num_results

    def __str__(self):
        return (
            f"applied_correctly \t= {self.applied_correctly},\n"
            f"applied_invalid \t= {self.applied_invalid},\n"
            f"applied_wrong_location \t= {self.applied_wrong_location},\n"
            f"missing \t\t= {self.missing},\n"
            f"filtered_correctly \t= {self.filtered_correctly},\n"
            f"filtered_incorrectly \t= {self.filtered_incorrectly},\n"
            f"mitigated_invalid \t= {self.mitigated_invalid},\n"
            f"mitigated_missing \t= {self.mitigated_missing},\n"
            f"edit_distance \t\t= {self.edit_distance}"
        )

    def __repr__(self):
        return (
            f"OutcomeClassification("
            f"applied_correctly={self.applied_correctly!r}, "
            f"applied_invalid={self.applied_invalid!r}, "
            f"applied_wrong_location={self.applied_wrong_location!r}, "
            f"missing={self.missing!r}, "
            f"filtered_correctly={self.filtered_correctly!r}, "
            f"filtered_incorrectly={self.filtered_incorrectly!r}, "
            f"mitigated_invalid={self.mitigated_invalid!r}, "
            f"mitigated_missing={self.mitigated_missing!r}, "
            f"edit_distance={self.edit_distance!r})"
        )
