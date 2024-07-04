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
        self.runID = json_object.get("runID")
        self.cherry = json_object.get("cherry")
        self.target = json_object.get("target")
        self.normalActualVsExpected = json_object.get("normalActualVsExpected")
        self.lineNormal = json_object.get("lineNormal")
        self.lineSuccessNormal = json_object.get("lineSuccessNormal")
        self.normalResult = json_object.get("normalResult")
        self.patchDuration = json_object.get("patchDuration")
        self.patchIsTrivial = json_object.get("patchIsTrivial")

    def __str__(self):
        return (
            f"PatchResult(dataset={self.dataset}, runID={
                self.runID}, cherry={self.cherry}, "
            f"target={self.target}, normalActualVsExpected={
                self.normalActualVsExpected}, "
            f"lineNormal={self.lineNormal}, lineSuccessNormal={
                self.lineSuccessNormal}, "
            f"normalResult={self.normalResult}, patchDuration={
                self.patchDuration}, "
            f"patchIsTrivial={self.patchIsTrivial})"
        )

    def __repr__(self):
        return (
            f"PatchResult(dataset={repr(self.dataset)}, runID={
                repr(self.runID)}, cherry={repr(self.cherry)}, "
            f"target={repr(self.target)}, normalActualVsExpected={
                repr(self.normalActualVsExpected)}, "
            f"lineNormal={repr(self.lineNormal)}, lineSuccessNormal={
                repr(self.lineSuccessNormal)}, "
            f"normalResult={repr(self.normalResult)}, patchDuration={
                repr(self.patchDuration)}, "
            f"patchIsTrivial={repr(self.patchIsTrivial)})"
        )
