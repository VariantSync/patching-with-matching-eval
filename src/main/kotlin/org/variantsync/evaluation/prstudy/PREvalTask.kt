package org.variantsync.evaluation.prstudy

import org.tinylog.kotlin.Logger
import org.variantsync.evaluation.EvalConfig
import org.variantsync.evaluation.IDProvider
import org.variantsync.evaluation.ResultAnalysis
import org.variantsync.evaluation.analysis.CountingMap
import org.variantsync.evaluation.baseline.diff.DiffParser
import org.variantsync.evaluation.baseline.diff.components.FineDiff
import org.variantsync.evaluation.baseline.diff.components.OriginalDiff
import org.variantsync.evaluation.baseline.shell.CpCommand
import org.variantsync.evaluation.baseline.shell.DiffCommand
import org.variantsync.evaluation.baseline.shell.RmCommand
import org.variantsync.evaluation.patching.Patcher
import org.variantsync.evaluation.patching.Rejects
import org.variantsync.evaluation.syncstudy.getFineDiff
import org.variantsync.evaluation.syncstudy.panic
import org.variantsync.vevos.simulation.feature.Variant
import org.variantsync.vevos.simulation.variability.SPLCommit
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class PREvalTask(
    private val config: EvalConfig,
    private val datasetName: String, private val gitHubRepoPath: Path,
    private val pullRequests: List<PullRequest>,
) : Runnable {
    private val operations: PREvalOperations = PREvalOperations(config.EXPERIMENT_DIR_MAIN())
    private val idProvider: IDProvider = IDProvider(config.EXPERIMENT_START_ID())

    override fun run() {
        // Clean old variant files
        cleanVariantDirectories()
        // Copy the source and target variant to the respective variant directories
        prepareVariantDirectories()

        val repoManager = VariantRepoManager(operations)

        // For each pull request
        Logger.info("Starting diffing and patching for pull requests...")
        var runID: ULong
        var numProcessed = 0uL
        val numPRs = pullRequests.size.toLong()
        Logger.info("There are $numPRs pull requests to work on.")
        for (pullRequest in pullRequests) {
            // Increase one extra time for the first parent in the sequence
            numProcessed++
            // Skip pairs until the start ID has been reached.
            runID = idProvider.next()
            if (runID < idProvider.start) {
                Logger.info("Skipped commit $runID")
                continue
            }

            try {
                repoManager.cleanRepoStates()
                if (!repoManager.preparePullRequest(pullRequest)) {
                    Logger.info("Not all commits of the PR could be found... skipping PR ${pullRequest.id} of $datasetName")
                    continue
                }
            } catch (e: Exception) {
                Logger.error("Was not able to checkout pull request commits in variant directories")
                Logger.error(e)
                e.printStackTrace()
                continue
            }

            if (config.EXPERIMENT_DEBUG() && operations.debugDir(pullRequest).toFile().mkdirs()) {
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
                operations.sourceVariantV0,
                operations.sourceVariantV1
            )

            if (originalPatch.isEmpty) {
                // There was no change to this variant, so we can skip it as source
                Logger.warn(
                    "Skipping PR " + pullRequest.id + " because there are no changes to code. Diff of code files is empty."
                )
                continue
            }

            if (config.EXPERIMENT_DEBUG()) {
                saveDiff(
                    originalPatch,
                    operations.debugDir(pullRequest).resolve("original.diff")
                )
            }

            for (patcher in operations.patchers) {
                saveDiff(originalPatch, operations.patchFile)
                Logger.debug("Saved original diff.")

                // Convert the original diff into a fine diff
                Logger.debug("Converting diff...")
                val splitPatch = getFineDiff(operations.workDir, originalPatch)
                saveDiff(splitPatch, operations.splitPatchFile)
                Logger.debug("Saved fine diff.")

                Logger.debug("Starting patch application for pull request " + pullRequest.id)
                val evolutionDiff = getFineDiff(
                    operations.workDir,
                    getOriginalDiff(operations.targetVariantV0, operations.targetVariantV1)
                )

                /* Application of patches without knowledge about features */
                Logger.debug("Applying patch from pull request...")
                val rejectsNormal = patcher.applyPatch(operations, source, target, false)

                // Gather the patch result
                val actualVsExpectedNormal = getActualVsExpected(operations.targetVariantV1, target, pullRequest)

                patcher.clean(operations)

                if (config.EXPERIMENT_DEBUG()) {
                    patchFilesDebug(
                        patcher,
                        originalPatch,
                        splitPatch,
                        pullRequest,
                        source,
                        target,
                        rejectsNormal,
                        evolutionDiff
                    )
                }

                val requiredChanges = CountingMap(splitPatch.intoChanges())

                /* Result Evaluation */
                val patchOutcome = ResultAnalysis.processOutcome(
                    operations,
                    datasetName, runID, source.name, target.name,
                    SPLCommit(pullRequest.sourceV0), SPLCommit(pullRequest.sourceV1), splitPatch, splitPatch,
                    requiredChanges,
                    actualVsExpectedNormal, actualVsExpectedNormal, rejectsNormal,
                    rejectsNormal, evolutionDiff
                )

                val resultFile = config.EXPERIMENT_DIR_RESULTS().resolve("${datasetName}_${patcher.name()}.results")
                try {
                    patchOutcome.writeAsJSON(resultFile, true)
                } catch (e: IOException) {
                    panic(
                        "Was not able to write filtered patch result file for run "
                                + runID, e
                    )
                }
                Logger.debug(
                    "Finished patching for source " + source.name + " and target "
                            + target.name
                )
            }
            if (numProcessed % 100uL == 0uL) {
                Logger.info(
                    String.format(
                        "Finished pull request %s of %s.%n",
                        numProcessed.toString(),
                        numPRs.toString()
                    )
                )
            }
        }
    }

    private fun prepareVariantDirectories() {
        Logger.debug("Creating new source and target variant copies.")
        operations.shell.execute(CpCommand(gitHubRepoPath, operations.sourceVariantV0).recursive())
            .expect("Was not able to copy source variant V0.")
        operations.shell.execute(CpCommand(gitHubRepoPath, operations.sourceVariantV1).recursive())
            .expect("Was not able to copy source variant V1.")
        operations.shell.execute(CpCommand(gitHubRepoPath, operations.targetVariantV0).recursive())
            .expect("Was not able to copy target variant V0.")
        operations.shell.execute(CpCommand(gitHubRepoPath, operations.targetVariantV1).recursive())
            .expect("Was not able to copy target variant V1.")
    }

    private fun cleanVariantDirectories() {
        Logger.debug("Cleaning old variant files.")
        if (Files.exists(operations.sourceVariantV0)) {
            operations.shell.execute(RmCommand(operations.sourceVariantV0).recursive())
                .expect("Was not able to remove source variant V0.")
        }
        if (Files.exists(operations.sourceVariantV1)) {
            operations.shell.execute(RmCommand(operations.sourceVariantV1).recursive())
                .expect("Was not able to remove source variant V1.")
        }
        if (Files.exists(operations.targetVariantV0)) {
            operations.shell.execute(RmCommand(operations.targetVariantV0).recursive())
                .expect("Was not able to remove target variant V0.")
        }
        if (Files.exists(operations.targetVariantV1)) {
            operations.shell.execute(RmCommand(operations.sourceVariantV1).recursive())
                .expect("Was not able to remove target variant V1.")
        }
    }

    /**
     * Get the difference between the target variant after patching and the target variant in the
     * next de.variantsync.studies.evolution step. Then, filter all differences that do not belong
     * to the source variant and could have therefore not been synchronized in any case.
     */
    private fun getActualVsExpected(pathToExpectedResult: Path, target: Variant, currentPR: PullRequest): FineDiff {
        val resultDiff = getOriginalDiff(operations.patchDir(), pathToExpectedResult)
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
        v0Path: Path, v1Path: Path
    ): OriginalDiff {
        val diffCommand: DiffCommand = DiffCommand.Recommended(
            operations.workDir.relativize(v0Path),
            operations.workDir.relativize(v1Path)
        ).exclude(".git")
        val output = operations.shell.execute(diffCommand, operations.workDir)
            .expect("Was not able to diff variants.")
        return DiffParser.toOriginalDiff(output)
    }

    private fun patchFilesDebug(
        patcher: Patcher,
        originalPatch: OriginalDiff,
        splitPatch: FineDiff,
        currentPR: PullRequest,
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
