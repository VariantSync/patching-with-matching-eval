package org.variantsync.evaluation.pareco

import org.tinylog.kotlin.Logger
import org.variantsync.evaluation.Operations
import org.variantsync.evaluation.patching.Patcher
import org.variantsync.evaluation.patching.UnixPatch
import org.variantsync.evaluation.baseline.shell.AppliedPatchTracker
import org.variantsync.evaluation.baseline.shell.ShellExecutor
import org.variantsync.evaluation.patching.MPatch
import java.io.IOException
import java.io.UncheckedIOException
import java.nio.file.Files
import java.nio.file.Path

class PaReCoOperations(mainDir: Path) : Operations() {
    // Working directory
    @JvmField
    var workDir: Path

    // Debug directory
    private val debugBaseDir: Path

    val sourceVariantV0: Path

    val sourceVariantV1: Path

    val targetVariantV0: Path

    val targetVariantV1: Path

    // Path to the patch file containing the patches without filtering
    val patchFile: Path

    // Path to the patch file containing the split patches without filtering
    val splitPatchFile: Path

    // Path to the rejects file created by patching without filtering
    val rejectsFile: Path

    // ShellExecutor for executing shell commands
    val shell: ShellExecutor

    val appliedPatchTracker: AppliedPatchTracker

    val patchers: MutableList<Patcher>

    init {
        try {
            if (mainDir.toFile().mkdirs()) {
                Logger.info("Created main directory $mainDir")
            }
            workDir = Files.createTempDirectory(mainDir, "workdir")
        } catch (e: IOException) {
            Logger.error("Was not able to initialize this.workDir", e)
            throw UncheckedIOException(e)
        }
        sourceVariantV0 = workDir.resolve("source-v0")
        sourceVariantV1 = workDir.resolve("source-v1")
        targetVariantV0 = workDir.resolve("target-v0")
        targetVariantV1 = workDir.resolve("target-v1")
        debugBaseDir = workDir.resolve("DEBUG")
        patchFile = workDir.resolve("patch.diff")
        splitPatchFile = workDir.resolve("patch-split.diff")
        rejectsFile = workDir.resolve("rejects-normal.txt")
        appliedPatchTracker = AppliedPatchTracker()
        shell =
            ShellExecutor(
                appliedPatchTracker,
                appliedPatchTracker,
                workDir
            )

        patchers = ArrayList()
        patchers.add(UnixPatch())
        patchers.add(MPatch())
    }

    fun debugDir(directory: String): Path {
        return debugBaseDir.resolve(directory)
    }

    fun debugDir(commit: PullRequest): Path {
        return debugDir(commit.sourceV1)
    }

    override fun rejectsFile(): Path {
        return rejectsFile
    }

    override fun rejectsFileFiltered(): Path {
        return rejectsFile
    }

    override fun splitAndFilteredPatchFile(): Path {
        return splitPatchFile
    }

    override fun splitPatchFile(): Path {
        return splitPatchFile
    }

    override fun workDir(): Path {
        return workDir
    }

    override fun shell(): ShellExecutor {
        return shell
    }

    override fun patchDir(): Path {
        return targetVariantV0
    }

    override fun appliedPatchTracker(): AppliedPatchTracker {
        return appliedPatchTracker
    }

    override fun filteredPatchFile(): Path {
        return patchFile
    }

    override fun patchFile(): Path {
        return patchFile
    }

    override fun sourceV0Path(name: String): Path {
        return sourceVariantV0
    }
}