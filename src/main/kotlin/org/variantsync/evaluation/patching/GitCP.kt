package org.variantsync.evaluation.patching

import org.tinylog.kotlin.Logger
import org.variantsync.evaluation.Operations
import org.variantsync.evaluation.baseline.diff.DiffParser
import org.variantsync.evaluation.baseline.diff.components.OriginalDiff
import org.variantsync.evaluation.baseline.shell.GitCherryPickCommand
import org.variantsync.evaluation.baseline.shell.ShellExecutor
import org.variantsync.evaluation.cherries.CherryEvalOperations
import org.variantsync.evaluation.error.ShellException
import org.variantsync.vevos.simulation.feature.Variant
import java.nio.file.Files
import java.util.function.Consumer

class GitCP(private val name: String, private val strip: Int) : Patcher {
    private var lastResult: org.variantsync.functjonal.Result<List<String>, ShellException>? = null
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
            Logger.debug("git cherry-pick failed")
            result.failure.output.forEach(Consumer { message: String? -> org.tinylog.kotlin.Logger.warn(message) })
            lastResult = result
        }
        applyOursMerge(operations, cherry, patch)
        return rejects
    }

    override fun name(): String {
        return name
    }

    override fun clean(operations: Operations) {
        super.clean(operations)
        if (lastResult.toString().contains("You are currently cherry-picking")) {
            val continueCommand = GitCherryPickCommand().cont()
            val customShell = ShellExecutor(Logger::debug, Logger::debug, operations.workDir())
            val continueResult = customShell.execute(continueCommand, operations.patchDir())
            if (continueResult.isFailure) {
                Logger.warn(continueResult)
            }
        }
    }

    private fun applyOursMerge(operations: Operations,
                               cherry: String,
                               patch: OriginalDiff) {
        val headMarker = "<<<<<<< HEAD"
        val divideMarker = "======="
        val endMarker = ">>>>>>> " + cherry.substring(0, 8)

        // Get the patched files
        var state = TentativeState.Outside
        for (fd in patch.fileDiffs) {
            var pathToFile = fd.oldFile.subpath(strip, fd.oldFile.nameCount)
            pathToFile = operations.patchDir().resolve(pathToFile)
            val lines = Files.readAllLines(pathToFile)

            val updatedLines = ArrayList<String>()
            for (line in lines) {
                if (state == TentativeState.Outside) {
                    if (line.startsWith(headMarker)) {
                        Logger.info("Found Head Marker")
                        state = TentativeState.Head
                    } else {
                        updatedLines.add(line)
                    }
                } else if (state == TentativeState.Head) {
                    if (line.startsWith(divideMarker)) {
                        Logger.info("Found Divide Marker")
                        state = TentativeState.Cherry
                    } else {
                        updatedLines.add(line)
                    }
                } else {
                    if (line.startsWith(endMarker)) {
                        Logger.info("Found End Marker")
                        state = TentativeState.Outside
                    }
                }
            }
            Files.write(pathToFile, updatedLines)
        }
    }

    enum class TentativeState {
        Head,
        Cherry,
        Outside,
    }
}