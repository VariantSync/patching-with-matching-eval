package org.variantsync.evaluation.execution

import java.nio.file.Path
import java.util.*
import java.util.concurrent.BlockingQueue
import java.util.concurrent.Callable
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import org.tinylog.kotlin.Logger
import org.variantsync.evaluation.analysis.TaskOutcome
import org.variantsync.evaluation.analysis.TaskResult
import org.variantsync.evaluation.util.diff.DiffParser
import org.variantsync.evaluation.util.diff.components.OriginalDiff
import org.variantsync.evaluation.util.shell.DiffCommand

class PatchComposition(val evalRun: EvaluationRun, val fileMap: Map<String, Int>)

class PatchCompositionTask(
        private val config: EvalConfig,
        private val datasetName: String,
        private val cherryPick: CherryPick,
        private val availableOperations: BlockingQueue<CompositionAnalysisOperations>,
        private val runID: ULong,
        val evalRun: EvaluationRun,
) : Callable<TaskOutcome> {

    override fun call(): TaskOutcome {
        val operations: CompositionAnalysisOperations

        synchronized(PatchCompositionTask::class.java) {
            // Retrieve the operations and the repo manager for this task
            Logger.debug("Getting the next available operations (" + availableOperations.size + ")")
            operations = availableOperations.take()
            Logger.debug(
                    "There are now " +
                            availableOperations.size +
                            " operations available. Took $operations"
            )
            Logger.debug("Remaining after take: " + opsToString())
        }

        var experimentResult = Optional.empty<List<TaskResult>>()
        try {
            experimentResult = Optional.of(callExecution(operations))
        } catch (e: Throwable) {
            Logger.error("Failed to finish task with runID $runID")
            Logger.error(e)
            e.printStackTrace()
        } finally {
            // Place the operations back in the queue to make them available to the next task
            Logger.debug(
                    "Placing operation $operations back in queue (" + availableOperations.size + ")"
            )
            Logger.debug("Remaining before place back: " + opsToString())
            availableOperations.add(operations)
            Logger.debug("There are now " + availableOperations.size + " operations available.")
        }

        return TaskOutcome(runID, experimentResult, evalRun)
    }

    private fun opsToString(): String {
        val sb = StringBuilder()
        for (op in availableOperations) {
            sb.append(op)
            sb.append(",")
        }
        return sb.toString()
    }

    fun callExecution(operations: CompositionAnalysisOperations): List<TaskResult> {
        try {
            // repoManager.cleanRepoStates()
            if (!operations.repoManager.prepareCherryPick(cherryPick)) {
                Logger.info(
                        "Not all commits of the cherry pick could be found... skipping cherry pick ${cherryPick.id} of $datasetName"
                )
                return ArrayList()
            }
        } catch (e: Exception) {
            Logger.debug("Was not able to checkout cherry pick commits in variant directories")
            Logger.debug(e)
            return ArrayList()
        }

        if (config.EXPERIMENT_DEBUG() && operations.debugDir(cherryPick).toFile().mkdirs()) {
            Logger.debug("Created Debug directory.")
        }

        // Apply diff to both versions of source variant
        Logger.debug("Diffing source...")
        val originalPatch =
                getOriginalDiff(operations, operations.sourceVariantV0, operations.sourceVariantV1)

        if (originalPatch.isEmpty) {
            // There was no change to this variant, so we can skip it as source
            Logger.info(
                    "Skipping cherry pick " +
                            cherryPick.id +
                            " because there are no changes to code. Diff of code files is empty."
            )
            return ArrayList()
        }

        Logger.debug("Saved original diff.")

        val results = ArrayList<TaskResult>()
        try {
            /* Application of patches without knowledge about features */
            Logger.debug("Analyzing patch composition of cherry-pick...")
            val fileMap = HashMap<String, Int>()
            for (fileDiff in originalPatch.fileDiffs) {
                val fileType = fileDiff.oldFile.fileName.toFile().extension
                fileMap[fileType] = fileMap.getOrDefault(fileType, 0) + 1
            }

            val resultFile = config.EXPERIMENT_DIR_RESULTS().resolve("${datasetName}.composition")
            results.add(TaskResult(PatchComposition(evalRun, fileMap), resultFile))

            Logger.debug(
                    "Finished analysis for cherry " +
                            cherryPick.cherryCommit +
                            " and target " +
                            cherryPick.targetCommit
            )
        } catch (e: Exception) {
            Logger.debug("Captured exception for cherry pick ${cherryPick.id}: ", e.message)
        }
        return results
    }

    // Get the difference between two directories using UNIX diff
    private fun getOriginalDiff(
            operations: CompositionAnalysisOperations,
            v0Path: Path,
            v1Path: Path
    ): OriginalDiff {
        return getOriginalDiff(operations, v0Path, v1Path, false)
    }

    // Get the difference between two directories using UNIX diff
    private fun getOriginalDiff(
            operations: CompositionAnalysisOperations,
            v0Path: Path,
            v1Path: Path,
            ignoreBlanks: Boolean
    ): OriginalDiff {
        val diffCommand: DiffCommand =
                DiffCommand.Recommended(
                                operations.workDir.relativize(v0Path),
                                operations.workDir.relativize(v1Path)
                        )
                        .exclude(".*")
        if (ignoreBlanks) {
            diffCommand.ignoreBlankLines()
        }
        val output = operations.shell.execute(diffCommand, operations.workDir)
        // .expect("Was not able to diff variants.")
        return if (output.isSuccess) {
            DiffParser.toOriginalDiff(output.success)
        } else {
            // Assume that the error lines still contain valid diffs, which is usually the case
            DiffParser.toOriginalDiff(output.failure.output)
        }
    }
}
