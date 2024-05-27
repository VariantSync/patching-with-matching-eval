package org.variantsync.evaluation.syncstudy

import de.ovgu.featureide.fm.core.base.IFeature
import de.ovgu.featureide.fm.core.base.IFeatureModel
import org.tinylog.kotlin.Logger
import org.variantsync.evaluation.EvalConfig
import org.variantsync.evaluation.SyncStudyResultAnalysis
import org.variantsync.evaluation.analysis.CountingMap
import org.variantsync.evaluation.baseline.diff.DiffParser
import org.variantsync.evaluation.baseline.diff.components.FineDiff
import org.variantsync.evaluation.baseline.diff.components.OriginalDiff
import org.variantsync.evaluation.baseline.diff.filter.CachedPCBasedFilter
import org.variantsync.evaluation.baseline.diff.filter.DiffFilter
import org.variantsync.evaluation.baseline.diff.filter.IFileDiffFilter
import org.variantsync.evaluation.baseline.diff.filter.ILineFilter
import org.variantsync.evaluation.baseline.diff.splitting.DefaultContextProvider
import org.variantsync.evaluation.baseline.diff.splitting.DiffSplitter
import org.variantsync.evaluation.baseline.diff.splitting.IContextProvider
import org.variantsync.evaluation.baseline.shell.CpCommand
import org.variantsync.evaluation.baseline.shell.DiffCommand
import org.variantsync.evaluation.baseline.shell.RmCommand
import org.variantsync.evaluation.error.Panic
import org.variantsync.evaluation.error.VariantGenerationException
import org.variantsync.evaluation.filterUnpatchedFiles
import org.variantsync.evaluation.patching.Change
import org.variantsync.evaluation.patching.Patcher
import org.variantsync.evaluation.patching.Rejects
import org.variantsync.evaluation.saveResult
import org.variantsync.vevos.simulation.feature.Variant
import org.variantsync.vevos.simulation.feature.config.FeatureIDEConfiguration
import org.variantsync.vevos.simulation.feature.sampling.FeatureIDESampler
import org.variantsync.vevos.simulation.feature.sampling.Sample
import org.variantsync.vevos.simulation.io.Resources
import org.variantsync.vevos.simulation.repository.SPLRepository
import org.variantsync.vevos.simulation.util.io.CaseSensitivePath
import org.variantsync.vevos.simulation.variability.SPLCommit
import org.variantsync.vevos.simulation.variability.pc.Artefact
import org.variantsync.vevos.simulation.variability.pc.SourceCodeFile
import org.variantsync.vevos.simulation.variability.pc.groundtruth.GroundTruth
import org.variantsync.vevos.simulation.variability.pc.options.VariantGenerationOptions
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.concurrent.BlockingQueue
import java.util.concurrent.Callable
import java.util.stream.Collectors

class SyncStudyTask(
    private val config: EvalConfig,
    private val datasetName: String,
    private val commit: SPLCommit,
    private val availableOperations: BlockingQueue<SyncStudyOperations>,
    private val parentRepos: Map<SyncStudyOperations, SPLRepository>,
    private val childRepos: Map<SyncStudyOperations, SPLRepository>,
    private val runID: ULong,
) : Callable<ULong> {
    private val strip = 2

    // The feature model for which variants are sampled
    private var currentModel: IFeatureModel? = null

    override fun call(): ULong {
        val operations: SyncStudyOperations
        val parentRepo: SPLRepository
        val childRepo: SPLRepository

        synchronized(SyncStudyTask::class.java) {
            // Retrieve the operations and the repo manager for this task
            Logger.debug("Getting the next available operations (" + availableOperations.size + ")")
            operations = availableOperations.take()
            Logger.debug("There are now " + availableOperations.size + " operations available. Took $operations")
            parentRepo = this.parentRepos[operations]!!
            childRepo = this.childRepos[operations]!!
        }

        try {
            callExecution(operations, parentRepo, childRepo)
        } catch (e: Throwable) {
            Logger.error("Failed to finish task with runID $runID")
            Logger.error(e)
            e.printStackTrace()
        } finally {
            // Place the operations back in the queue to make them available to the next task
            Logger.debug("Placing operation $operations back in queue (" + availableOperations.size + ")")
            availableOperations.add(operations)
            Logger.debug("There are now " + availableOperations.size + " operations available.")
        }

        return runID
    }

    private fun callExecution(operations: SyncStudyOperations, parentRepo: SPLRepository, childRepo: SPLRepository) {
        // We can only process the commit if it has at least one parent
        if (this.commit.parents().isEmpty) {
            return
        }
        val parentCommit = this.commit.parents().get()[0]
        if (parentCommit.id().trim().isEmpty()) {
            return
        }

        try {
            splRepoPreparation(parentRepo, childRepo, parentCommit, this.commit)
        } catch (e: Exception) {
            Logger.error("Was not able to prepare SPL repositories for commit pair ${parentCommit.id()} -> ${this.commit.id()}")
            Logger.error(e)
            e.printStackTrace()
            return
        }

        // While more random configurations to consider
        for (i in 0 until config.EXPERIMENT_REPEATS()) {
            Logger.debug(
                "Starting repetition " + (i + 1) + " of " + config.EXPERIMENT_REPEATS() + " with "
                        + config.EXPERIMENT_VARIANT_COUNT() + " variants."
            )
            if (config.EXPERIMENT_DEBUG() && operations.debugDir(this.commit).toFile().mkdirs()) {
                Logger.debug("Created Debug directory.")
            }

            // Sample set of random variants
            Logger.debug("Sampling next set of variants...")
            val sample = sample(operations)
            Logger.debug("Done. Sampled " + sample.variants().size + " variants.")
            if (Files.exists(operations.variantsDirV0.path())) {
                Logger.debug("Cleaning variants dir V0.")
                operations.shell.execute(RmCommand(operations.variantsDirV0.path()).recursive())
            }
            if (Files.exists(operations.variantsDirV1.path())) {
                Logger.debug("Cleaning variants dir V1.")
                operations.shell.execute(RmCommand(operations.variantsDirV1.path()).recursive())
            }

            // Write information about the commits
            if (config.EXPERIMENT_DEBUG()) {
                splPCDebug(operations)
            }

            // Generate the randomly selected variants at both versions
            val groundTruthV0: MutableMap<Variant, GroundTruth> = HashMap()
            val groundTruthV1: MutableMap<Variant, GroundTruth> = HashMap()
            Logger.debug("Generating variants...")
            if (!generateVariants(operations, sample, groundTruthV0, groundTruthV1)) {
                continue
            }
            Logger.debug("Done.")

            // Select the first variant as source
            val source = sample.variants()[0] ?: continue
            Logger.debug("Starting diff application for source variant " + source.name)
            if (Files.exists(operations.splitPatchFile)) {
                Logger.debug("Cleaning old patch file " + operations.splitPatchFile)
                operations.shell.execute(RmCommand(operations.splitPatchFile))
            }
            // Apply diff to both versions of source variant
            Logger.debug("Diffing source...")
            val originalDiff = getOriginalDiff(
                operations,
                operations.variantsDirV0.path().resolve(source.name),
                operations.variantsDirV1.path().resolve(source.name)
            )
            if (originalDiff.isEmpty) {
                // There was no change to this variant, so we can skip it as source
                Logger.debug(
                    "Skipping " + source.name
                            + " because there are no changes to code. Diff of code files is empty."
                )
                continue
            }
            if (config.EXPERIMENT_DEBUG()) {
                saveDiff(
                    originalDiff,
                    operations.debugDir(this.commit).resolve(source.name + "_original.diff")
                )
            }

            for (patcher in operations.patchers) {
                runPatchApplication(
                    operations,
                    patcher,
                    source,
                    sample,
                    originalDiff,
                    groundTruthV0,
                    groundTruthV1,
                    this.commit,
                    runID,
                    parentCommit
                )
            }
        }

        // Free memory of parentCommit
        parentCommit.forget()
        // Free memory of commit V1
        this.commit.forget()
    }

    private fun runPatchApplication(
        operations: SyncStudyOperations,
        patcher: Patcher,
        source: Variant,
        sample: Sample,
        originalPatch: OriginalDiff,
        groundTruthV0: MutableMap<Variant, GroundTruth>,
        groundTruthV1: MutableMap<Variant, GroundTruth>,
        currentCommit: SPLCommit,
        runID: ULong,
        parentCommit: SPLCommit,
    ) {
        saveDiff(originalPatch, operations.patchFile)
        Logger.debug("Saved original diff.")

        // Convert the original diff into a fine diff
        Logger.debug("Converting diff...")
        val splitPatch = getFineDiff(operations.workDir, originalPatch)
        saveDiff(splitPatch, operations.splitPatchFile)
        Logger.debug("Saved fine diff.")

        // For each target variant,
        Logger.debug("Starting patch application for source variant " + source.name)
        for (target in sample.variants()) {
            if (target === source) {
                continue
            }
            Logger.debug(source.name + " --patch--> " + target.name)
            val pathToTarget = operations.variantsDirV0.path().resolve(target.name)
            val pathToExpectedResult = operations.variantsDirV1.path().resolve(target.name)
            val originalEvolutionDiff = getOriginalDiff(operations, pathToTarget, pathToExpectedResult)

            val patchIsTrivial = originalPatch.partiallyEquals(originalEvolutionDiff, strip)
            if (patchIsTrivial) {
                // We only focus on variability, which is expressed by differences in the patch and evolution
                Logger.debug("Patch is trivial")
            } else {
                Logger.debug("Patch is not trivial")
            }

            var evolutionDiff = getFineDiff(
                operations.workDir,
                originalEvolutionDiff
            )
            evolutionDiff = filterUnpatchedFiles(originalPatch, evolutionDiff, strip)

            /* Application of patches without knowledge about features */
            Logger.debug("Applying patch without knowledge about features...")
            // Apply the patch to the target variant
            resetPatchDirectory(operations, pathToTarget)
            val normalStart = Instant.now()
            val rejectsNormal = patcher.applyPatch(operations, source, target, false)
            val normalEnd = Instant.now()
            val normalDuration = Duration.between(normalStart, normalEnd)

            if (config.EXPERIMENT_DEBUG()) {
                targetFilesNormalDebug(operations, target, pathToTarget, pathToExpectedResult)
            }

            // Gather the patch result
            var actualVsExpectedNormal = getActualVsExpected(operations, pathToExpectedResult, "normal", target)
            actualVsExpectedNormal = filterUnpatchedFiles(originalPatch, actualVsExpectedNormal, strip)

            /* Application of patches with knowledge about PC of edit only */
            Logger.debug("Applying patch with knowledge about edits' PCs...")
            // Create target variant specific patch that respects PCs
            val filteredPatch = getFilteredDiff(
                originalPatch,
                groundTruthV0[source]!!.variant(),
                groundTruthV1[source]!!.variant(), target,
                operations.variantsDirV0.path(), operations.variantsDirV1.path()
            )
            saveDiff(filteredPatch, operations.filteredPatchFile)

            // Create target variant specific patch that respects PCs and is split into line-sized changes
            val splitAndFilteredPatch = getSplitAndFilteredDiff(
                operations,
                originalPatch,
                groundTruthV0[source]!!.variant(),
                groundTruthV1[source]!!.variant(), target,
                operations.variantsDirV0.path(), operations.variantsDirV1.path()
            )
            saveDiff(splitAndFilteredPatch, operations.splitAndFilteredPatchFile)

            // Apply the filtered patch to the target variant, if there are changes left
            resetPatchDirectory(operations, pathToTarget)
            val filteredStart = Instant.now()
            val rejectsFiltered = if (splitAndFilteredPatch.content.isNotEmpty()) {
                patcher.applyPatch(operations, source, target, true)
            } else {
                Rejects(ArrayList())
            }
            val filteredEnd = Instant.now()
            val filteredDuration = Duration.between(filteredStart, filteredEnd)

            // Gather the result
            var actualVsExpectedFiltered = getActualVsExpected(operations, pathToExpectedResult, "filtered", target)
            actualVsExpectedFiltered = filterUnpatchedFiles(originalPatch, actualVsExpectedFiltered, strip)

            patcher.clean(operations)

            if (config.EXPERIMENT_DEBUG()) {
                patchFilesDebug(
                    operations,
                    patcher,
                    originalPatch,
                    splitPatch,
                    source,
                    filteredPatch,
                    splitAndFilteredPatch,
                    target,
                    rejectsNormal,
                    rejectsFiltered,
                    evolutionDiff
                )
            }

            val requiredChanges = getRequiredChanges(
                operations,
                originalPatch,
                groundTruthV0[source]!!.variant(),
                groundTruthV1[source]!!.variant(), target,
                operations.variantsDirV0.path(), operations.variantsDirV1.path()
            )

            /* Result Evaluation */
            val patchOutcome = SyncStudyResultAnalysis.processOutcome(
                operations,
                datasetName, runID, source.name, target.name,
                parentCommit, currentCommit, splitPatch, splitAndFilteredPatch,
                requiredChanges,
                actualVsExpectedNormal, actualVsExpectedFiltered, rejectsNormal,
                rejectsFiltered, evolutionDiff,
                normalDuration, filteredDuration,
                patchIsTrivial,
            )

            val resultFile = config.EXPERIMENT_DIR_RESULTS().resolve("${datasetName}_${patcher.name()}.results")
            saveResult(patchOutcome, resultFile, runID, source, target)
        }
    }

    private fun resetPatchDirectory(operations: SyncStudyOperations, pathToTarget: Path?) {
        // Clean patch directory
        if (Files.exists(operations.patchDir.toAbsolutePath())) {
            operations.shell.execute(RmCommand(operations.patchDir.toAbsolutePath()).recursive())
        }
        try {
            Files.createDirectories(operations.patchDir.parent)
        } catch (e: IOException) {
            e.printStackTrace()
            panic("Was not able to create patch directories: ", e)
        }

        // copy target variant
        operations.shell.execute(CpCommand(pathToTarget, operations.patchDir).recursive())
            .expect("Was not able to copy variant $pathToTarget")
    }

    private fun SyncStudyTask.patchFilesDebug(
        operations: SyncStudyOperations,
        patcher: Patcher,
        originalPatch: OriginalDiff,
        splitPatch: FineDiff,
        source: Variant,
        filteredPatch: OriginalDiff,
        splitAndFilteredPatch: FineDiff,
        target: Variant,
        rejectsNormal: Rejects,
        rejectsFiltered: Rejects,
        evolutionDiff: FineDiff
    ) {
        saveDiff(
            splitPatch,
            operations.debugDir(commit).resolve(source.name + "_split.diff")
        )
        saveDiff(
            splitAndFilteredPatch,
            operations.debugDir(commit).resolve(target.name)
                .resolve(source.name + "_to_" + target.name + "_split_filtered.diff")
        )
        saveDiff(
            originalPatch,
            operations.debugDir(commit).resolve(source.name + ".diff")
        )
        saveDiff(
            filteredPatch,
            operations.debugDir(commit).resolve(target.name)
                .resolve(source.name + "_to_" + target.name + "_filtered.diff")
        )
        saveRejects(
            rejectsNormal,
            operations.debugDir(commit).resolve(target.name)
                .resolve(target.name + "_rejects_normal_${patcher.name()}.diff")
        )
        saveRejects(
            rejectsFiltered,
            operations.debugDir(commit).resolve(target.name)
                .resolve(target.name + "_rejects_filtered_${patcher.name()}.diff")
        )
        operations.debugDir(commit).resolve(target.name).toFile().mkdirs()
        saveDiff(
            evolutionDiff,
            operations.debugDir(commit).resolve(target.name)
                .resolve(target.name + "_evolution.diff")
        )
        operations.shell.execute(
            CpCommand(
                operations.patchDir,
                operations.debugDir(commit).resolve(target.name).resolve("patched_filtered")
            ).recursive()
        )
            .expect("Was not able to copy variant $target.name")
    }


    private fun generateVariants(
        operations: SyncStudyOperations,
        sample: Sample,
        groundTruthV0: MutableMap<Variant, GroundTruth>,
        groundTruthV1: MutableMap<Variant, GroundTruth>,
    ): Boolean {
        var success = true
        for (variant in sample.variants()) {
            try {
                generateVariant(operations, groundTruthV0, groundTruthV1, variant)
            } catch (e: Exception) {
                Logger.warn(
                    "Was not able to generate all variants for commit ${this.commit.id()}:\n" +
                            "{}", e
                )
                Logger.warn("Skipping commit.")
                success = false
            }
        }
        return success
    }

    /**
     * Get the difference between the target variant after patching and the target variant in the
     * next de.variantsync.studies.evolution step. Then, filter all differences that do not belong
     * to the source variant and could have therefore not been synchronized in any case.
     */
    private fun getActualVsExpected(
        operations: SyncStudyOperations,
        pathToExpectedResult: Path,
        filePostfix: String,
        target: Variant
    ): FineDiff {
        val resultDiff = getOriginalDiff(operations, operations.patchDir, pathToExpectedResult, true)
        if (config.EXPERIMENT_DEBUG()) {
            try {
                Files.write(
                    operations.debugDir(this.commit).resolve(target.name)
                        .resolve(target.name + "_actual_expected-$filePostfix.diff"),
                    resultDiff.toLines()
                )
            } catch (e: IOException) {
                Logger.error("Was not able to save resultDiffOriginal:\n{}", e)
            }
        }
        return getFineDiff(operations.workDir, resultDiff)
    }

    /**
     * Randomly sample a set of variants valid in both commits.
     *
     * @return The sampled variants
     */
    fun sample(operations: SyncStudyOperations): Sample {
        if (currentModel == null || this.commit !== commit) {
            Logger.debug("Loading feature models.")
            currentModel = commit.featureModel().run().orElseThrow()
            featureModelDebug(operations, currentModel)
        }
        return FeatureIDESampler.CreateRandomSampler(this.config.EXPERIMENT_VARIANT_COUNT()).sample(currentModel)
    }

    // Save the features in the feature models
    private fun featureModelDebug(operations: SyncStudyOperations, model: IFeatureModel?) {
        if (config.EXPERIMENT_DEBUG()) {
            try {
                Files.write(
                    operations.debugDir(this.commit).resolve("features.txt"), model!!.features.stream()
                        .map { obj: IFeature -> obj.name }.collect(Collectors.toSet())
                )
            } catch (e: IOException) {
                Logger.error("Was not able to write commit data:\n{}", e)
            }
        }
    }

    // Generate the two versions of a variant
    private fun generateVariant(
        operations: SyncStudyOperations,
        groundTruthV0: MutableMap<Variant, GroundTruth>,
        groundTruthV1: MutableMap<Variant, GroundTruth>, variant: Variant
    ) {
        Logger.debug("Generating variant " + variant.name)
        if (config.EXPERIMENT_DEBUG() && variant.configuration is FeatureIDEConfiguration) {
            val config = variant.configuration as FeatureIDEConfiguration
            val p = operations.debugDir(commit).resolve("configs")
            p.toFile().mkdirs()
            try {
                Files.write(
                    p.resolve(variant.name + ".config"),
                    config.toAssignment().entries.stream()
                        .map { (key, value): Map.Entry<Any, Boolean> -> "$key : $value" }
                        .collect(Collectors.toList())
                )
            } catch (e: IOException) {
                Logger.error(
                    "Was not able to write configuration of " + variant.name + ":\n" +
                            "{}", e
                )
            }
        }
        try {
            Files.createDirectories(operations.variantsDirV0.path().resolve(variant.name))
            Files.createDirectories(operations.variantsDirV1.path().resolve(variant.name))
        } catch (e: IOException) {
            e.printStackTrace()
            panic("Was not able to create directory for variant: " + variant.name)
        }
        val gtV0 = commit.presenceConditionsBefore().run().orElseThrow {
            NoSuchElementException(
                "%s ; %s ; %s".format(
                    variant,
                    operations.splCopyB, commit
                )
            )
        }
            .generateVariant(variant, CaseSensitivePath(operations.splCopyA),
                operations.variantsDirV0.resolve(variant.name),
                VariantGenerationOptions
                    .ExitOnErrorButAllowNonExistentFiles(
                        false
                    ) { _: SourceCodeFile? -> true })

        if (gtV0.isFailure) {
            Logger.error("Was not able to generate V0 of $variant:\n{}")
            throw VariantGenerationException(gtV0.failure)

        }

        if (config.EXPERIMENT_DEBUG()) {
            try {
                val p = operations.debugDir(commit)
                    .resolve("PCs")
                    .resolve("parentCommit-" + variant.name + ".variant.csv")
                p.parent.toFile().mkdirs()
                Resources.Instance().write(
                    Artefact::class.java, gtV0.success.variant(), p
                )
            } catch (e: Resources.ResourceIOException) {
                Logger.error(
                    "Was not able to write ground truth:\n" +
                            "{}", e
                )
            }
        }
        groundTruthV0[variant] = gtV0.success

        val gtV1 = commit.presenceConditionsAfter().run()
            .orElseThrow {
                NoSuchElementException(
                    "%s ; %s ; %s".format(
                        variant,
                        operations.splCopyB, commit
                    )
                )
            }
            .generateVariant(variant, CaseSensitivePath(operations.splCopyB),
                operations.variantsDirV1.resolve(variant.name),
                VariantGenerationOptions
                    .ExitOnErrorButAllowNonExistentFiles(
                        false
                    ) { _: SourceCodeFile? -> true })
        if (gtV1.isFailure) {
            Logger.error(
                "Was not able to generate V1 of $variant:\n" +
                        "{}", gtV1.failure
            )
            throw VariantGenerationException(gtV1.failure)
        }
        if (config.EXPERIMENT_DEBUG()) {
            try {
                val p = operations.debugDir(commit)
                    .resolve("PCs")
                    .resolve("childCommit-" + variant.name + ".variant.csv")
                p.parent.toFile().mkdirs()
                Resources.Instance().write(
                    Artefact::class.java, gtV1.success.variant(), p
                )
            } catch (e: Resources.ResourceIOException) {
                Logger.error(
                    "Was not able to write ground truth:\n" +
                            "{}", e
                )
            }
        }
        groundTruthV1[variant] = gtV1.success
    }

    /**
     * Prepare the two copies of the SPL repository by cleaning them and checking out the next
     * commit pair
     */
    private fun splRepoPreparation(
        parentRepo: SPLRepository, childRepo: SPLRepository,
        parentCommit: SPLCommit, childCommit: SPLCommit
    ) {
        Logger.debug("Next V0 commit: $parentCommit")
        Logger.debug("Next V1 commit: $childCommit")
        // Checkout the commits in the SPL repository
        try {
            Logger.debug("Checkout of commits in SPL repo.")
            parentRepo.checkoutCommit(parentCommit, true)
            childRepo.checkoutCommit(childCommit, true)
        } catch (e: Exception) {
            Logger.warn(
                "Was not able to checkout commits ($parentCommit -> $childCommit) for SPL repository:\n" +
                        "{}", e
            )
            // Try checkout again
            Logger.warn("Retry of commits in SPL repo.")
            parentRepo.checkoutCommit(parentCommit, true)
            childRepo.checkoutCommit(childCommit, true)
            Logger.warn("Retry succeeded")
        }
        Logger.debug("Done.")
    }

    /**
     * Clean the repo before the next commit is checked out.
     */
    private fun cleanRepo(repo: SPLRepository) {
        // Stash all changes and drop the stash. This is a workaround as the JGit API does not support restore.
        Logger.warn("Cleaning state of V0 repo.")
        try {
            repo.stashCreate(true)
            repo.dropStash()
            Logger.warn("Cleaning state of repo.")
        } catch (e: Exception) {
            panic("Was not able to clean SPL repository (${repo.path}).", e)
        }
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

    // Get the filtered line-level patches for a given difference
    private fun getSplitAndFilteredDiff(
        operations: SyncStudyOperations,
        originalDiff: OriginalDiff,
        tracesV0: Artefact,
        tracesV1: Artefact,
        target: Variant,
        oldVersionRoot: Path,
        newVersionRoot: Path
    ): FineDiff {
        val cachedPCBasedFilter = CachedPCBasedFilter(tracesV0, tracesV1, target, oldVersionRoot, newVersionRoot, strip)
        return getSplitAndFilteredDiff(operations, originalDiff, cachedPCBasedFilter, false)
    }

    // Get the filtered line-level patches for a given difference
    private fun getFilteredDiff(
        originalDiff: OriginalDiff,
        tracesV0: Artefact,
        tracesV1: Artefact,
        target: Variant,
        oldVersionRoot: Path,
        newVersionRoot: Path
    ): OriginalDiff {
        val cachedPCBasedFilter = CachedPCBasedFilter(tracesV0, tracesV1, target, oldVersionRoot, newVersionRoot, strip)
        return DiffFilter.filter(originalDiff, cachedPCBasedFilter, cachedPCBasedFilter)
    }

    // Get the filtered line-level patches for a given difference
    private fun getRequiredChanges(
        operations: SyncStudyOperations,
        originalDiff: OriginalDiff,
        tracesV0: Artefact,
        tracesV1: Artefact,
        target: Variant,
        oldVersionRoot: Path,
        newVersionRoot: Path
    ): CountingMap<Change> {
        val cachedPCBasedFilter = CachedPCBasedFilter(tracesV0, tracesV1, target, oldVersionRoot, newVersionRoot, strip)
        val fineDiff = getSplitAndFilteredDiff(operations, originalDiff, cachedPCBasedFilter, true)
        return CountingMap(fineDiff.intoChanges(strip))
    }

    // Get the filtered line-level patches for a given difference
    private fun <T> getSplitAndFilteredDiff(
        operations: SyncStudyOperations,
        originalDiff: OriginalDiff,
        filter: T,
        filterDisabled: Boolean,
    ): FineDiff where T : IFileDiffFilter?, T : ILineFilter? {
        // Create target variant specific patch that respects PCs
        val contextProvider: IContextProvider = DefaultContextProvider(operations.workDir, filterDisabled)
        return DiffSplitter.split(originalDiff, filter, filter, contextProvider)
    }

    // Get the difference between two directories using UNIX diff
    private fun getOriginalDiff(
        operations: SyncStudyOperations,
        v0Path: Path, v1Path: Path
    ): OriginalDiff {
        return getOriginalDiff(operations, v0Path, v1Path, false)
    }

    // Get the difference between two directories using UNIX diff
    private fun getOriginalDiff(
        operations: SyncStudyOperations,
        v0Path: Path, v1Path: Path,
        ignoreBlankLines: Boolean,
    ): OriginalDiff {
        val diffCommand: DiffCommand = DiffCommand.Recommended(
            operations.workDir.relativize(v0Path),
            operations.workDir.relativize(v1Path)
        )
        if (ignoreBlankLines) {
            diffCommand.ignoreBlankLines()
        }
        val output = operations.shell.execute(diffCommand, operations.workDir)
            .expect("Was not able to diff variants.")
        return DiffParser.toOriginalDiff(output)
    }

    private fun splPCDebug(operations: SyncStudyOperations) {
        try {
            val v0PCs = this.commit.presenceConditionsBefore().run()
            val p = operations.debugDir(this.commit).resolve("PCs")
            p.toFile().mkdirs()
            if (v0PCs.isPresent) {
                Resources.Instance().write(
                    Artefact::class.java, v0PCs.get(),
                    p.resolve("pcs-parent.spl.csv")
                )
            }
            val v1PCs = this.commit.presenceConditionsAfter().run()
            if (v1PCs.isPresent) {
                Resources.Instance().write(
                    Artefact::class.java, v1PCs.get(),
                    p.resolve("pcs-child.spl.csv")
                )
            }
        } catch (e: Resources.ResourceIOException) {
            Logger.error("Was not able to write PCs", e)
        }
    }

    private fun targetFilesNormalDebug(
        operations: SyncStudyOperations,
        target: Variant,
        pathToTarget: Path,
        pathToExpectedResult: Path
    ) {
        operations.debugDir(this.commit).resolve(target.name).toFile().mkdirs()
        operations.shell.execute(
            CpCommand(
                pathToTarget,
                operations.debugDir(this.commit).resolve(target.name).resolve("original")
            ).recursive()
        )
            .expect("Was not able to copy variant $target.name")
        operations.shell.execute(
            CpCommand(
                operations.patchDir,
                operations.debugDir(this.commit).resolve(target.name).resolve("patched_normal")
            ).recursive()
        )
            .expect("Was not able to copy variant $target.name")
        operations.shell.execute(
            CpCommand(
                pathToExpectedResult,
                operations.debugDir(this.commit).resolve(target.name).resolve("expected")
            ).recursive()
        )
            .expect("Was not able to copy variant $target.name")
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


fun getFineDiff(workDir: Path, originalDiff: OriginalDiff): FineDiff {
    val contextProvider = DefaultContextProvider(workDir)
    return DiffSplitter.split(originalDiff, contextProvider)
}