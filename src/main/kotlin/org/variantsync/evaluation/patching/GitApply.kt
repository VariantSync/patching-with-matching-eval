package org.variantsync.evaluation.patching

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Consumer
import org.apache.commons.io.FileUtils
import org.apache.commons.io.filefilter.IOFileFilter
import org.apache.commons.io.filefilter.TrueFileFilter
import org.tinylog.kotlin.Logger
import org.variantsync.evaluation.execution.EvalConfig
import org.variantsync.evaluation.execution.Operations
import org.variantsync.evaluation.execution.panic
import org.variantsync.evaluation.execution.readContentSafely
import org.variantsync.evaluation.util.diff.DiffParser
import org.variantsync.evaluation.util.diff.components.Hunk
import org.variantsync.evaluation.util.shell.GitApplyCommand
import org.variantsync.evaluation.util.shell.ShellExecutor
import org.variantsync.vevos.simulation.feature.Variant

class GitApply(private val config: EvalConfig, private val name: String, private val strip: Int) : Patcher {
    override fun applyPatch(
            operations: Operations,
            sourceVariant: Variant,
            targetVariant: Variant,
            withFiler: Boolean
    ): Rejects {
        val pathToPatchFile =
                if (withFiler) {
                    operations.filteredPatchFile()
                } else {
                    operations.patchFile()
                }

        if (!Files.exists(pathToPatchFile)) {
            // If there is nothing to patch, there is nothing to reject
            return Rejects(ArrayList())
        }

        val patchCommand = GitApplyCommand.Recommended(pathToPatchFile).strip(strip).reject()

        // apply patch to target variant
        val customShell = ShellExecutor(Logger::debug, Logger::debug, operations.workDir(),config.EXPERIMENT_TIMEOUT_LENGTH(), config.EXPERIMENT_TIMEOUT_UNIT())
        val result = customShell.execute(patchCommand, operations.patchDir())

        val rejects = Rejects(ArrayList())
        if (result.isSuccess) {
            result.success.forEach(Consumer { message: String? -> Logger.debug(message) })
        } else {
            Logger.debug("git apply failed")
            result.failure.output.forEach(Consumer { message: String? -> Logger.debug(message) })
        }

        return rejects
    }

    override fun name(): String {
        return this.name
    }

    private fun findRejects(operations: Operations): Collection<File> {
        // Define a file filter to select .rej files
        val rejectFilter: IOFileFilter =
                object : IOFileFilter {
                    override fun accept(file: File): Boolean {
                        return file.name.endsWith(".rej")
                    }

                    override fun accept(dir: File?, name: String): Boolean {
                        return name.endsWith(".rej")
                    }
                }
        // Search for reject files recursively
        val rejectFiles: Collection<File> =
                FileUtils.listFiles(
                        operations.patchDir().toFile(),
                        rejectFilter,
                        TrueFileFilter.INSTANCE // This filter accepts all directories for recursive
                        // search
                        )
        return rejectFiles
    }

    override fun clean(operations: Operations) {
        val rejectFiles = findRejects(operations)
        for (rejectFile in rejectFiles) {
            Logger.debug("Cleaning old rejects file $rejectFile")
            Files.delete(rejectFile.toPath())
        }
    }
}
