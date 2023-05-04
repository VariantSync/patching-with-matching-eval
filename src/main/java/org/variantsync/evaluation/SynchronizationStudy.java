package org.variantsync.evaluation;

import de.ovgu.featureide.fm.core.base.IFeatureModel;
import de.ovgu.featureide.fm.core.base.IFeatureModelElement;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.variantsync.evaluation.baseline.diff.filter.DiffFilter;
import org.variantsync.evaluation.baseline.shell.*;
import org.variantsync.functjonal.Result;
import org.variantsync.evaluation.baseline.diff.DiffParser;
import org.variantsync.evaluation.baseline.diff.components.FineDiff;
import org.variantsync.evaluation.baseline.diff.components.OriginalDiff;
import org.variantsync.evaluation.baseline.diff.filter.CachedPCBasedFilter;
import org.variantsync.evaluation.error.Panic;
import org.variantsync.evaluation.error.ShellException;
import org.variantsync.vevos.simulation.feature.Variant;
import org.variantsync.vevos.simulation.feature.config.FeatureIDEConfiguration;
import org.variantsync.vevos.simulation.feature.sampling.FeatureIDESampler;
import org.variantsync.vevos.simulation.feature.sampling.Sample;
import org.variantsync.vevos.simulation.feature.sampling.Sampler;
import org.variantsync.vevos.simulation.io.Resources;
import org.variantsync.vevos.simulation.io.data.VariabilityDatasetLoader;
import org.variantsync.vevos.simulation.repository.SPLRepository;
import org.tinylog.Logger;
import org.variantsync.vevos.simulation.util.io.CaseSensitivePath;
import org.variantsync.vevos.simulation.variability.SPLCommit;
import org.variantsync.vevos.simulation.variability.VariabilityDataset;
import org.variantsync.vevos.simulation.variability.pc.Artefact;
import org.variantsync.vevos.simulation.variability.pc.groundtruth.GroundTruth;
import org.variantsync.vevos.simulation.variability.pc.options.VariantGenerationOptions;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * This class contains the core workflow of our study as described in our paper.
 */
public class SynchronizationStudy {
    // Working directory
    protected final Path workDir;
    // Directory for storing results
    protected final Path resultsDir;
    // Directory for saving debug files
    protected final Path debugDir;
    // Flag whether the study is executed in debug mode
    protected final boolean inDebug;
    // Path to the result file
    protected final Path resultFile;
    // Path to the first copy of the SPL. We require copy to consider different versions
    protected final Path splCopyA;
    // Path to the second copy of the SPL
    protected final Path splCopyB;
    // Path to the directory containing the variants generated for the parent commit
    protected final CaseSensitivePath variantsDirV0;
    // Path to the directory containing the variants generated for the child commit
    protected final CaseSensitivePath variantsDirV1;
    // The directory to which patches are applied. A copy of the target variant is created in this
    // directory.
    protected final Path patchDir;
    // Path to the patch file containing the patches without filtering
    protected final Path normalPatchFile;
    // Path to the patch file containing the patches with filtering
    protected final Path filteredPatchFile;
    // Path to the rejects file created by patching without filtering
    protected final Path rejectsNormalFile;
    // Path to the rejects file created by patching with filtering
    protected final Path rejectsFilteredFile;
    // ShellExecutor for executing shell commands
    protected final ShellExecutor shell;
    // Number of random repetitions for each commit pair. New variants are sampled for each
    // repetition
    protected final int numRepetitions;
    // Number of sampled variants
    protected final int numVariants;
    // The id of the first run that is to be executed. Required for the short installation
    // validation.
    protected final int startID;
    // Name of the experimental subject
    protected final String datasetName;
    // Path to the subject's sources
    protected final Path repositoryPath;
    // Path to the ground truth dataset
    protected final Path groundTruthPath;
    // Evolution history of the subject
    protected final List<SPLCommit> history;
    // The variant sampler
    private final Sampler sampler;
    // The feature model for which variants are sampled
    private IFeatureModel currentModel;
    // The considered commit
    private SPLCommit currentCommit;

    /**
     * Initialize the study from the given configuration
     */
    public SynchronizationStudy(String datasetName, Path mainDir, Path resultsDir,
                    Path repositoryPath, Path groundTruthPath, int numRepetitions, int numVariants,
                    int startID, boolean inDebug) {
        try {
            if (mainDir.toFile().mkdirs()) {
                Logger.info("Created main directory " + mainDir);
            }
            this.workDir = Files.createTempDirectory(mainDir, "workdir");
        } catch (final IOException e) {
            Logger.error("Was not able to initialize this.workDir", e);
            throw new UncheckedIOException(e);
        }
        this.resultsDir = resultsDir;
        this.debugDir = resultsDir.resolve("DEBUG");
        this.inDebug = inDebug;
        this.resultFile = resultsDir.resolve("%s.results".formatted(datasetName));
        this.repositoryPath = repositoryPath;
        this.groundTruthPath = groundTruthPath;
        this.splCopyA = this.workDir.resolve("SPL-A");
        this.splCopyB = this.workDir.resolve("SPL-B");
        this.variantsDirV0 = new CaseSensitivePath(this.workDir.resolve("V0Variants"));
        this.variantsDirV1 = new CaseSensitivePath(this.workDir.resolve("V1Variants"));
        this.patchDir = this.workDir.resolve("TARGET/V0");
        this.normalPatchFile = this.workDir.resolve("patch.txt");
        this.filteredPatchFile = this.workDir.resolve("filtered-patch.txt");
        this.rejectsNormalFile = this.workDir.resolve("rejects-normal.txt");
        this.rejectsFilteredFile = this.workDir.resolve("rejects-filtered.txt");
        this.shell = new ShellExecutor(Logger::debug, Logger::debug, this.workDir);
        this.numRepetitions = numRepetitions;
        this.numVariants = numVariants;
        this.startID = startID;
        this.datasetName = datasetName;
        this.sampler = FeatureIDESampler.CreateRandomSampler(this.numVariants);

        this.history = init();
    }

    // Read a rejects file
    @Nullable
    private static OriginalDiff readRejects(final Path rejectFile) {
        OriginalDiff rejectsDiff = null;
        if (Files.exists(rejectFile)) {
            try {
                final List<String> rejects = Files.readAllLines(rejectFile);
                rejectsDiff = DiffParser.toOriginalDiff(rejects);
            } catch (final IOException e) {
                Logger.error("Was not able to read rejects file.", e);
            }
        }
        return rejectsDiff;
    }

    /**
     * Execute the study.
     */
    public void run() {
        // Initialize the SPL repositories for different versions
        Logger.info("Initializing SPL repos.");
        SPLRepository parentRepo = new SPLRepository(splCopyA);
        SPLRepository childRepo = new SPLRepository(splCopyB);

        // For each pair
        Logger.info("Starting diffing and patching...");
        long runID = 0;
        int commitCount = 0;
        final long historySize = history.size();
        Logger.info("There are " + historySize + " commits to work on.");
        for (final SPLCommit currentCommit : history) {
            // Increase one extra time for the first parent in the sequence
            commitCount++;
            // Skip pairs until the start ID has been reached.
            if (commitCount < startID) {
                Logger.info("Skipped commit " + commitCount);
                continue;
            }
            // We can only process the commit if it has at least one parent
            if (currentCommit.parents().isEmpty()) {
                continue;
            }
            SPLCommit parentCommit = currentCommit.parents().get()[0];

            splRepoPreparation(parentRepo, childRepo, parentCommit, currentCommit);

            // While more random configurations to consider
            for (int i = 0; i < numRepetitions; i++) {
                Logger.info("Starting repetition " + (i + 1) + " of " + numRepetitions + " with "
                                + numVariants + " variants.");
                if (inDebug && Files.exists(debugDir)) {
                    shell.execute(new RmCommand(debugDir).recursive());
                }
                if (inDebug && debugDir.toFile().mkdirs()) {
                    Logger.debug("Created Debug directory.");
                }

                // Sample set of random variants
                Logger.info("Sampling next set of variants...");
                final Sample sample = sample(currentCommit);
                Logger.info("Done. Sampled " + sample.variants().size() + " variants.");

                if (Files.exists(variantsDirV0.path())) {
                    Logger.info("Cleaning variants dir V0.");
                    shell.execute(new RmCommand(variantsDirV0.path()).recursive());
                }
                if (Files.exists(variantsDirV1.path())) {
                    Logger.info("Cleaning variants dir V1.");
                    shell.execute(new RmCommand(variantsDirV1.path()).recursive());
                }

                // Write information about the commits
                if (inDebug) {
                    try {
                        final var v0PCs = currentCommit.presenceConditionsBefore().run();
                        if (v0PCs.isPresent()) {
                            Resources.Instance().write(Artefact.class, v0PCs.get(),
                                            debugDir.resolve("V0.spl.csv"));
                        }

                        final var v1PCs = currentCommit.presenceConditionsAfter().run();
                        if (v1PCs.isPresent()) {
                            Resources.Instance().write(Artefact.class, v1PCs.get(),
                                            debugDir.resolve("V1.spl.csv"));
                        }
                    } catch (final Resources.ResourceIOException e) {
                        panic("Was not able to write PCs", e);
                    }
                }

                // Generate the randomly selected variants at both versions
                final Map<Variant, GroundTruth> groundTruthV0 = new HashMap<>();
                final Map<Variant, GroundTruth> groundTruthV1 = new HashMap<>();
                Logger.info("Generating variants...");
                for (final Variant variant : sample.variants()) {
                    generateVariant(currentCommit, groundTruthV0, groundTruthV1, variant);
                }
                Logger.info("Done.");

                // Select each variant once as source
                Variant source = sample.variants().get(0);
                if (source == null) {
                    continue;
                }
                Logger.info("Starting diff application for source variant " + source.getName());
                if (Files.exists(normalPatchFile)) {
                    Logger.info("Cleaning old patch file " + normalPatchFile);
                    shell.execute(new RmCommand(normalPatchFile));
                }
                // Apply diff to both versions of source variant
                Logger.info("Diffing source...");
                final OriginalDiff originalDiff =
                                getOriginalDiff(variantsDirV0.path().resolve(source.getName()),
                                                variantsDirV1.path().resolve(source.getName()));
                if (originalDiff.isEmpty()) {
                    // There was no change to this variant, so we can skip it as source
                    Logger.info("Skipping " + source.getName()
                                    + " because there are no changes to code. Diff of code files is empty.");
                    continue;
                } else if (inDebug) {
                    try {
                        Files.write(debugDir.resolve("diff.txt"), originalDiff.toLines());
                    } catch (final IOException e) {
                        Logger.error("Was not able to save diff", e);
                    }
                }
                Logger.info("Converting diff...");
                // Convert the original diff into a fine diff
                final FineDiff normalPatch = getFineDiff(originalDiff);
                saveDiff(normalPatch, normalPatchFile);
                Logger.info("Saved fine diff.");

                // For each target variant,
                Logger.info("Starting patch application for source variant " + source.getName());
                for (final Variant target : sample.variants()) {
                    if (target == source) {
                        continue;
                    }
                    runID++;
                    Logger.info(source.getName() + " --patch--> " + target.getName());
                    final Path pathToTarget = variantsDirV0.path().resolve(target.getName());
                    final Path pathToExpectedResult =
                                    variantsDirV1.path().resolve(target.getName());
                    final FineDiff evolutionDiff = getFineDiff(
                                    getOriginalDiff(pathToTarget, pathToExpectedResult));
                    if (inDebug) {
                        saveDiff(evolutionDiff, debugDir.resolve("evolutionDiff.txt"));
                    }

                    /* Application of patches without knowledge about features */
                    Logger.info("Applying patch without knowledge about features...");
                    // Apply the fine diff to the target variant
                    final Set<String> skippedNormal =
                                    applyPatch(normalPatchFile, pathToTarget, rejectsNormalFile);
                    // Evaluate the patch result
                    final FineDiff actualVsExpectedNormal =
                                    getActualVsExpected(pathToExpectedResult);
                    final OriginalDiff rejectsNormal = readRejects(rejectsNormalFile);

                    /* Application of patches with knowledge about PC of edit only */
                    Logger.info("Applying patch with knowledge about edits' PCs...");
                    // Create target variant specific patch that respects PCs
                    final FineDiff filteredPatch = getFilteredDiff(originalDiff,
                                    groundTruthV0.get(source).variant(),
                                    groundTruthV1.get(source).variant(), target,
                                    variantsDirV0.path(), variantsDirV1.path());
                    final boolean emptyPatch = filteredPatch.content().isEmpty();
                    saveDiff(filteredPatch, filteredPatchFile);
                    // Apply the patch
                    final Set<String> skippedFiltered = applyPatch(filteredPatchFile, pathToTarget,
                                    rejectsFilteredFile, emptyPatch);
                    // Evaluate the result
                    final FineDiff actualVsExpectedFiltered =
                                    getActualVsExpected(pathToExpectedResult);
                    final OriginalDiff rejectsFiltered = readRejects(rejectsFilteredFile);

                    /* Result Evaluation */
                    final PatchOutcome patchOutcome = ResultAnalysis.processOutcome(datasetName,
                                    runID, source.getName(), target.getName(), parentCommit,
                                    currentCommit, normalPatch, filteredPatch,
                                    actualVsExpectedNormal, actualVsExpectedFiltered, rejectsNormal,
                                    rejectsFiltered, evolutionDiff, skippedNormal, skippedFiltered);

                    try {
                        patchOutcome.writeAsJSON(resultFile, true);
                    } catch (final IOException e) {
                        Logger.error("Was not able to write filtered patch result file for run "
                                        + runID, e);
                    }

                    Logger.info("Finished patching for source " + source.getName() + " and target "
                                    + target.getName());
                }
            }

            Logger.info(String.format("Finished commit %d of %d.%n", commitCount, historySize));

            // Free memory of parentCommit
            parentCommit.forget();
            // Free memory of commit V1
            currentCommit.forget();
        }
        Logger.info("All done.");
    }

    /**
     * Get the difference between the target variant after patching and the target variant in the
     * next de.variantsync.studies.evolution step. Then, filter all differences that do not belong
     * to the source variant and could have therefore not been synchronized in any case.
     */
    private FineDiff getActualVsExpected(final Path pathToExpectedResult) {
        final OriginalDiff resultDiff = getOriginalDiff(patchDir, pathToExpectedResult);
        if (inDebug) {
            try {
                Files.write(debugDir.resolve("resultDiffOriginal.txt"), resultDiff.toLines());
            } catch (final IOException e) {
                Logger.error("Was not able to save resultDiffOriginal", e);
            }
        }
        FineDiff fineResult = getFineDiff(resultDiff);
        if (inDebug) {
            try {
                Files.write(debugDir.resolve("resultDiffFine.txt"), fineResult.toLines());
            } catch (final IOException e) {
                Logger.error("Was not able to save resultDiffFiltered", e);
            }
        }

        return fineResult;
    }

    /**
     * Randomly sample a set of variants valid in both commits.
     *
     * @param commit The id of the parent commit
     * @param commitV1 The id of the child commit
     * @return The sampled variants
     */
    protected Sample sample(final SPLCommit commit) {
        if (currentModel == null || currentCommit != commit) {
            Logger.info("Loading feature models.");
            currentCommit = commit;
            currentModel = commit.featureModel().run().orElseThrow();
            featureModelDebug(currentModel);
        }
        return sampler.sample(currentModel);
    }

    // Save the features in the feature models
    private void featureModelDebug(final IFeatureModel model) {
        if (inDebug) {
            try {
                Files.write(debugDir.resolve("features.txt"), model.getFeatures().stream()
                                .map(IFeatureModelElement::getName).collect(Collectors.toSet()));
            } catch (final IOException e) {
                Logger.error("Was not able to write commit data.", e);
            }
        }
    }

    // Generate the two versions of a variant
    private void generateVariant(final SPLCommit currentCommit,
                    final Map<Variant, GroundTruth> groundTruthV0,
                    final Map<Variant, GroundTruth> groundTruthV1, final Variant variant) {
        Logger.info("Generating variant " + variant.getName());
        if (inDebug && variant.getConfiguration() instanceof FeatureIDEConfiguration config) {
            try {
                Files.write(debugDir.resolve(variant.getName() + ".config"),
                                config.toAssignment().entrySet().stream().map(
                                                entry -> entry.getKey() + " : " + entry.getValue())
                                                .collect(Collectors.toList()));
            } catch (final IOException e) {
                Logger.error("Was not able to write configuration of " + variant.getName(), e);
            }
        }

        try {
            Files.createDirectories(variantsDirV0.path().resolve(variant.getName()));
            Files.createDirectories(variantsDirV1.path().resolve(variant.getName()));
        } catch (final IOException e) {
            e.printStackTrace();
            panic("Was not able to create directory for variant: " + variant.getName());
        }

        final GroundTruth gtV0 = currentCommit.presenceConditionsBefore().run().orElseThrow()
                        .generateVariant(variant, new CaseSensitivePath(splCopyA),
                                        variantsDirV0.resolve(variant.getName()),
                                        VariantGenerationOptions
                                                        .ExitOnErrorButAllowNonExistentFiles(false,
                                                                        f -> true))
                        .expect("Was not able to generate V0 of " + variant);
        if (inDebug) {
            try {
                Resources.Instance().write(Artefact.class, gtV0.variant(),
                                debugDir.resolve("V0-" + variant.getName() + ".variant.csv"));
            } catch (final Resources.ResourceIOException e) {
                Logger.error("Was not able to write ground truth.");
            }
        }
        groundTruthV0.put(variant, gtV0);

        final GroundTruth gtV1 = currentCommit.presenceConditionsAfter().run()
                        .orElseThrow(() -> new RuntimeException(
                                        "%s ; %s ; %s".formatted(variant, splCopyB, currentCommit)))
                        .generateVariant(variant, new CaseSensitivePath(splCopyB),
                                        variantsDirV1.resolve(variant.getName()),
                                        VariantGenerationOptions
                                                        .ExitOnErrorButAllowNonExistentFiles(false,
                                                                        f -> true))
                        .expect("Was not able to generate V1 of " + variant);
        if (inDebug) {
            try {
                Resources.Instance().write(Artefact.class, gtV1.variant(),
                                debugDir.resolve("V1-" + variant.getName() + ".variant.csv"));
            } catch (final Resources.ResourceIOException e) {
                Logger.error("Was not able to write ground truth.", e);
            }
        }
        groundTruthV1.put(variant, gtV1);
    }

    /**
     * Prepare the two copies of the SPL repository by cleaning them and checking out the next
     * commit pair
     */
    protected void splRepoPreparation(final SPLRepository parentRepo, final SPLRepository childRepo,
                    final SPLCommit parentCommit, final SPLCommit childCommit) {
        Logger.info("Next V0 commit: " + parentCommit);
        Logger.info("Next V1 commit: " + childCommit);
        // Checkout the commits in the SPL repository
        try {
            Logger.info("Checkout of commits in SPL repo.");
            parentRepo.checkoutCommit(parentCommit, true);
            childRepo.checkoutCommit(childCommit, true);

        } catch (final GitAPIException | IOException e) {
            panic("Was not able to checkout commit for SPL repository.", e);
        }
        Logger.info("Done.");
    }


    // Initialize the study by loading the required data
    private List<SPLCommit> init() {
        // Clean old SPL repo files
        Logger.info("Cleaning old repo files.");
        if (Files.exists(splCopyA)) {
            shell.execute(new RmCommand(splCopyA).recursive())
                            .expect("Was not able to remove SPL-V0.");
        }
        if (Files.exists(splCopyB)) {
            shell.execute(new RmCommand(splCopyB).recursive())
                            .expect("Was not able to remove SPL-V1.");
        }
        // Copy the SPL repo
        Logger.info("Creating new SPL repo copies.");
        shell.execute(new CpCommand(repositoryPath, splCopyA).recursive())
                        .expect("Was not able to copy SPL-V0.");
        shell.execute(new CpCommand(repositoryPath, splCopyB).recursive())
                        .expect("Was not able to copy SPL-V1.");


        // Load VariabilityDataset
        Logger.info("Loading variability dataset.");
        VariabilityDataset dataset = null;
        try {
            final Resources instance = Resources.Instance();
            final VariabilityDatasetLoader datasetLoader = new VariabilityDatasetLoader();
            instance.registerLoader(VariabilityDataset.class, datasetLoader);
            dataset = instance.load(VariabilityDataset.class, groundTruthPath);
            Logger.info("Dataset loaded.");
        } catch (final Resources.ResourceIOException e) {
            panic("Was not able to load dataset.", e);
        }

        // Retrieve pairs/sequences of usable commits
        Logger.info("Retrieving commit pairs");
        return Objects.requireNonNull(dataset).getSuccessCommits();
    }

    // Save the difference as a patch file
    private void saveDiff(final FineDiff fineDiff, final Path file) {
        // Save the fine diff to a file
        try {
            Files.write(file, fineDiff.toLines());
        } catch (final IOException e) {
            panic("Was not able to save diff to file " + file);
        }
    }

    // Apply a patch file to a target variant
    private Set<String> applyPatch(final Path patchFile, final Path targetVariant,
                    final Path rejectFile) {
        return applyPatch(patchFile, targetVariant, rejectFile, false);
    }

    // Apply a patch file to a target variant
    private Set<String> applyPatch(final Path patchFile, final Path targetVariant,
                    final Path rejectFile, final boolean emptyPatch) {
        // Clean patch directory
        if (Files.exists(patchDir.toAbsolutePath())) {
            shell.execute(new RmCommand(patchDir.toAbsolutePath()).recursive());
        }

        try {
            Files.createDirectories(patchDir.getParent());
        } catch (IOException e) {
            e.printStackTrace();
            panic("Was not able to create patch directories: ", e);
        }

        if (Files.exists(rejectFile)) {
            Logger.info("Cleaning old rejects file " + rejectFile);
            shell.execute(new RmCommand(rejectFile));
        }

        // copy target variant
        shell.execute(new CpCommand(targetVariant, patchDir).recursive())
                        .expect("Was not able to copy variant " + targetVariant);

        // apply patch to copied target variant
        final Set<String> skipped = new HashSet<>();
        if (!emptyPatch) {
            final Result<List<String>, ShellException> result =
                            shell.execute(PatchCommand.Recommended(patchFile).strip(2)
                                            .rejectFile(rejectFile).force(), patchDir);
            if (result.isSuccess()) {
                result.getSuccess().forEach(Logger::debug);
            } else {
                final List<String> lines = result.getFailure().getOutput();
                Logger.info("Failed to apply part of patch. See debug log and rejects file for more information");
                String oldFile;
                for (final String nextLine : lines) {
                    Logger.debug(nextLine);
                    if (nextLine.startsWith("|---")) {
                        oldFile = nextLine.split("\\s+")[1];
                        skipped.add(oldFile);
                    }
                }
            }
        }
        return skipped;
    }

    @NotNull
    private FineDiff getFineDiff(final OriginalDiff originalDiff) {
        // Simple conversion, we no longer separate line-wise
        return new FineDiff(originalDiff.fileDiffs());
    }

    // Get the filtered patch for a given difference
    private FineDiff getFilteredDiff(final OriginalDiff originalDiff, final Artefact tracesV0,
                    final Artefact tracesV1, final Variant target, Path oldVersionRoot,
                    Path newVersionRoot) {
        final CachedPCBasedFilter cachedPCBasedFilter = new CachedPCBasedFilter(tracesV0, tracesV1,
                        target, oldVersionRoot, newVersionRoot, 2);
        return DiffFilter.filter(originalDiff, cachedPCBasedFilter, cachedPCBasedFilter);
    }

    // Get the difference between two directories using UNIX diff
    protected OriginalDiff getOriginalDiff(final Path v0Path, final Path v1Path) {
        final DiffCommand diffCommand = DiffCommand.Recommended(workDir.relativize(v0Path),
                        this.workDir.relativize(v1Path));
        final List<String> output = shell.execute(diffCommand, this.workDir)
                        .expect("Was not able to diff variants.");
        if (inDebug) {
            try {
                Files.createDirectories(this.debugDir);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            Path file = this.debugDir.resolve("latestDiff.txt");
            try (PrintWriter writer = new PrintWriter(file.toFile())) {
                for (String line : output) {
                    writer.write(line);
                    writer.write("\n");
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return DiffParser.toOriginalDiff(output);
    }

    // Abort the program
    protected void panic(final String message) {
        Logger.error(message);
        throw new Panic(message);
    }

    // Abort the program
    protected void panic(final String message, final Exception e) {
        Logger.error(message, e);
        e.printStackTrace();
        throw new Panic(message);
    }

}
