package org.variantsync.evaluation

import de.ovgu.featureide.fm.core.base.IFeature
import de.ovgu.featureide.fm.core.base.IFeatureModel
import org.tinylog.kotlin.Logger
import org.variantsync.evaluation.baseline.diff.DiffParser
import org.variantsync.evaluation.baseline.diff.components.FineDiff
import org.variantsync.evaluation.baseline.diff.components.OriginalDiff
import org.variantsync.evaluation.baseline.diff.filter.CachedPCBasedFilter
import org.variantsync.evaluation.baseline.diff.filter.IFileDiffFilter
import org.variantsync.evaluation.baseline.diff.filter.ILineFilter
import org.variantsync.evaluation.baseline.diff.splitting.DefaultContextProvider
import org.variantsync.evaluation.baseline.diff.splitting.DiffSplitter
import org.variantsync.evaluation.baseline.diff.splitting.IContextProvider
import org.variantsync.evaluation.baseline.shell.*
import org.variantsync.evaluation.common.Change
import org.variantsync.evaluation.error.Panic
import org.variantsync.evaluation.error.VariantGenerationException
import org.variantsync.vevos.simulation.feature.Variant
import org.variantsync.vevos.simulation.feature.config.FeatureIDEConfiguration
import org.variantsync.vevos.simulation.feature.sampling.FeatureIDESampler
import org.variantsync.vevos.simulation.feature.sampling.Sample
import org.variantsync.vevos.simulation.feature.sampling.Sampler
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
import java.util.function.Consumer
import java.util.stream.Collectors

class Task(
    datasetName: String, mainDir: Path, repositoryPath: Path,
    resultsFile: Path, commits: List<SPLCommit>, numRepetitions: Int, numVariants: Int,
    inDebug: Boolean, idProvider: IDProvider
) : Runnable {
    private val operations: Operations
    private val resultsFile: Path
    private val repositoryPath: Path
    private val commits: List<SPLCommit>
    private val idProvider: IDProvider
    private val numRepetitions: Int
    private val numVariants: Int
    private val inDebug: Boolean
    private val datasetName: String

    // The variant sampler
    private val sampler: Sampler

    // The feature model for which variants are sampled
    private var currentModel: IFeatureModel? = null

    // The considered commit
    private var currentCommit: SPLCommit? = null

    init {
        operations = Operations(mainDir)
        this.repositoryPath = repositoryPath
        this.datasetName = datasetName
        this.resultsFile = resultsFile
        this.commits = commits
        this.numRepetitions = numRepetitions
        this.numVariants = numVariants
        this.inDebug = inDebug
        this.idProvider = idProvider
        sampler = FeatureIDESampler.CreateRandomSampler(this.numVariants)
    }

    override fun run() {
        // Initialize the SPL repositories for different versions
        Logger.info("Initializing SPL repos.")
        initializeSPLCopies()
        val parentRepo = SPLRepository(operations.splCopyA)
        val childRepo = SPLRepository(operations.splCopyB)
        try {
            Files.createDirectories(resultsFile.parent)
        } catch (e: IOException) {
            panic("Was not able to create results directory for $resultsFile")
        }

        // For each pair
        Logger.info("Starting diffing and patching...")
        var runID: ULong
        var numProcessed = 0uL
        val numCommits = commits.size.toLong()
        Logger.info("There are $numCommits commits to work on.")
        for (currentCommit in commits) {
            // Increase one extra time for the first parent in the sequence
            numProcessed++
            // Skip pairs until the start ID has been reached.
            runID = idProvider.next()
            if (runID < idProvider.start) {
                Logger.info("Skipped commit $runID")
                continue
            }
            // We can only process the commit if it has at least one parent
            if (currentCommit.parents().isEmpty) {
                continue
            }
            val parentCommit = currentCommit.parents().get()[0]
            if (parentCommit.id().trim().isEmpty()) {
                continue
            }

            try {
                splRepoPreparation(parentRepo, childRepo, parentCommit, currentCommit)
            } catch (e: Exception) {
                Logger.error("Was not able to prepare SPL repositories for commit pair ${parentCommit.id()} -> ${currentCommit.id()}")
                Logger.error(e)
                e.printStackTrace()
                continue
            }

            // While more random configurations to consider
            for (i in 0 until numRepetitions) {
                Logger.debug(
                    "Starting repetition " + (i + 1) + " of " + numRepetitions + " with "
                            + numVariants + " variants."
                )
                if (inDebug && operations.debugDir(currentCommit).toFile().mkdirs()) {
                    Logger.debug("Created Debug directory.")
                }

                // Sample set of random variants
                Logger.debug("Sampling next set of variants...")
                val sample = sample(currentCommit)
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
                if (inDebug) {
                    splPCDebug()
                }

                // Generate the randomly selected variants at both versions
                val groundTruthV0: MutableMap<Variant, GroundTruth> = HashMap()
                val groundTruthV1: MutableMap<Variant, GroundTruth> = HashMap()
                Logger.debug("Generating variants...")
                if (!generateVariants(sample, groundTruthV0, groundTruthV1)) {
                    continue
                }
                Logger.debug("Done.")

                // Select the first variant as source
                val source = sample.variants()[0] ?: continue
                Logger.debug("Starting diff application for source variant " + source.name)
                if (Files.exists(operations.normalPatchFile)) {
                    Logger.debug("Cleaning old patch file " + operations.normalPatchFile)
                    operations.shell.execute(RmCommand(operations.normalPatchFile))
                }
                // Apply diff to both versions of source variant
                Logger.debug("Diffing source...")
                val originalDiff = getOriginalDiff(
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
                if (inDebug) {
                    saveDiff(
                        originalDiff,
                        operations.debugDir(currentCommit).resolve(source.name + "_original.diff")
                    )
                }
                /*
                evaluateUnixPatch(
                    originalDiff,
                    currentCommit,
                    source,
                    sample,
                    groundTruthV0,
                    groundTruthV1,
                    runID,
                    parentCommit
                )*/

                evaluateMPatch(
                    originalDiff,
                    currentCommit,
                    source,
                    sample,
                    groundTruthV0,
                    groundTruthV1,
                    runID,
                    parentCommit
                )
            }
            if (numProcessed % 100uL == 0uL) {
                Logger.info(
                    String.format(
                        "Finished commit %s of %s.%n",
                        numProcessed.toString(),
                        numCommits.toString()
                    )
                )
            }

            // Free memory of parentCommit
            parentCommit.forget()
            // Free memory of commit V1
            currentCommit.forget()
        }
    }

    private fun evaluateUnixPatch(
        originalDiff: OriginalDiff,
        currentCommit: SPLCommit,
        source: Variant,
        sample: Sample,
        groundTruthV0: MutableMap<Variant, GroundTruth>,
        groundTruthV1: MutableMap<Variant, GroundTruth>,
        runID: ULong,
        parentCommit: SPLCommit
    ) {
        Logger.debug("Converting diff...")
        // Convert the original diff into a fine diff
        val finePatch = getFineDiff(originalDiff)
        saveDiff(finePatch, operations.normalPatchFile)
        Logger.debug("Saved fine diff.")

        val patchApplier = { _: Path, pathToTarget: Path ->
            applyPatch(
                operations.normalPatchFile,
                pathToTarget,
                operations.rejectsNormalFile
            )
        }


        runPatchApplication(
            source,
            sample,
            patchApplier,
            finePatch,
            originalDiff,
            groundTruthV0,
            groundTruthV1,
            currentCommit,
            runID,
            parentCommit,
        )
    }

    private fun evaluateMPatch(
        originalDiff: OriginalDiff,
        currentCommit: SPLCommit,
        source: Variant,
        sample: Sample,
        groundTruthV0: MutableMap<Variant, GroundTruth>,
        groundTruthV1: MutableMap<Variant, GroundTruth>,
        runID: ULong,
        parentCommit: SPLCommit
    ) {
        saveDiff(originalDiff, operations.normalPatchFile)
        Logger.debug("Saved original diff.")

        val patchApplier = { pathToSource: Path, pathToTarget: Path ->
            applyMPatch(
                operations.normalPatchFile,
                pathToSource,
                pathToTarget,
                operations.rejectsNormalFile
            )
        }

        runPatchApplication(
            source,
            sample,
            patchApplier,
            getFineDiff(originalDiff),
            originalDiff,
            groundTruthV0,
            groundTruthV1,
            currentCommit,
            runID,
            parentCommit,
        )
    }

    private fun runPatchApplication(
        source: Variant,
        sample: Sample,
        patchApplier: (Path, Path) -> Set<String>,
        finePatch: FineDiff,
        originalDiff: OriginalDiff,
        groundTruthV0: MutableMap<Variant, GroundTruth>,
        groundTruthV1: MutableMap<Variant, GroundTruth>,
        currentCommit: SPLCommit,
        runID: ULong,
        parentCommit: SPLCommit,
    ) {
        // For each target variant,
        Logger.debug("Starting patch application for source variant " + source.name)
        for (target in sample.variants()) {
            if (target === source) {
                continue
            }
            Logger.debug(source.name + " --patch--> " + target.name)
            val pathToSource = operations.variantsDirV0.path().resolve(source.name)
            val pathToTarget = operations.variantsDirV0.path().resolve(target.name)
            val pathToExpectedResult = operations.variantsDirV1.path().resolve(target.name)
            val evolutionDiff = getFineDiff(
                getOriginalDiff(pathToTarget, pathToExpectedResult)
            )

            /* Application of patches without knowledge about features */
            Logger.debug("Applying patch without knowledge about features...")
            // Apply the fine diff to the target variant
            val skippedNormal = patchApplier(pathToSource, pathToTarget)
            if (inDebug) {
                targetFilesNormalDebug(target, pathToTarget, pathToExpectedResult)
            }

            // Gather the patch result
            val actualVsExpectedNormal = getActualVsExpected(pathToExpectedResult, "normal", target)
            // TODO: mpatch specific rejects reading
            val rejectsNormal = readRejects(operations.rejectsNormalFile, finePatch)

            /* Application of patches with knowledge about PC of edit only */
            Logger.debug("Applying patch with knowledge about edits' PCs...")
            // Create target variant specific patch that respects PCs
            // TODO: mpatch specific filtering
            val filteredPatch = getFilteredDiff(
                originalDiff,
                groundTruthV0[source]!!.variant(),
                groundTruthV1[source]!!.variant(), target,
                operations.variantsDirV0.path(), operations.variantsDirV1.path()
            )
            val emptyPatch = filteredPatch.content.isEmpty()
            saveDiff(filteredPatch, operations.filteredPatchFile)

            // Apply the patch
            val skippedFiltered = applyPatch(
                operations.filteredPatchFile,
                pathToTarget, operations.rejectsFilteredFile, emptyPatch
            )

            // Gather the result
            val actualVsExpectedFiltered = getActualVsExpected(pathToExpectedResult, "filtered", target)
            val rejectsFiltered = readRejects(operations.rejectsFilteredFile, filteredPatch)
            if (inDebug) {
                patchFilesDebug(
                    finePatch,
                    currentCommit,
                    source,
                    filteredPatch,
                    target,
                    rejectsNormal,
                    rejectsFiltered,
                    evolutionDiff
                )
            }

            val requiredChanges = getRequiredChanges(
                originalDiff,
                groundTruthV0[source]!!.variant(),
                groundTruthV1[source]!!.variant(), target,
                operations.variantsDirV0.path(), operations.variantsDirV1.path()
            )

            /* Result Evaluation */
            val patchOutcome = ResultAnalysis.processOutcome(
                operations,
                datasetName, runID, source.name, target.name,
                parentCommit, currentCommit, finePatch, filteredPatch,
                requiredChanges,
                actualVsExpectedNormal, actualVsExpectedFiltered, rejectsNormal,
                rejectsFiltered, evolutionDiff, skippedNormal, skippedFiltered
            )
            try {
                patchOutcome.writeAsJSON(resultsFile, true)
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
    }

    private fun Task.patchFilesDebug(
        normalPatch: FineDiff,
        currentCommit: SPLCommit,
        source: Variant,
        filteredPatch: FineDiff,
        target: Variant,
        rejectsNormal: FineDiff,
        rejectsFiltered: FineDiff,
        evolutionDiff: FineDiff
    ) {
        saveDiff(
            normalPatch,
            operations.debugDir(currentCommit).resolve(source.name + "_to_any_patch_normal.diff")
        )
        saveDiff(
            filteredPatch,
            operations.debugDir(currentCommit).resolve(target.name)
                .resolve(source.name + "_to_" + target.name + "_patch_filtered.diff")
        )
        saveDiff(
            rejectsNormal,
            operations.debugDir(currentCommit).resolve(target.name)
                .resolve(target.name + "_rejects_normal.diff")
        )
        saveDiff(
            rejectsFiltered,
            operations.debugDir(currentCommit).resolve(target.name)
                .resolve(target.name + "_rejects_filtered.diff")
        )
        operations.debugDir(currentCommit).resolve(target.name).toFile().mkdirs()
        saveDiff(
            evolutionDiff,
            operations.debugDir(currentCommit).resolve(target.name)
                .resolve(target.name + "_evolution.diff")
        )
        operations.shell.execute(
            CpCommand(
                operations.patchDir,
                operations.debugDir(currentCommit).resolve(target.name).resolve("patched_filtered")
            ).recursive()
        )
            .expect("Was not able to copy variant $target.name")
    }


    private fun generateVariants(
        sample: Sample,
        groundTruthV0: MutableMap<Variant, GroundTruth>,
        groundTruthV1: MutableMap<Variant, GroundTruth>,
    ): Boolean {
        var success = true
        for (variant in sample.variants()) {
            try {
                generateVariant(currentCommit!!, groundTruthV0, groundTruthV1, variant)
            } catch (e: Exception) {
                Logger.warn(
                    "Was not able to generate all variants for commit ${currentCommit!!.id()}:\n" +
                            "{}", e
                )
                Logger.warn("Skipping commit.")
                success = false
            }
        }
        return success
    }

    private fun initializeSPLCopies() {
        // Clean old SPL repo files
        Logger.debug("Cleaning old repo files.")
        if (Files.exists(operations.splCopyA)) {
            operations.shell.execute(RmCommand(operations.splCopyA).recursive())
                .expect("Was not able to remove SPL-V0.")
        }
        if (Files.exists(operations.splCopyB)) {
            operations.shell.execute(RmCommand(operations.splCopyB).recursive())
                .expect("Was not able to remove SPL-V1.")
        }
        // Copy the SPL repo
        Logger.debug("Creating new SPL repo copies.")
        operations.shell.execute(CpCommand(repositoryPath, operations.splCopyA).recursive())
            .expect("Was not able to copy SPL-V0.")
        operations.shell.execute(CpCommand(repositoryPath, operations.splCopyB).recursive())
            .expect("Was not able to copy SPL-V1.")
    }

    /**
     * Get the difference between the target variant after patching and the target variant in the
     * next de.variantsync.studies.evolution step. Then, filter all differences that do not belong
     * to the source variant and could have therefore not been synchronized in any case.
     */
    private fun getActualVsExpected(pathToExpectedResult: Path, filePostfix: String, target: Variant): FineDiff {
        val resultDiff = getOriginalDiff(operations.patchDir, pathToExpectedResult)
        if (inDebug) {
            try {
                Files.write(
                    operations.debugDir(currentCommit!!).resolve(target.name)
                        .resolve(target.name + "_actual_expected-$filePostfix.diff"),
                    resultDiff.toLines()
                )
            } catch (e: IOException) {
                Logger.error("Was not able to save resultDiffOriginal:\n{}", e)
            }
        }
        return getFineDiff(resultDiff)
    }

    /**
     * Randomly sample a set of variants valid in both commits.
     *
     * @param commit The id of the parent commit
     * @return The sampled variants
     */
    fun sample(commit: SPLCommit): Sample {
        if (currentModel == null || currentCommit !== commit) {
            Logger.debug("Loading feature models.")
            currentCommit = commit
            currentModel = commit.featureModel().run().orElseThrow()
            featureModelDebug(currentModel)
        }
        return sampler.sample(currentModel)
    }

    // Save the features in the feature models
    private fun featureModelDebug(model: IFeatureModel?) {
        if (inDebug) {
            try {
                Files.write(
                    operations.debugDir(currentCommit!!).resolve("features.txt"), model!!.features.stream()
                        .map { obj: IFeature -> obj.name }.collect(Collectors.toSet())
                )
            } catch (e: IOException) {
                Logger.error("Was not able to write commit data:\n{}", e)
            }
        }
    }

    // Generate the two versions of a variant
    private fun generateVariant(
        currentCommit: SPLCommit,
        groundTruthV0: MutableMap<Variant, GroundTruth>,
        groundTruthV1: MutableMap<Variant, GroundTruth>, variant: Variant
    ) {
        Logger.debug("Generating variant " + variant.name)
        if (inDebug && variant.configuration is FeatureIDEConfiguration) {
            val config = variant.configuration as FeatureIDEConfiguration
            val p = operations.debugDir(currentCommit).resolve("configs")
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
        val gtV0 = currentCommit.presenceConditionsBefore().run().orElseThrow {
            NoSuchElementException(
                "%s ; %s ; %s".format(
                    variant,
                    operations.splCopyB, currentCommit
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

        if (inDebug) {
            try {
                val p = operations.debugDir(currentCommit)
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
        val gtV1 = currentCommit.presenceConditionsAfter().run()
            .orElseThrow {
                NoSuchElementException(
                    "%s ; %s ; %s".format(
                        variant,
                        operations.splCopyB, currentCommit
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
        if (inDebug) {
            try {
                val p = operations.debugDir(currentCommit)
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
            Logger.error(
                "Was not able to checkout commits ($parentCommit -> $childCommit) for SPL repository:\n" +
                        "{}", e
            )
            // Try to clean and checkout again
            cleanRepo(parentRepo)
            cleanRepo(childRepo)
            Logger.warn("Retry of commits in SPL repo.")
            parentRepo.checkoutCommit(parentCommit, true)
            childRepo.checkoutCommit(childCommit, true)
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
    private fun saveDiff(fineDiff: OriginalDiff, file: Path) {
        // Save the fine diff to a file
        try {
            Files.write(file, fineDiff.toLines())
        } catch (e: IOException) {
            panic("Was not able to save diff to file $file")
        }
    }

    // Apply a patch file to a target variant using mpatch
    private fun applyMPatch(
        patchFile: Path, sourceVariant: Path, targetVariant: Path, rejectFile: Path
    ): Set<String> {
        val patchCommand = MPatchCommand.Recommended(sourceVariant, patchFile).strip(2)
            .rejectsFile(rejectFile)
        return applyPatch(patchCommand, patchFile, targetVariant, rejectFile, false)
    }

    // Apply a patch file to a target variant using mpatch
    private fun applyMPatch(
        patchFile: Path, sourceVariant: Path, targetVariant: Path, rejectFile: Path, emptyPatch: Boolean
    ): Set<String> {
        val patchCommand = MPatchCommand.Recommended(sourceVariant, patchFile).strip(2)
            .rejectsFile(rejectFile)
        return applyPatch(patchCommand, patchFile, targetVariant, rejectFile, emptyPatch)
    }

    // Apply a patch file to a target variant using Unix patch
    private fun applyPatch(
        patchFile: Path, targetVariant: Path, rejectFile: Path
    ): Set<String> {
        val patchCommand = PatchCommand.Recommended(patchFile).strip(2)
            .rejectFile(rejectFile).force().ignoreWhitespace()
        return applyPatch(patchCommand, patchFile, targetVariant, rejectFile, false)
    }

    // Apply a patch file to a target variant using Unix patch
    private fun applyPatch(
        patchFile: Path, targetVariant: Path, rejectFile: Path, emptyPatch: Boolean
    ): Set<String> {
        val patchCommand = PatchCommand.Recommended(patchFile).strip(2)
            .rejectFile(rejectFile).force().ignoreWhitespace()
        return applyPatch(patchCommand, patchFile, targetVariant, rejectFile, emptyPatch)
    }

    // Apply a patch file to a target variant
    private fun applyPatch(
        patchCommand: ShellCommand,
        patchFile: Path, targetVariant: Path,
        rejectFile: Path, emptyPatch: Boolean
    ): Set<String> {
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
        if (Files.exists(rejectFile)) {
            Logger.debug("Cleaning old rejects file $rejectFile")
            operations.shell.execute(RmCommand(rejectFile))
        }

        // copy target variant
        operations.shell.execute(CpCommand(targetVariant, operations.patchDir).recursive())
            .expect("Was not able to copy variant $targetVariant")

        // apply patch to copied target variant
        val skipped: MutableSet<String> = HashSet()
        if (!emptyPatch) {
            val result = operations.shell.execute(
                patchCommand,
                operations.patchDir
            )
            if (result.isSuccess) {
                result.success.forEach(Consumer { message: String? -> Logger.debug(message) })
            } else {
                val lines = result.failure.output
                Logger.debug("Failed to apply part of patch. See debug log and rejects file for more information")
                var oldFile: String
                for (nextLine in lines) {
                    Logger.debug(nextLine)
                    if (nextLine.startsWith("|---")) {
                        oldFile = nextLine.split("\\s+".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[1]
                        skipped.add(oldFile)
                    }
                }
            }
        }
        return skipped
    }

    private fun getFineDiff(originalDiff: OriginalDiff): FineDiff {
        val contextProvider = DefaultContextProvider(operations.workDir)
        return DiffSplitter.split(originalDiff, contextProvider)
    }

    // Get the filtered line-level patches for a given difference
    private fun getFilteredDiff(
        originalDiff: OriginalDiff,
        tracesV0: Artefact,
        tracesV1: Artefact,
        target: Variant,
        oldVersionRoot: Path,
        newVersionRoot: Path
    ): FineDiff {
        val cachedPCBasedFilter = CachedPCBasedFilter(tracesV0, tracesV1, target, oldVersionRoot, newVersionRoot, 2)
        return getFilteredDiff(originalDiff, cachedPCBasedFilter, false)
    }

    // Get the filtered line-level patches for a given difference
    private fun getRequiredChanges(
        originalDiff: OriginalDiff,
        tracesV0: Artefact,
        tracesV1: Artefact,
        target: Variant,
        oldVersionRoot: Path,
        newVersionRoot: Path
    ): CountingMap<Change> {
        val cachedPCBasedFilter = CachedPCBasedFilter(tracesV0, tracesV1, target, oldVersionRoot, newVersionRoot, 2)
        val fineDiff = getFilteredDiff(originalDiff, cachedPCBasedFilter, true)
        return CountingMap(fineDiff.intoChanges())
    }

    // Get the filtered line-level patches for a given difference
    private fun <T> getFilteredDiff(
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
        v0Path: Path, v1Path: Path
    ): OriginalDiff {
        val diffCommand: DiffCommand = DiffCommand.Recommended(
            operations.workDir.relativize(v0Path),
            operations.workDir.relativize(v1Path)
        )
        val output = operations.shell.execute(diffCommand, operations.workDir)
            .expect("Was not able to diff variants.")
        return DiffParser.toOriginalDiff(output)
    }

    // Abort the program
    private fun panic(message: String) {
        Logger.error(message)
        throw Panic(message)
    }

    // Abort the program
    private fun panic(message: String, e: Exception) {
        Logger.error(message)
        Logger.error(e.message)
        Logger.error(e)
        e.printStackTrace()
        throw Panic(message)
    }

    // Read a rejects file
    private fun readRejects(rejectFile: Path, patch: FineDiff): FineDiff {
        var rejectsDiff: OriginalDiff? = null
        if (Files.exists(rejectFile)) {
            try {
                val rejects = Files.readAllLines(rejectFile)
                rejectsDiff = DiffParser.toOriginalDiff(rejects)
            } catch (e: IOException) {
                panic("Was not able to read rejects file.", e)
            }
        }
        val result: FineDiff =
            if (rejectsDiff == null) {
                FineDiff(ArrayList())
            } else {
                getFineDiff(rejectsDiff)
            }

        if (operations.appliedPatchTracker.hasAnyError()) {
            Logger.error("patch that caused the error: {}", patch.content()[operations.appliedPatchTracker.patchId]);
        }
        if (operations.appliedPatchTracker.hasCriticalError()) {
            // There was a critical error due to a bug in patch
            // We have to read which file caused the error from our tracker, and then add all patches that came afterward
            // to the rejects, because patching was aborted
            val file = operations.appliedPatchTracker.lastPatchTarget()
            var afterError = false
            for (fd in patch.content) {
                if (fd.oldFile.endsWith(file)) {
                    afterError = true;
                }
                if (afterError) {
                    result.content.add(fd)
                }
            }
        }
        if (operations.appliedPatchTracker.hasNormalError()) {
            // A normal error causes only the problematic patch to fail.
            // We can add this patch to the rejects.
            result.content.add(patch.content()[operations.appliedPatchTracker.patchId])
        }
        operations.appliedPatchTracker.reset()
        return result
    }

    private fun splPCDebug() {
        try {
            val v0PCs = currentCommit!!.presenceConditionsBefore().run()
            val p = operations.debugDir(currentCommit!!).resolve("PCs")
            p.toFile().mkdirs()
            if (v0PCs.isPresent) {
                Resources.Instance().write(
                    Artefact::class.java, v0PCs.get(),
                    p.resolve("pcs-parent.spl.csv")
                )
            }
            val v1PCs = currentCommit!!.presenceConditionsAfter().run()
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

    private fun targetFilesNormalDebug(target: Variant, pathToTarget: Path, pathToExpectedResult: Path) {
        operations.shell.execute(
            CpCommand(
                pathToTarget,
                operations.debugDir(currentCommit!!).resolve(target.name).resolve("original")
            ).recursive()
        )
            .expect("Was not able to copy variant $target.name")
        operations.shell.execute(
            CpCommand(
                operations.patchDir,
                operations.debugDir(currentCommit!!).resolve(target.name).resolve("patched_normal")
            ).recursive()
        )
            .expect("Was not able to copy variant $target.name")
        operations.shell.execute(
            CpCommand(
                pathToExpectedResult,
                operations.debugDir(currentCommit!!).resolve(target.name).resolve("expected")
            ).recursive()
        )
            .expect("Was not able to copy variant $target.name")
    }
}
