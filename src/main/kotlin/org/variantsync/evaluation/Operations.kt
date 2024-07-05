package org.variantsync.evaluation

import org.variantsync.evaluation.baseline.shell.AppliedPatchTracker
import org.variantsync.evaluation.baseline.shell.ShellExecutor
import java.nio.file.Path

abstract class Operations {
    abstract fun rejectsFile(): Path
    abstract fun rejectsFileFiltered(): Path
    abstract fun splitAndFilteredPatchFile(): Path
    abstract fun splitPatchFile(): Path
    abstract fun workDir(): Path
    abstract fun shell(): ShellExecutor
    abstract fun patchDir(): Path
    abstract fun appliedPatchTracker(): AppliedPatchTracker
    abstract fun filteredPatchFile(): Path
    abstract fun patchFile(): Path
    abstract fun sourceV0Path(name: String): Path
}