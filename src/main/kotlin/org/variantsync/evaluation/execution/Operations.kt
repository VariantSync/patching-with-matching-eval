package org.variantsync.evaluation.execution

import java.nio.file.Path
import org.variantsync.evaluation.util.shell.AppliedPatchTracker
import org.variantsync.evaluation.util.shell.ShellExecutor

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

