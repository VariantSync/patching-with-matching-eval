package org.variantsync.evaluation.patching

import org.tinylog.kotlin.Logger
import org.variantsync.evaluation.execution.Operations
import org.variantsync.evaluation.util.diff.DiffParser
import org.variantsync.evaluation.util.shell.GitCherryPickCommand
import org.variantsync.evaluation.util.shell.ShellExecutor
import org.variantsync.evaluation.execution.EvalOperations
import org.variantsync.evaluation.error.ShellException
import org.variantsync.evaluation.execution.readContentSafely
import org.variantsync.vevos.simulation.feature.Variant
import java.nio.file.Files
import java.util.function.Consumer

class GitCP(private val name: String, private val strip: Int, private val strategy: MergeStrategy) : Patcher {
    private var lastResult: org.variantsync.functjonal.Result<List<String>, ShellException>? = null
    private val conflictDetectionText = "CONFLICT (content): Merge conflict in "

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
        val patch = DiffParser.toOriginalDiff(readContentSafely(pathToPatchFile))

        if (operations !is EvalOperations) {
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
        val conflictingFiles = ArrayList<String>()
        if (result.isSuccess) {
            result.success.forEach(Consumer { message: String? -> Logger.debug(message) })
        } else {
            Logger.debug("git cherry-pick failed")
            result.failure.output.forEach(Consumer { message: String? -> Logger.debug(message) })
            for (errorMessage in result.failure.output) {
                if (errorMessage.startsWith(conflictDetectionText)) {
                    conflictingFiles.add(errorMessage.substring(conflictDetectionText.length))
                }
            }
            lastResult = result
        }
        applyMergeStrategy(operations, cherry, conflictingFiles)
        return rejects
    }

    override fun name(): String {
        return name
    }

    override fun clean(operations: Operations) {
        super.clean(operations)
        val command = GitCherryPickCommand().abort()
        val customShell = ShellExecutor(Logger::debug, Logger::debug, operations.patchDir())
        val continueResult = customShell.execute(command, operations.patchDir())
        if (continueResult.isFailure && continueResult.failure.output.isNotEmpty()) {
            Logger.debug(continueResult)
        }
    }

    private fun applyMergeStrategy(operations: Operations,
                                   cherry: String,
                                   conflictingFiles: List<String>) {
        val headMarker = "<<<<<<< HEAD"
        val divideMarker = "======="
        val endMarker = ">>>>>>> " + cherry.substring(0, 8)

        if (this.strategy == MergeStrategy.Default) {
            // The default strategy is to keep the tentative merge
            return
        }

        // Get the patched files
        var state = TentativeState.Outside
        for (conflictingFile in conflictingFiles) {
            val pathToFile = operations.patchDir().resolve(conflictingFile)
            val lines = readContentSafely(pathToFile)

            val updatedLines = ArrayList<String>()
            for (line in lines) {
                if (state == TentativeState.Outside) {
                    // Outside
                    if (line.startsWith(headMarker)) {
                        Logger.debug("Found Head Marker")
                        state = TentativeState.Head
                    } else {
                        updatedLines.add(line)
                    }
                } else if (state == TentativeState.Head) {
                    // Inside ours
                    if (line.startsWith(divideMarker)) {
                        Logger.debug("Found Divide Marker")
                        state = TentativeState.Cherry
                    } else if (this.strategy == MergeStrategy.Ours){
                        updatedLines.add(line)
                    }
                } else {
                    // Inside theirs
                    if (line.startsWith(endMarker)) {
                        Logger.debug("Found End Marker")
                        state = TentativeState.Outside
                    } else if (this.strategy == MergeStrategy.Theirs){
                        updatedLines.add(line)
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

enum class MergeStrategy {
    Default,
    Ours,
    Theirs,
}