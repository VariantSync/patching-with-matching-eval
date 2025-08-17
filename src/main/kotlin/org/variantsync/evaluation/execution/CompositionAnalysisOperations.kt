package org.variantsync.evaluation.execution

import java.io.IOException
import java.io.UncheckedIOException
import java.nio.file.Files
import java.nio.file.Path
import org.tinylog.kotlin.Logger
import org.variantsync.evaluation.patching.Patcher
import org.variantsync.evaluation.util.shell.AppliedPatchTracker
import org.variantsync.evaluation.util.shell.ShellExecutor

class CompositionAnalysisOperations(mainDir: Path, gitHubRepoPath: Path) : Operations() {
    // Working directory
    @JvmField var workDir: Path

    // Debug directory
    private val debugBaseDir: Path

    val sourceVariantV0: Path

    val sourceVariantV1: Path

    // Path to the patch file containing the patches without filtering
    val patchFile: Path

    // ShellExecutor for executing shell commands
    val shell: ShellExecutor

    val appliedPatchTracker: AppliedPatchTracker

    val patchers: List<Patcher>

    val repoManager: SourceRepoManager

    val STRIP = 1

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
        debugBaseDir = workDir.resolve("DEBUG")
        patchFile = workDir.resolve("patch.diff")
        appliedPatchTracker = AppliedPatchTracker()
        shell = ShellExecutor({ _ -> }, Logger::error, workDir)

        patchers = defaultPatchers(STRIP)
        repoManager = SourceRepoManager(sourceVariantV0, sourceVariantV1, gitHubRepoPath)
    }

    fun debugDir(directory: String): Path {
        return debugBaseDir.resolve(directory)
    }

    fun debugDir(commit: CherryPick): Path {
        return debugDir(commit.cherryCommit)
    }

    override fun rejectsFile(): Path {
        TODO("Not yet implemented")
    }

    override fun rejectsFileFiltered(): Path {
        TODO("Not yet implemented")
    }

    override fun splitAndFilteredPatchFile(): Path {
        TODO("Not yet implemented")
    }

    override fun splitPatchFile(): Path {
        TODO("Not yet implemented")
    }

    override fun workDir(): Path {
        return workDir
    }

    override fun shell(): ShellExecutor {
        return shell
    }

    override fun patchDir(): Path {
        TODO("Not yet implemented")
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

    fun strip(): Int {
        return STRIP
    }
}

