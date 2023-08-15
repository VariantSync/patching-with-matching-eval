package org.variantsync.evaluation

import de.ovgu.featureide.fm.core.base.IFeature
import de.ovgu.featureide.fm.core.base.IFeatureModel
import org.eclipse.jgit.api.errors.GitAPIException
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
import org.variantsync.evaluation.baseline.shell.CpCommand
import org.variantsync.evaluation.baseline.shell.DiffCommand
import org.variantsync.evaluation.baseline.shell.PatchCommand
import org.variantsync.evaluation.baseline.shell.RmCommand
import org.variantsync.evaluation.common.Change
import org.variantsync.evaluation.error.Panic
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
import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Consumer
import java.util.stream.Collectors

class Task(
    datasetName: String, mainDir: Path, repositoryPath: Path,
    resultsFile: Path, commits: List<SPLCommit>, numRepetitions: Int, numVariants: Int,
    inDebug: Boolean, idProvider: IDProvider
) : Runnable {
    private val workdir: WorkPaths
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
        workdir = WorkPaths(mainDir)
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
        initializeSPLCopies(workdir)
        val parentRepo = SPLRepository(workdir.splCopyA)
        val childRepo = SPLRepository(workdir.splCopyB)
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
            splRepoPreparation(parentRepo, childRepo, parentCommit, currentCommit)

            // While more random configurations to consider
            for (i in 0 until numRepetitions) {
                Logger.debug(
                    "Starting repetition " + (i + 1) + " of " + numRepetitions + " with "
                            + numVariants + " variants."
                )
                if (inDebug && Files.exists(workdir.debugDir)) {
                    workdir.shell.execute(RmCommand(workdir.debugDir).recursive())
                }
                if (inDebug && workdir.debugDir.toFile().mkdirs()) {
                    Logger.debug("Created Debug directory.")
                }

                // Sample set of random variants
                Logger.debug("Sampling next set of variants...")
                val sample = sample(currentCommit)
                Logger.debug("Done. Sampled " + sample.variants().size + " variants.")
                if (Files.exists(workdir.variantsDirV0.path())) {
                    Logger.debug("Cleaning variants dir V0.")
                    workdir.shell.execute(RmCommand(workdir.variantsDirV0.path()).recursive())
                }
                if (Files.exists(workdir.variantsDirV1.path())) {
                    Logger.debug("Cleaning variants dir V1.")
                    workdir.shell.execute(RmCommand(workdir.variantsDirV1.path()).recursive())
                }

                // Write information about the commits
                if (inDebug) {
                    try {
                        val v0PCs = currentCommit.presenceConditionsBefore().run()
                        if (v0PCs.isPresent) {
                            Resources.Instance().write(
                                Artefact::class.java, v0PCs.get(),
                                workdir.debugDir.resolve("V0.spl.csv")
                            )
                        }
                        val v1PCs = currentCommit.presenceConditionsAfter().run()
                        if (v1PCs.isPresent) {
                            Resources.Instance().write(
                                Artefact::class.java, v1PCs.get(),
                                workdir.debugDir.resolve("V1.spl.csv")
                            )
                        }
                    } catch (e: Resources.ResourceIOException) {
                        panic("Was not able to write PCs", e)
                    }
                }

                // Generate the randomly selected variants at both versions
                val groundTruthV0: MutableMap<Variant, GroundTruth> = HashMap()
                val groundTruthV1: MutableMap<Variant, GroundTruth> = HashMap()
                Logger.debug("Generating variants...")
                for (variant in sample.variants()) {
                    generateVariant(currentCommit, groundTruthV0, groundTruthV1, variant, workdir)
                }
                Logger.debug("Done.")

                // Select each variant once as source
                val source = sample.variants()[0] ?: continue
                Logger.debug("Starting diff application for source variant " + source.name)
                if (Files.exists(workdir.normalPatchFile)) {
                    Logger.debug("Cleaning old patch file " + workdir.normalPatchFile)
                    workdir.shell.execute(RmCommand(workdir.normalPatchFile))
                }
                // Apply diff to both versions of source variant
                Logger.debug("Diffing source...")
                val originalDiff = getOriginalDiff(
                    workdir.variantsDirV0.path().resolve(source.name),
                    workdir.variantsDirV1.path().resolve(source.name), workdir
                )
                if (originalDiff.isEmpty) {
                    // There was no change to this variant, so we can skip it as source
                    Logger.debug(
                        "Skipping " + source.name
                                + " because there are no changes to code. Diff of code files is empty."
                    )
                    continue
                } else if (inDebug) {
                    try {
                        Files.write(workdir.debugDir.resolve("diff.txt"), originalDiff.toLines())
                    } catch (e: IOException) {
                        Logger.error("Was not able to save diff", e)
                    }
                }
                Logger.debug("Converting diff...")
                // Convert the original diff into a fine diff
                val normalPatch = getFineDiff(originalDiff)
                saveDiff(normalPatch, workdir.normalPatchFile)
                Logger.debug("Saved fine diff.")

                // For each target variant,
                Logger.debug("Starting patch application for source variant " + source.name)
                for (target in sample.variants()) {
                    if (target === source) {
                        continue
                    }
                    Logger.debug(source.name + " --patch--> " + target.name)
                    val pathToTarget = workdir.variantsDirV0.path().resolve(target.name)
                    val pathToExpectedResult = workdir.variantsDirV1.path().resolve(target.name)
                    val evolutionDiff = getFineDiff(
                        getOriginalDiff(pathToTarget, pathToExpectedResult, workdir)
                    )
                    if (inDebug) {
                        saveDiff(evolutionDiff, workdir.debugDir.resolve("evolutionDiff.txt"))
                    }

                    /* Application of patches without knowledge about features */Logger.debug("Applying patch without knowledge about features...")
                    // Apply the fine diff to the target variant
                    val skippedNormal = applyPatch(
                        workdir.normalPatchFile,
                        pathToTarget, workdir.rejectsNormalFile, workdir
                    )
                    // Evaluate the patch result
                    val actualVsExpectedNormal = getActualVsExpected(workdir, pathToExpectedResult, "normal")
                    val rejectsNormal = readRejects(workdir.rejectsNormalFile)

                    /* Application of patches with knowledge about PC of edit only */Logger.debug("Applying patch with knowledge about edits' PCs...")
                    // Create target variant specific patch that respects PCs
                    val filteredPatch = getFilteredDiff(
                        originalDiff,
                        groundTruthV0[source]!!.variant(),
                        groundTruthV1[source]!!.variant(), target,
                        workdir.variantsDirV0.path(), workdir.variantsDirV1.path()
                    )
                    val emptyPatch = filteredPatch.content.isEmpty()
                    saveDiff(filteredPatch, workdir.filteredPatchFile)
                    // Apply the patch
                    val skippedFiltered = applyPatch(
                        workdir.filteredPatchFile,
                        pathToTarget, workdir.rejectsFilteredFile, emptyPatch, workdir
                    )
                    // Evaluate the result
                    val actualVsExpectedFiltered = getActualVsExpected(workdir, pathToExpectedResult, "filtered")
                    val rejectsFiltered = readRejects(workdir.rejectsFilteredFile)
                    val requiredChanges = getRequiredChanges(originalDiff,
                        groundTruthV0[source]!!.variant(),
                        groundTruthV1[source]!!.variant(), target,
                        workdir.variantsDirV0.path(), workdir.variantsDirV1.path()
                    )

                    /* Result Evaluation */
                    val patchOutcome = ResultAnalysis.processOutcome(
                        workdir,
                        datasetName, runID, source.name, target.name,
                        parentCommit, currentCommit, normalPatch, filteredPatch,
                        requiredChanges,
                        actualVsExpectedNormal, actualVsExpectedFiltered, rejectsNormal,
                        rejectsFiltered, evolutionDiff, skippedNormal, skippedFiltered
                    )
                    try {
                        patchOutcome.writeAsJSON(resultsFile, true)
                    } catch (e: IOException) {
                        Logger.error(
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
            if (numProcessed % 100uL == 0uL) {
                Logger.info(String.format("Finished commit %s of %s.%n", numProcessed.toString(), numCommits.toString()))
            }

            // Free memory of parentCommit
            parentCommit.forget()
            // Free memory of commit V1
            currentCommit.forget()
        }
    }

    private fun initializeSPLCopies(workdir: WorkPaths) {
        // Clean old SPL repo files
        Logger.debug("Cleaning old repo files.")
        if (Files.exists(workdir.splCopyA)) {
            workdir.shell.execute(RmCommand(workdir.splCopyA).recursive())
                .expect("Was not able to remove SPL-V0.")
        }
        if (Files.exists(workdir.splCopyB)) {
            workdir.shell.execute(RmCommand(workdir.splCopyB).recursive())
                .expect("Was not able to remove SPL-V1.")
        }
        // Copy the SPL repo
        Logger.debug("Creating new SPL repo copies.")
        workdir.shell.execute(CpCommand(repositoryPath, workdir.splCopyA).recursive())
            .expect("Was not able to copy SPL-V0.")
        workdir.shell.execute(CpCommand(repositoryPath, workdir.splCopyB).recursive())
            .expect("Was not able to copy SPL-V1.")
    }

    /**
     * Get the difference between the target variant after patching and the target variant in the
     * next de.variantsync.studies.evolution step. Then, filter all differences that do not belong
     * to the source variant and could have therefore not been synchronized in any case.
     */
    private fun getActualVsExpected(workdir: WorkPaths, pathToExpectedResult: Path, filePostfix: String): FineDiff {
        val resultDiff = getOriginalDiff(workdir.patchDir, pathToExpectedResult, workdir)
        if (inDebug) {
            try {
                Files.write(
                    workdir.debugDir.resolve("resultDiffOriginal-$filePostfix.txt"),
                    resultDiff.toLines()
                )
            } catch (e: IOException) {
                Logger.error("Was not able to save resultDiffOriginal", e)
            }
        }
        val fineResult = getFineDiff(resultDiff)
        if (inDebug) {
            try {
                Files.write(workdir.debugDir.resolve("resultDiffFine-$filePostfix.txt"), fineResult.toLines())
            } catch (e: IOException) {
                Logger.error("Was not able to save resultDiffFiltered", e)
            }
        }
        return fineResult
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
                Files.write(workdir.debugDir.resolve("features.txt"), model!!.features.stream()
                    .map { obj: IFeature -> obj.name }.collect(Collectors.toSet())
                )
            } catch (e: IOException) {
                Logger.error("Was not able to write commit data.", e)
            }
        }
    }

    // Generate the two versions of a variant
    private fun generateVariant(
        currentCommit: SPLCommit,
        groundTruthV0: MutableMap<Variant, GroundTruth>,
        groundTruthV1: MutableMap<Variant, GroundTruth>, variant: Variant,
        workdir: WorkPaths
    ) {
        Logger.debug("Generating variant " + variant.name)
        if (inDebug && variant.configuration is FeatureIDEConfiguration) {
            val config = variant.configuration as FeatureIDEConfiguration
            try {
                Files.write(
                    workdir.debugDir.resolve(variant.name + ".config"),
                    config.toAssignment().entries.stream().map { (key, value): Map.Entry<Any, Boolean> -> "$key : $value" }
                        .collect(Collectors.toList())
                )
            } catch (e: IOException) {
                Logger.error("Was not able to write configuration of " + variant.name, e)
            }
        }
        try {
            Files.createDirectories(workdir.variantsDirV0.path().resolve(variant.name))
            Files.createDirectories(workdir.variantsDirV1.path().resolve(variant.name))
        } catch (e: IOException) {
            e.printStackTrace()
            panic("Was not able to create directory for variant: " + variant.name)
        }
        val gtV0 = currentCommit.presenceConditionsBefore().run().orElseThrow()
            .generateVariant(variant, CaseSensitivePath(workdir.splCopyA),
                workdir.variantsDirV0.resolve(variant.name),
                VariantGenerationOptions
                    .ExitOnErrorButAllowNonExistentFiles(
                        false
                    ) { _: SourceCodeFile? -> true })
            .expect("Was not able to generate V0 of $variant")
        if (inDebug) {
            try {
                Resources.Instance().write(
                    Artefact::class.java, gtV0.variant(), workdir.debugDir
                        .resolve("V0-" + variant.name + ".variant.csv")
                )
            } catch (e: Resources.ResourceIOException) {
                Logger.error("Was not able to write ground truth.")
            }
        }
        groundTruthV0[variant] = gtV0
        val gtV1 = currentCommit.presenceConditionsAfter().run()
            .orElseThrow {
                RuntimeException(
                    "%s ; %s ; %s".format(
                        variant,
                        workdir.splCopyB, currentCommit
                    )
                )
            }
            .generateVariant(variant, CaseSensitivePath(workdir.splCopyB),
                workdir.variantsDirV1.resolve(variant.name),
                VariantGenerationOptions
                    .ExitOnErrorButAllowNonExistentFiles(
                        false
                    ) { _: SourceCodeFile? -> true })
            .expect("Was not able to generate V1 of $variant")
        if (inDebug) {
            try {
                Resources.Instance().write(
                    Artefact::class.java, gtV1.variant(), workdir.debugDir
                        .resolve("V1-" + variant.name + ".variant.csv")
                )
            } catch (e: Resources.ResourceIOException) {
                Logger.error("Was not able to write ground truth.", e)
            }
        }
        groundTruthV1[variant] = gtV1
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
        } catch (e: GitAPIException) {
            panic("Was not able to checkout commits ($parentCommit -> $childCommit) for SPL repository.", e)
        } catch (e: IOException) {
            panic("Was not able to checkout commits ($parentCommit -> $childCommit) for SPL repository.", e)
        }
        Logger.debug("Done.")
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

    // Apply a patch file to a target variant
    private fun applyPatch(
        patchFile: Path, targetVariant: Path,
        rejectFile: Path, workdir: WorkPaths
    ): Set<String> {
        return applyPatch(patchFile, targetVariant, rejectFile, false, workdir)
    }

    // Apply a patch file to a target variant
    private fun applyPatch(
        patchFile: Path, targetVariant: Path,
        rejectFile: Path, emptyPatch: Boolean, workdir: WorkPaths
    ): Set<String> {
        // Clean patch directory
        if (Files.exists(workdir.patchDir.toAbsolutePath())) {
            workdir.shell.execute(RmCommand(workdir.patchDir.toAbsolutePath()).recursive())
        }
        try {
            Files.createDirectories(workdir.patchDir.parent)
        } catch (e: IOException) {
            e.printStackTrace()
            panic("Was not able to create patch directories: ", e)
        }
        if (Files.exists(rejectFile)) {
            Logger.debug("Cleaning old rejects file $rejectFile")
            workdir.shell.execute(RmCommand(rejectFile))
        }

        // copy target variant
        workdir.shell.execute(CpCommand(targetVariant, workdir.patchDir).recursive())
            .expect("Was not able to copy variant $targetVariant")

        // apply patch to copied target variant
        val skipped: MutableSet<String> = HashSet()
        if (!emptyPatch) {
            val result = workdir.shell.execute(
                PatchCommand.Recommended(patchFile).strip(2)
                    .rejectFile(rejectFile).force(),
                workdir.patchDir
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
        val contextProvider = DefaultContextProvider(workdir.workDir)
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
        val contextProvider: IContextProvider = DefaultContextProvider(workdir.workDir, filterDisabled)
        return DiffSplitter.split(originalDiff, filter, filter, contextProvider)
    }

    // Get the difference between two directories using UNIX diff
    private fun getOriginalDiff(
        v0Path: Path, v1Path: Path,
        workdir: WorkPaths
    ): OriginalDiff {
        val diffCommand: DiffCommand = DiffCommand.Recommended(
            workdir.workDir.relativize(v0Path),
            workdir.workDir.relativize(v1Path)
        )
        val output = workdir.shell.execute(diffCommand, workdir.workDir)
            .expect("Was not able to diff variants.")
        if (inDebug) {
            try {
                Files.createDirectories(workdir.debugDir)
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
            val file = workdir.debugDir.resolve("latestDiff.txt")
            try {
                PrintWriter(file.toFile()).use { writer ->
                    for (line in output) {
                        writer.write(line)
                        writer.write("\n")
                    }
                }
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
        }
        return DiffParser.toOriginalDiff(output)
    }

    // Abort the program
    private fun panic(message: String) {
        Logger.error(message)
        throw Panic(message)
    }

    // Abort the program
    private fun panic(message: String, e: Exception) {
        Logger.error(message, e)
        e.printStackTrace()
        throw Panic(message)
    }

    // Read a rejects file
    private fun readRejects(rejectFile: Path): FineDiff {
        var rejectsDiff: OriginalDiff? = null
        if (Files.exists(rejectFile)) {
            try {
                val rejects = Files.readAllLines(rejectFile)
                rejectsDiff = DiffParser.toOriginalDiff(rejects)
            } catch (e: IOException) {
                Logger.error("Was not able to read rejects file.", e)
            }
        }
        val result: FineDiff =
        if (rejectsDiff == null) {
            FineDiff(ArrayList())
        } else {
            getFineDiff(rejectsDiff)
        }
        return result
    }
}
