package org.variantsync.evaluation.patching

import org.tinylog.Logger
import org.variantsync.evaluation.Operations
import org.variantsync.evaluation.baseline.diff.DiffParser
import org.variantsync.evaluation.baseline.shell.GitCherryPickCommand
import org.variantsync.evaluation.baseline.shell.ShellExecutor
import org.variantsync.evaluation.cherries.CherryEvalOperations
import org.variantsync.vevos.simulation.feature.Variant
import java.nio.file.Files
import java.util.function.Consumer

// TODO: Find a valid evaluation method; Currently, we cannot really detect errors made by cherry pick
class GitCP(private val name: String, private val strip: Int) : Patcher {
    override fun applyPatch(
        operations: Operations,
        sourceVariant: Variant,
        targetVariant: Variant,
        withFiler: Boolean
    ): Rejects {
        val pathToPatchFile = if (withFiler) {
            operations.filteredPatchFile()
        } else {
            operations.patchFile()
        }
        val patch = DiffParser.toOriginalDiff(Files.readAllLines(pathToPatchFile))

        if (operations !is CherryEvalOperations) {
            // If this is not an evaluation of cherry picks, we cannot apply git cherry pick as patcher
            return Rejects(patch.intoChanges(strip))
        }
        val cherry = operations.repoManager.lastCherry!!.cherryCommit

        val patchCommand = GitCherryPickCommand.Recommended(cherry)


        // apply patch to target variant
        val customShell = ShellExecutor(Logger::debug, Logger::debug, operations.workDir())
        val result = customShell.execute(
            patchCommand,
            operations.patchDir()
        )
        val rejects = Rejects(ArrayList())
        if (result.isSuccess) {
            result.success.forEach(Consumer { message: String? -> org.tinylog.kotlin.Logger.debug(message) })
        } else {
            org.tinylog.kotlin.Logger.debug("git cherry-pick failed")
            result.failure.output.forEach(Consumer { message: String? -> org.tinylog.kotlin.Logger.warn(message) })
            if (result.toString().contains("You are currently cherry-picking")) {
                val continueCommand = GitCherryPickCommand().cont()
                val continueResult = customShell.execute(continueCommand, operations.patchDir())
                if (continueResult.isFailure) {
                    Logger.warn(continueResult)
                }
            }
        }
        return rejects
    }

    override fun name(): String {
        return name
    }

    override fun clean(operations: Operations) {
        super.clean(operations)
        // TODO("Proper cleaning")
    }
}