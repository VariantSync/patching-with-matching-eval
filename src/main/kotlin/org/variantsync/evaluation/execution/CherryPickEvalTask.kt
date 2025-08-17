package org.variantsync.evaluation.execution

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.*
import kotlin.collections.ArrayList
import org.prop4j.Node
import org.tinylog.kotlin.Logger
import org.variantsync.evaluation.analysis.ResultAnalysis
import org.variantsync.evaluation.analysis.TaskOutcome
import org.variantsync.evaluation.analysis.TaskResult
import org.variantsync.evaluation.error.Panic
import org.variantsync.evaluation.patching.Patcher
import org.variantsync.evaluation.patching.Rejects
import org.variantsync.evaluation.patching.UTF8Exception
import org.variantsync.evaluation.util.diff.components.OriginalDiff
import org.variantsync.evaluation.util.shell.CpCommand
import org.variantsync.evaluation.util.shell.RmCommand
import org.variantsync.vevos.simulation.feature.Variant
import org.variantsync.vevos.simulation.feature.config.IConfiguration

class CherryPickEvalTask(
        private val repetition: Int,
        private val config: EvalConfig,
        private val datasetName: String,
        private val cherryPick: CherryPick,
        private val evalSetup: EvalOperations,
        private val runID: ULong,
        val evalRun: EvaluationRun,
) {

    fun execute(): TaskOutcome {
        var experimentResult = Optional.empty<List<TaskResult>>()
        try {
            experimentResult = Optional.of(callExecution())
        } catch (e: Throwable) {
            Logger.error("Failed to finish task with runID $runID")
            Logger.error(e)
            e.printStackTrace()
        }
        return TaskOutcome(runID, experimentResult, evalRun)
    }

    private fun callExecution(): List<TaskResult> {
        try {
            if (!evalSetup.repoManager.prepareCherryPick(cherryPick)) {
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

        if (config.EXPERIMENT_DEBUG() && evalSetup.debugDir(cherryPick).toFile().mkdirs()) {
            Logger.debug("Created Debug directory.")
        }

        /* we have no knowledge about features, so the configuration agrees with everything*/
        val source = Variant("source", AllTrueConfiguration())
        val target = Variant("target", AllTrueConfiguration())

        if (Files.exists(evalSetup.splitPatchFile)) {
            Logger.debug("Cleaning old patch file " + evalSetup.splitPatchFile)
            evalSetup.shell.execute(RmCommand(evalSetup.splitPatchFile))
        }

        // Apply diff to both versions of source variant
        Logger.debug("Diffing source...")
        val originalPatch =
                getOriginalDiff(evalSetup, evalSetup.sourceVariantV0, evalSetup.sourceVariantV1)

        if (originalPatch.isEmpty) {
            // There was no change to this variant, so we can skip it as source
            Logger.info(
                    "Skipping cherry pick " +
                            cherryPick.id +
                            " because there are no changes to code. Diff of code files is empty."
            )
            return ArrayList()
        }

        if (config.EXPERIMENT_DEBUG()) {
            saveDiff(originalPatch, evalSetup.debugDir(cherryPick).resolve("original.diff"))
        }

        saveDiff(originalPatch, evalSetup.patchFile)
        Logger.debug("Saved original diff.")

        val results = ArrayList<TaskResult>()
        try {
            Logger.debug("Starting patch application for cherry-pick " + cherryPick.id)
            var evolutionDiff =
                    getOriginalDiff(evalSetup, evalSetup.targetVariantV0, evalSetup.targetVariantV1)

            val patchIsTrivial = originalPatch.partiallyEquals(evolutionDiff, evalSetup.STRIP)
            if (patchIsTrivial) {
                // We only focus on variability, which is expressed by differences in the patch and
                // evolution
                Logger.debug("Patch is trivial")
            } else {
                Logger.debug("Patch is not trivial")
            }

            evolutionDiff = filterUnpatchedFiles(originalPatch, evolutionDiff, evalSetup.STRIP)

            for (patcher in evalSetup.patchers) {
                /* Application of patches without knowledge about features */
                Logger.debug("Applying patch from cherry-pick...")
                val start = Instant.now()
                var rejectsNormal: Rejects
                try {
                    rejectsNormal = patcher.applyPatch(evalSetup, source, target, false)
                } catch (e: UTF8Exception) {
                    Logger.debug(e)
                    patcher.clean(evalSetup)
                    evalSetup.repoManager.resetTargetVariant()
                    return ArrayList()
                } catch (e: Exception) {
                    Logger.debug(e)
                    rejectsNormal = Rejects(ArrayList())
                }
                val end = Instant.now()
                val patchDuration = Duration.between(start, end)

                // Gather the patch result
                var actualVsExpectedNormal =
                        getActualVsExpected(
                                evalSetup,
                                evalSetup.targetVariantV1,
                                target,
                                cherryPick
                        )
                actualVsExpectedNormal =
                        filterUnpatchedFiles(originalPatch, actualVsExpectedNormal, evalSetup.STRIP)

                if (config.EXPERIMENT_DEBUG()) {
                    patchFilesDebug(
                            evalSetup,
                            patcher,
                            originalPatch,
                            cherryPick,
                            source,
                            target,
                            rejectsNormal,
                            evolutionDiff
                    )
                }

                /* Result Evaluation */
                val patchOutcome =
                        ResultAnalysis.processCherriesOutcome(
                                evalSetup,
                                cherryPick,
                                datasetName,
                                runID,
                                originalPatch,
                                actualVsExpectedNormal,
                                rejectsNormal,
                                evolutionDiff,
                                patchDuration,
                                patchIsTrivial,
                        )

                val resultFile =
                        config.EXPERIMENT_DIR_RESULTS()
                                .resolve("rep-${repetition}")
                                .resolve("${datasetName}_${patcher.name()}.results")
                results.add(TaskResult(patchOutcome, resultFile))

                Logger.debug(
                        "Finished patching for cherry " +
                                cherryPick.cherryCommit +
                                " and target " +
                                cherryPick.targetCommit
                )

                patcher.clean(evalSetup)
                evalSetup.repoManager.resetTargetVariant()
            }
        } catch (e: Exception) {
            Logger.debug("Captured exception for cherry pick ${cherryPick.id}: ", e.message)
        }
        return results
    }

    /**
     * Get the difference between the target variant after patching and the target variant in the
     * next de.variantsync.studies.evolution step. Then, filter all differences that do not belong
     * to the source variant and could have therefore not been synchronized in any case.
     */
    private fun getActualVsExpected(
            operations: EvalOperations,
            pathToExpectedResult: Path,
            target: Variant,
            currentPR: CherryPick
    ): OriginalDiff {
        val resultDiff =
                getOriginalDiff(operations, operations.patchDir(), pathToExpectedResult, true)
        if (config.EXPERIMENT_DEBUG() && !resultDiff.isEmpty) {
            try {
                saveDiff(
                        resultDiff,
                        operations
                                .debugDir(currentPR)
                                .resolve(target.name)
                                .resolve(target.name + "_actual_expected.diff")
                )
            } catch (e: IOException) {
                Logger.error("Was not able to save resultDiffOriginal:\n{}", e)
            }
        }
        return resultDiff
    }

    // Save the difference as a patch file
    private fun saveRejects(rejects: Rejects, file: Path) {
        // Save the fine diff to a file
        try {
            Files.createDirectories(file.parent)
            Files.write(file, rejects.toLines())
        } catch (_: IOException) {
            panic("Was not able to save diff to file $file")
        }
    }

    // Save the difference as a patch file
    private fun saveDiff(fineDiff: OriginalDiff, file: Path) {
        // Save the fine diff to a file
        try {
            Files.createDirectories(file.parent)
            Files.write(file, fineDiff.toLines())
        } catch (_: IOException) {
            panic("Was not able to save diff to file $file")
        }
    }

    private fun patchFilesDebug(
            operations: EvalOperations,
            patcher: Patcher,
            originalPatch: OriginalDiff,
            currentPR: CherryPick,
            source: Variant,
            target: Variant,
            rejectsNormal: Rejects,
            evolutionDiff: OriginalDiff
    ) {
        saveDiff(originalPatch, operations.debugDir(currentPR).resolve(source.name + ".diff"))
        saveRejects(
                rejectsNormal,
                operations
                        .debugDir(currentPR)
                        .resolve(target.name)
                        .resolve(target.name + "_rejects_normal_${patcher.name()}.diff")
        )
        operations.debugDir(currentPR).resolve(target.name).toFile().mkdirs()
        saveDiff(
                evolutionDiff,
                operations
                        .debugDir(currentPR)
                        .resolve(target.name)
                        .resolve(target.name + "_evolution.diff")
        )
        operations
                .shell
                .execute(
                        CpCommand(
                                        operations.patchDir(),
                                        operations
                                                .debugDir(currentPR)
                                                .resolve(target.name)
                                                .resolve("patched_filtered")
                                )
                                .recursive()
                )
                .expect("Was not able to copy variant $target.name")
    }
}

class AllTrueConfiguration : IConfiguration {
    override fun satisfies(p0: Node?): Boolean {
        return true
    }
}

// Abort the program
fun panic(message: String, e: Exception) {
    Logger.error(message)
    Logger.error(e.message)
    Logger.error(e)
    e.printStackTrace()
    throw Panic(message)
}

// Abort the program
fun panic(message: String) {
    Logger.error(message)
    throw Panic(message)
}
