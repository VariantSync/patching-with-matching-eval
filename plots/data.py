class PatchStrategy:
    def __init__(self, name):
        self.name = name

        self.tp = 0
        self.fp = 0
        self.tn = 0
        self.fn = 0

        self.wrongLocation = 0

        self.commitPatches = 0
        self.commitSuccess = 0

        self.file = 0
        self.fileSuccess = 0

        self.line = 0
        self.lineSuccess = 0

    def getNumCommitFailures(self):
        return self.commitPatches - self.commitSuccess

    def getNumFilePatchFailures(self):
        return self.file - self.fileSuccess

    def getNumLinePatchFailures(self):
        return self.line - self.lineSuccess

class Experiment:
    def __init__(self):
        self.normal = PatchStrategy("normal")
        self.filtered = PatchStrategy("filtered")
