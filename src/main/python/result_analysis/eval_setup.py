from enum import Enum
import numpy as np


class Patcher(Enum):
    MPatch = "pwm_f2"
    UnixPatch = "unix_patch"
    GitApply = "git_apply"
    GitCherry = "git_cherry"

    def __str__(self):
        return self.value

    def __repr__(self):
        return f"{self.__class__.__name__}.{self.name}"

    def nice_name(self):
        return {
            Patcher.MPatch: "\\approach{}",
            Patcher.UnixPatch: "\\patch{}",
            Patcher.GitApply: "\\gitapply{}",
            Patcher.GitCherry: "\\gitcherrypickshort{}",
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
        self.pick_id = json_object.get("pick")
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
            f"PatchResult(dataset={self.dataset}, runID={self.run_id}, cherry={self.cherry_id}, "
            f"target={self.pick_id}, normalActualVsExpected={self.num_actual_vs_expected}, "
            f"lineNormal={self.num_changes_total}, lineSuccessNormal={self.num_changes_applied}, "
            f"normalResult={self.outcome_classification}, patchDuration={self.patch_duration}, "
            f"patchIsTrivial={self.patch_is_trivial})"
        )

    def __repr__(self):
        return (
            f"PatchResult(dataset={repr(self.dataset)}, runID={repr(self.run_id)}, cherry={repr(self.cherry_id)}, "
            f"target={repr(self.pick_id)}, normalActualVsExpected={repr(self.num_actual_vs_expected)}, "
            f"lineNormal={repr(self.num_changes_total)}, lineSuccessNormal={repr(self.num_changes_applied)}, "
            f"normalResult={repr(self.outcome_classification)}, patchDuration={repr(self.patch_duration)}, "
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

    def num_positive(self) -> int:
        return self.applied_correctly + self.missing + self.applied_wrong_location

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


class Metric(Enum):
    F1Score = "f1_score"
    Automation = "patch_automation"
    EditDistance = "avg_edit_distance"
    Runtime = "avg_runtime"

    def __str__(self):
        return self.value

    def __repr__(self):
        return f"{self.__class__.__name__}.{self.name}"

    def nice_name(self):
        return {
            Metric.F1Score: "F1 Score",
            Metric.Automation: "Autom. (\\%)",
            Metric.EditDistance: "Req. Fixes",
            Metric.Runtime: "Time (s)",
        }[self]


class RQ3PatcherData:
    def __init__(
        self,
        patcher: Patcher,
        precision: float,
        recall: float,
        f1_score: float,
        patch_automation: float,
        avg_edit_distance: float,
        avg_runtime: float,
    ):
        self.precision = np.array([precision])
        self.recall = np.array([recall])
        self.f1_score = np.array([f1_score])
        self.patcher = patcher
        self.patch_automation = np.array([patch_automation])
        self.avg_edit_distance = np.array([avg_edit_distance])
        self.avg_runtime = np.array([avg_runtime])

    def add_data(
        self,
        precision,
        recall,
        f1_score,
        patch_automation,
        avg_edit_distance,
        avg_runtime,
    ):
        self.precision = np.append(self.precision, precision)
        self.recall = np.append(self.recall, recall)
        self.f1_score = np.append(self.f1_score, f1_score)
        self.patch_automation = np.append(self.patch_automation, patch_automation)
        self.avg_edit_distance = np.append(self.avg_edit_distance, avg_edit_distance)
        self.avg_runtime = np.append(self.avg_runtime, avg_runtime)

    def __str__(self):
        return (
            f"Patcher: {self.patcher:<12} "
            f"Precision: {np.mean(self.precision):1.2f}, "
            f"Recall: {np.mean(self.recall):1.2f}, "
            f"Patch Automation: {100 * np.mean(self.patch_automation):2.2f}%, "
            f"Avg Edit Distance: {np.mean(self.avg_edit_distance):2.2f}, "
            f"Avg Runtime: {np.mean(self.avg_runtime):1.2f}s"
        )

    def get(self, metric: Metric) -> float:
        return getattr(self, metric.value)


class AccumulatedPatcherData:
    def __init__(self, accumulated: RQ3PatcherData, per_patch: RQ3PatcherData):
        self.accumulated = accumulated
        self.per_patch = per_patch
