from enum import Enum


class Patcher(Enum):
    MPatch = "mpatch"
    UnixPatch = "unix_patch"
    GitApply = "git_apply"

    def __str__(self):
        return self.value

    def __repr__(self):
        return f"{self.__class__.__name__}.{self.name}"
