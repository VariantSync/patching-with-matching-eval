package org.variantsync.evaluation

import org.tinylog.kotlin.Logger
import org.variantsync.evaluation.baseline.shell.AppliedPatchTracker
import org.variantsync.evaluation.baseline.shell.ShellExecutor
import org.variantsync.vevos.simulation.util.io.CaseSensitivePath
import org.variantsync.vevos.simulation.variability.SPLCommit
import java.io.IOException
import java.io.UncheckedIOException
import java.nio.file.Files
import java.nio.file.Path

class Operations(mainDir: Path) {
    // Working directory
    @JvmField
    var workDir: Path

    // Debug directory
    private val debugBaseDir: Path

    // Path to the first copy of the SPL. We require copy to consider different versions
    val splCopyA: Path

    // Path to the second copy of the SPL
    val splCopyB: Path

    // Path to the directory containing the variants generated for the parent commit
    val variantsDirV0: CaseSensitivePath

    // Path to the directory containing the variants generated for the child commit
    val variantsDirV1: CaseSensitivePath

    // The directory to which patches are applied. A copy of the target variant is created in this
    // directory.
    val patchDir: Path

    // Path to the patch file containing the patches without filtering
    val patchFile: Path

    // Path to the patch file containing the patches with filtering
    val filteredPatchFile: Path

    // Path to the patch file containing the split patches without filtering
    val splitPatchFile: Path

    // Path to the patch file containing the split patches with filtering
    val splitAndFilteredPatchFile: Path

    // Path to the rejects file created by patching without filtering
    val rejectsFile: Path

    // Path to the rejects file created by patching with filtering
    val rejectsFileFiltered: Path

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
        debugBaseDir = workDir.resolve("DEBUG")
        splCopyA = workDir.resolve("SPL-A")
        splCopyB = workDir.resolve("SPL-B")
        variantsDirV0 = CaseSensitivePath(workDir.resolve("V0Variants"))
        variantsDirV1 = CaseSensitivePath(workDir.resolve("V1Variants"))
        patchDir = workDir.resolve("TARGET/V0")
        patchFile = workDir.resolve("patch.diff")
        filteredPatchFile = workDir.resolve("patch-filtered.diff")
        splitPatchFile = workDir.resolve("patch-split.diff")
        splitAndFilteredPatchFile = workDir.resolve("patch-split-filtered.diff")
        rejectsFile = workDir.resolve("rejects-normal.txt")
        rejectsFileFiltered = workDir.resolve("rejects-filtered.txt")
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

    fun debugDir(commit: SPLCommit): Path {
        return debugDir(commit.id())
    }
}
