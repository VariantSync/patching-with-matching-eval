package org.variantsync.evaluation.cherries

import org.tinylog.kotlin.Logger
import org.variantsync.evaluation.CherryPickResultAnalysis
import org.variantsync.evaluation.EvalConfig
import org.variantsync.evaluation.baseline.diff.DiffParser
import org.variantsync.evaluation.baseline.diff.components.FineDiff
import org.variantsync.evaluation.baseline.diff.components.OriginalDiff
import org.variantsync.evaluation.baseline.shell.CpCommand
import org.variantsync.evaluation.baseline.shell.DiffCommand
import org.variantsync.evaluation.baseline.shell.RmCommand
import org.variantsync.evaluation.patching.Patcher
import org.variantsync.evaluation.patching.Rejects
import org.variantsync.evaluation.saveResult
import org.variantsync.evaluation.syncstudy.getFineDiff
import org.variantsync.evaluation.syncstudy.panic
import org.variantsync.vevos.simulation.feature.Variant
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.concurrent.BlockingQueue
import java.util.concurrent.Callable


class CherryPickEvalTask(
    private val config: EvalConfig,
    private val datasetName: String,
    private val cherryPick: CherryPick,
    private val availableOperations: BlockingQueue<CherryEvalOperations>,
    private val repoManagers: Map<CherryEvalOperations, VariantRepoManager>,
    private val runID: ULong,
) : Callable<ULong> {

    override fun call(): ULong {
        val operations: CherryEvalOperations
        val repoManager: VariantRepoManager

        synchronized(CherryPickEvalTask::class.java) {
            // Retrieve the operations and the repo manager for this task
            Logger.debug("Getting the next available operations (" + availableOperations.size + ")")
            operations = availableOperations.take()
            Logger.debug("There are now " + availableOperations.size + " operations available. Took $operations")
            Logger.debug("Remaining after take: " + opsToString())
            repoManager = this.repoManagers[operations]!!
        }

        try {
            callExecution(repoManager, operations)
        } catch (e: Throwable) {
            Logger.error("Failed to finish task with runID $runID")
            Logger.error(e)
            e.printStackTrace()
        } finally {
            // Place the operations back in the queue to make them available to the next task
            Logger.debug("Placing operation $operations back in queue (" + availableOperations.size + ")")
            Logger.debug("Remaining before place back: " + opsToString())
            availableOperations.add(operations)
            Logger.debug("There are now " + availableOperations.size + " operations available.")
        }

        return runID
    }

    private fun opsToString(): String {
        val sb = StringBuilder()
        for (op in availableOperations) {
            sb.append(op)
            sb.append(",")
        }
        return sb.toString()
    }

    fun callExecution(repoManager: VariantRepoManager, operations: CherryEvalOperations) {
        try {
            // repoManager.cleanRepoStates()
            if (!repoManager.prepareCherryPick(cherryPick)) {
                Logger.info("Not all commits of the cherry pick could be found... skipping cherry pick ${cherryPick.id} of $datasetName")
                return
            }
        } catch (e: Exception) {
            Logger.error("Was not able to checkout cherry pick commits in variant directories")
            Logger.error(e)
            return
        }

        if (config.EXPERIMENT_DEBUG() && operations.debugDir(cherryPick).toFile().mkdirs()) {
            Logger.debug("Created Debug directory.")
        }

        val source = Variant("source") { true /* we have no knowledge about features, so everything is true */ }
        val target = Variant("target") { true /* we have no knowledge about features, so everything is true */ }

        if (Files.exists(operations.splitPatchFile)) {
            Logger.debug("Cleaning old patch file " + operations.splitPatchFile)
            operations.shell.execute(RmCommand(operations.splitPatchFile))
        }

        // Apply diff to both versions of source variant
        Logger.debug("Diffing source...")
        val originalPatch = getOriginalDiff(
            operations,
            operations.sourceVariantV0,
            operations.sourceVariantV1
        )

        if (originalPatch.isEmpty) {
            // There was no change to this variant, so we can skip it as source
            Logger.info(
                "Skipping cherry pick " + cherryPick.id + " because there are no changes to code. Diff of code files is empty."
            )
            return
        }

        if (config.EXPERIMENT_DEBUG()) {
            saveDiff(
                originalPatch,
                operations.debugDir(cherryPick).resolve("original.diff")
            )
        }

        saveDiff(originalPatch, operations.patchFile)
        Logger.debug("Saved original diff.")

        try {
            // Convert the original diff into a fine diff
            Logger.debug("Converting diff...")
            val splitPatch = getFineDiff(operations.workDir, originalPatch)
            saveDiff(splitPatch, operations.splitPatchFile)
            Logger.debug("Saved fine diff.")

            Logger.debug("Starting patch application for cherry-pick " + cherryPick.id)
            val originalEvolutionDiff =
                getOriginalDiff(operations, operations.targetVariantV0, operations.targetVariantV1)

            val patchIsTrivial = originalPatch.partiallyEquals(originalEvolutionDiff, operations.strip)
            if (patchIsTrivial) {
                // We only focus on variability, which is expressed by differences in the patch and evolution
                Logger.debug("Patch is trivial")
            } else {
                Logger.debug("Patch is not trivial")
            }

            val evolutionDiff = getFineDiff(
                operations.workDir,
                originalEvolutionDiff
            )

            for (patcher in operations.patchers) {
                /* Application of patches without knowledge about features */
                Logger.debug("Applying patch from cherry-pick...")
                val start = Instant.now()
                val rejectsNormal = patcher.applyPatch(operations, source, target, false)
                val end = Instant.now()
                val patchDuration = Duration.between(start, end)

                // Gather the patch result
                val actualVsExpectedNormal =
                    getActualVsExpected(operations, operations.targetVariantV1, target, cherryPick)

                patcher.clean(operations)

                if (config.EXPERIMENT_DEBUG()) {
                    patchFilesDebug(
                        operations,
                        patcher,
                        originalPatch,
                        splitPatch,
                        cherryPick,
                        source,
                        target,
                        rejectsNormal,
                        evolutionDiff
                    )
                }

                /* Result Evaluation */
                val patchOutcome = CherryPickResultAnalysis.processCherriesOutcome(
                    operations,
                    cherryPick,
                    datasetName,
                    runID,
                    splitPatch,
                    actualVsExpectedNormal,
                    rejectsNormal,
                    evolutionDiff,
                    patchDuration,
                    patchIsTrivial,
                )

                val resultFile = config.EXPERIMENT_DIR_RESULTS().resolve("${datasetName}_${patcher.name()}.results")
                saveResult(patchOutcome, cherryPick, resultFile, runID)
                repoManager.resetTargetVariant()
            }
        } catch (e: Exception) {
            Logger.debug("Captured exception for cherry pick ${cherryPick.id}: ", e.message)
        }
    }


    /**
     * Get the difference between the target variant after patching and the target variant in the
     * next de.variantsync.studies.evolution step. Then, filter all differences that do not belong
     * to the source variant and could have therefore not been synchronized in any case.
     */
    private fun getActualVsExpected(
        operations: CherryEvalOperations,
        pathToExpectedResult: Path,
        target: Variant,
        currentPR: CherryPick
    ): FineDiff {
        val resultDiff = getOriginalDiff(operations, operations.patchDir(), pathToExpectedResult, true)
        if (config.EXPERIMENT_DEBUG()) {
            try {
                Files.write(
                    operations.debugDir(currentPR).resolve(target.name)
                        .resolve(target.name + "_actual_expected.diff"),
                    resultDiff.toLines()
                )
            } catch (e: IOException) {
                Logger.error("Was not able to save resultDiffOriginal:\n{}", e)
            }
        }
        return getFineDiff(operations.workDir, resultDiff)
    }

    // Save the difference as a patch file
    private fun saveDiff(fineDiff: FineDiff, file: Path) {
        // Save the fine diff to a file
        try {
            Files.write(file, fineDiff.toLines())
        } catch (e: IOException) {
            panic("Was not able to save diff to file $file")
        }
    }

    // Save the difference as a patch file
    private fun saveRejects(rejects: Rejects, file: Path) {
        // Save the fine diff to a file
        try {
            Files.write(file, rejects.toLines())
        } catch (e: IOException) {
            panic("Was not able to save diff to file $file")
        }
    }

    // Save the difference as a patch file
    private fun saveDiff(fineDiff: OriginalDiff, file: Path) {
        // Save the fine diff to a file
        try {
            Files.write(file, fineDiff.toLines())
        } catch (e: IOException) {
            panic("Was not able to save diff to file $file")
        }
    }

    // Get the difference between two directories using UNIX diff
    private fun getOriginalDiff(
        operations: CherryEvalOperations,
        v0Path: Path, v1Path: Path
    ): OriginalDiff {
        return getOriginalDiff(operations, v0Path, v1Path, false)
    }

    // Get the difference between two directories using UNIX diff
    private fun getOriginalDiff(
        operations: CherryEvalOperations,
        v0Path: Path, v1Path: Path, ignoreBlanks: Boolean
    ): OriginalDiff {
        val diffCommand: DiffCommand = DiffCommand.Recommended(
            operations.workDir.relativize(v0Path),
            operations.workDir.relativize(v1Path)
        ).exclude(".*")
        if (ignoreBlanks) {
            diffCommand.ignoreBlankLines()
        }
        val output = operations.shell.execute(diffCommand, operations.workDir)
        //.expect("Was not able to diff variants.")
        return if (output.isSuccess) {
            DiffParser.toOriginalDiff(output.success)
        } else {
            // Assume that the error lines still contain valid diffs, which is usually the case
            DiffParser.toOriginalDiff(output.failure.output)
        }
    }

    private fun patchFilesDebug(
        operations: CherryEvalOperations,
        patcher: Patcher,
        originalPatch: OriginalDiff,
        splitPatch: FineDiff,
        currentPR: CherryPick,
        source: Variant,
        target: Variant,
        rejectsNormal: Rejects,
        evolutionDiff: FineDiff
    ) {
        saveDiff(
            splitPatch,
            operations.debugDir(currentPR).resolve(source.name + "_split.diff")
        )
        saveDiff(
            originalPatch,
            operations.debugDir(currentPR).resolve(source.name + ".diff")
        )
        saveRejects(
            rejectsNormal,
            operations.debugDir(currentPR).resolve(target.name)
                .resolve(target.name + "_rejects_normal_${patcher.name()}.diff")
        )
        operations.debugDir(currentPR).resolve(target.name).toFile().mkdirs()
        saveDiff(
            evolutionDiff,
            operations.debugDir(currentPR).resolve(target.name)
                .resolve(target.name + "_evolution.diff")
        )
        operations.shell.execute(
            CpCommand(
                operations.patchDir(),
                operations.debugDir(currentPR).resolve(target.name).resolve("patched_filtered")
            ).recursive()
        )
            .expect("Was not able to copy variant $target.name")
    }

}
