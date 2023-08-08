package org.variantsync.evaluation;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.variantsync.diffdetective.util.Assert;
import org.variantsync.evaluation.baseline.diff.components.FineDiff;
import org.variantsync.evaluation.baseline.diff.lines.ChangedLine;
import org.tinylog.Logger;

import org.variantsync.evaluation.common.Change;
import org.variantsync.vevos.simulation.variability.SPLCommit;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Performs the result analysis presented in our paper.
 */
public class ResultAnalysis {
    private static final String DIV = "++++++++++++++++++++++++++++++++++++++";
    private static final String LINE_SEP = System.lineSeparator();

    /**
     * Analyze the outcome of applying patches to a target variant
     *
     * @param dataset The considered SPL
     * @param runID The id of the analyzed run
     * @param sourceVariant The source variant's name
     * @param targetVariant The target variant's name
     * @param commitV0 The id of the parent commit
     * @param commitV1 The id of the child commit
     * @param normalPatch The FineDiff with all prepared changes
     * @param filteredPatch The FineDiff with all prepared changes after filtering
     * @param resultDiffNormal The difference between the patched target variant and the expected
     *        result
     * @param resultDiffFiltered The difference between the patched target variant and the expected
     *        result (with filtering)
     * @param rejectsNormal The rejected patches (aka. failed patches) without filtering
     * @param rejectsFiltered The rejected patches (aka. failed patches) with filtering
     * @param targetChanges The difference between the two versions of the target variant
     * @param skippedFilesNormal List of files that were not found by patch and therefore not
     *        patched
     * @param skippedFilesFiltered List of files that were not found by patch and therefore not
     *        patched
     * @return The patch outcome
     */
    public static PatchOutcome processOutcome(final WorkPaths workdir, final boolean inDebug,
            final String dataset, final long runID, final String sourceVariant,
            final String targetVariant, final SPLCommit commitV0, final SPLCommit commitV1,
            final FineDiff normalPatch, final FineDiff filteredPatch,
            final FineDiff resultDiffNormal, final FineDiff resultDiffFiltered,
            FineDiff rejectsNormal, FineDiff rejectsFiltered, final FineDiff targetChanges,
            final Set<String> skippedFilesNormal, final Set<String> skippedFilesFiltered) {
        Logger.debug("Processing outcome for patch process in " + workdir.workDir);
        // evaluate patch rejects
        // number of tried file-level patches
        final int fileNormal = new HashSet<>(normalPatch.content().stream()
                .map(fd -> fd.oldFile().toString()).collect(Collectors.toList())).size();
        // number of tried line-level patches
        final List<ChangedLine> lineNormal = FineDiff.determineChangedLines(normalPatch);
        // number of failed patches
        int fileNormalFailed;
        List<ChangedLine> lineNormalFailed;
        if (rejectsNormal == null) {
            // If there is no rejects file, because all patches were applied successfully
            rejectsNormal = new FineDiff(new ArrayList<>());
        }

        // Determine the number of failed file-level patches (without filtering)
        fileNormalFailed = new HashSet<>(rejectsNormal.content().stream()
                .map(fd -> fd.oldFile().toString()).collect(Collectors.toSet())).size();
        fileNormalFailed += skippedFilesNormal.size();
        Logger.debug(
                "" + fileNormalFailed + " of " + fileNormal + " normal file-sized patches failed.");

        // Determine the number of failed line-level patches (without filtering)
        lineNormalFailed = FineDiff.determineChangedLines(rejectsNormal);
        FineDiff.determineChangedLines(normalPatch).stream()
                .filter(change -> skippedFilesNormal.contains(change.file().toString()))
                .forEach(lineNormalFailed::add);
        Logger.debug(
                "" + lineNormalFailed + " of " + lineNormal + " normal line-sized patches failed");

        // Number of tried file-level patches (with filtering)
        final int fileFiltered = new HashSet<>(filteredPatch.content().stream()
                .map(fd -> fd.oldFile().toString()).collect(Collectors.toList())).size();
        // Number of tried line-level patches (with filtering)
        final List<ChangedLine> lineFiltered = FineDiff.determineChangedLines(filteredPatch);
        // Number of failed patches
        int fileFilteredFailed;
        List<ChangedLine> lineFilteredFailed;
        if (rejectsFiltered == null) {
            // If there is no rejects file, because all patches were applied successfully
            rejectsFiltered = new FineDiff(new ArrayList<>());
        }

        // Determine the number of failed file-level patches (with filtering)
        fileFilteredFailed = new HashSet<>(rejectsFiltered.content().stream()
                .map(fd -> fd.oldFile().toString()).collect(Collectors.toList())).size();
        fileFilteredFailed += skippedFilesFiltered.size();
        Logger.debug("" + fileFilteredFailed + " of " + fileFiltered
                + " filtered file-sized patches failed.");

        // Determine the number of failed line-level patches (with filtering)
        lineFilteredFailed = FineDiff.determineChangedLines(rejectsFiltered);
        FineDiff.determineChangedLines(filteredPatch).stream()
                .filter(change -> skippedFilesFiltered.contains(change.file().toString()))
                .forEach(lineFilteredFailed::add);
        Logger.debug("" + lineFilteredFailed + " of " + lineFiltered
                + " filtered line-sized patches failed");


        final EvaluationScenario scenario = initScenario(normalPatch, targetChanges);
        final EvalResult normalResult = scenario.evaluate(
                new CountingMap<>(normalPatch.intoChanges()),
                new CountingMap<>(rejectsNormal.intoChanges()),
                new CountingMap<>(FineDiff.determineChangedLines(resultDiffNormal)));

        final EvalResult filteredResult = scenario.evaluate(
                new CountingMap<>(filteredPatch.intoChanges()),
                new CountingMap<>(rejectsFiltered.intoChanges()),
                new CountingMap<>(FineDiff.determineChangedLines(resultDiffFiltered)));

        Assert.assertEquals(normalResult.resultCount(), filteredResult.resultCount());
        Assert.assertEquals(normalResult.resultCount(), lineNormal.size());
        Assert.assertEquals(filteredResult.resultCount(), lineNormal.size());

        return new PatchOutcome(dataset, runID, commitV0.id(), commitV1.id(), sourceVariant,
                targetVariant, resultDiffNormal.content().size(),
                resultDiffFiltered.content().size(), fileNormal, lineNormal.size(),
                fileNormal - fileNormalFailed, lineNormal.size() - lineNormalFailed.size(),
                fileFiltered, lineFiltered.size(), fileFiltered - fileFilteredFailed,
                lineFiltered.size() - lineFilteredFailed.size(), normalResult, filteredResult);
    }

    private static EvaluationScenario initScenario(FineDiff unfilteredPatch, FineDiff targetEvolutionDiff) {
        Logger.debug("Calculating result table with TP, FP, TN, and FN.");
        List<Change> changesToClassify = unfilteredPatch.intoChanges();
        List<Change> changesInEvolution = targetEvolutionDiff.intoChanges();

        // Changes in the target variant's evolution that cannot be
        // synchronized, because they are not part of the source variant and therefore not of the
        // patch
        final List<Change> unpatchableChanges = new ArrayList<>();
        // Expected changes, i.e., changes in the target variant's
        // evolution that can be synchronized
        List<Change> requiredChanges = new ArrayList<>();
        {
            final List<Change> tempChanges = new ArrayList<>(changesToClassify);
            for (Change evolutionChange : changesInEvolution) {
                if (!tempChanges.contains(evolutionChange)) {
                    unpatchableChanges.add(evolutionChange);
                } else {
                    requiredChanges.add(evolutionChange);
                    tempChanges.remove(evolutionChange);
                }
            }
        }

        // Determine undesired changes, i.e., changes in the evolution of the source but not the
        // target
        List<Change> undesiredChangesTotal = determineUndesired(changesToClassify, requiredChanges);

        return new EvaluationScenario(new CountingMap<>(requiredChanges), new CountingMap<>(undesiredChangesTotal), new CountingMap<>(unpatchableChanges));
    }

    private static List<Change> determineUndesired(List<Change> changesToClassify,
                                                        List<Change> requiredChanges) {
        final List<Change> undesiredChanges = new ArrayList<>();
        {
            final List<Change> tempChanges = new ArrayList<>(requiredChanges);
            for (Change patchChange : changesToClassify) {
                if (!tempChanges.contains(patchChange)) {
                    undesiredChanges.add(patchChange);
                } else {
                    tempChanges.remove(patchChange);
                }
            }
        }
        return undesiredChanges;
    }

    /**
     * Run the result analysis on the collected results. This method is called by the Docker
     * container after the study has been run.
     *
     * @param args CL arguments
     * @throws IOException If the results cannot be loaded
     */
    public static void main(final String... args) throws IOException {
        if (args.length < 1) {
            System.err.println(
                    "The first argument should provide the path to the configuration file that is to be used");
        }
        final StudyConfiguration config = new StudyConfiguration(new File(args[0]));
        final Path resultsDir = Path.of(config.EXPERIMENT_DIR_RESULTS());
        try (Stream<Path> files = Files.list(resultsDir)) {
            files.filter(f -> {
                String fileName = f.getFileName().toString();
                return fileName.endsWith(".results");
            }).forEach(f -> {
                try {
                    analyze(f);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }

    }

    private static void analyze(Path resultFile) throws IOException {
        String fileName = resultFile.getFileName().getName(0).toString();
        final Path resultSummaryFile =
                resultFile.getParent().resolve("%s.summary".formatted(fileName));

        StringBuilder sb = new StringBuilder();
        final AccumulatedOutcome accumulatedOutcome = loadResultObjects(resultFile);
        sb.append(LINE_SEP);
        sb.append(DIV).append(LINE_SEP);
        sb.append("Patch Success").append(LINE_SEP);
        sb.append(DIV).append(LINE_SEP);
        printTechnicalSuccess(sb, accumulatedOutcome);

        long normalTP = accumulatedOutcome.normalResult().getApplied();
        normalTP += accumulatedOutcome.normalResult().getMitigatedMissing();
        long normalFP = accumulatedOutcome.normalResult().getInvalid();
        long normalTN = accumulatedOutcome.normalResult().getFilteredCorrectly();
        normalTN += accumulatedOutcome.normalResult().getMitigatedInvalid();
        long normalFN = accumulatedOutcome.normalResult().getWrongLocation();
        normalFN += accumulatedOutcome.normalResult().getMissing();

        long filteredTP = accumulatedOutcome.filteredResult().getApplied();
        filteredTP += accumulatedOutcome.filteredResult().getMitigatedMissing();
        long filteredFP = accumulatedOutcome.filteredResult().getInvalid();
        long filteredTN = accumulatedOutcome.filteredResult().getFilteredCorrectly();
        filteredTN += accumulatedOutcome.filteredResult().getMitigatedInvalid();
        long filteredFN = accumulatedOutcome.filteredResult().getWrongLocation();
        filteredFN += accumulatedOutcome.filteredResult().getMissing();


        sb.append(LINE_SEP);
        sb.append(DIV).append(LINE_SEP);
        sb.append("Correctness").append(LINE_SEP);
        sb.append(DIV).append(LINE_SEP);

        sb.append("Without Domain Knowledge").append(LINE_SEP);
        printCorrectness(sb, accumulatedOutcome.normalResult());

        sb.append(LINE_SEP);
        sb.append(DIV).append(LINE_SEP);
        sb.append("With Domain Knowledge").append(LINE_SEP);
        sb.append(LINE_SEP);

        printCorrectness(sb, accumulatedOutcome.filteredResult());

        sb.append(LINE_SEP);
        sb.append(DIV).append(LINE_SEP);
        sb.append("Precision / Recall").append(LINE_SEP);
        sb.append(DIV).append(LINE_SEP);


        sb.append("Without Domain Knowledge").append(LINE_SEP);
        printPrecisionRecall(sb, normalTP, normalFP, normalTN, normalFN);

        sb.append(LINE_SEP);
        sb.append(DIV).append(LINE_SEP);
        sb.append("With Domain Knowledge").append(LINE_SEP);
        sb.append(LINE_SEP);

        printPrecisionRecall(sb, filteredTP, filteredFP, filteredTN, filteredFN);

        sb.append(DIV).append(LINE_SEP);
        sb.append("Accuracy").append(LINE_SEP);
        sb.append(DIV).append(LINE_SEP);

        printAccuracy(sb, normalTP, normalFP, normalTN, normalFN, "Normal");
        printAccuracy(sb, filteredTP, filteredFP, filteredTN, filteredFN, "Filtered");

        sb.append(DIV).append(LINE_SEP);
        System.out.print(sb);
        Files.writeString(resultSummaryFile, sb);
    }

    private static void printAccuracy(StringBuilder sb, long tp, long fp, long tn, long fn,
            String name) {
        long expectedCount = tp + tn;
        long allPositives = tp + fn;
        long allNegative = fp + tn;
        double truePositiveRate = (double) tp / (double) allPositives;
        double trueNegativeRate = (double) tn / (double) allNegative;
        long all = tp + fp + tn + fn;

        sb.append(String.format("%s patching achieved the expected result %d out of %d times", name,
                expectedCount, all)).append(LINE_SEP);
        sb.append(String.format("Accuracy: %s", percentage(expectedCount, all))).append(LINE_SEP);
        sb.append(String.format("Balanced Accuracy: %1.2f",
                ((truePositiveRate + trueNegativeRate) / 2.0))).append(LINE_SEP).append(LINE_SEP);
    }

    private static void printTechnicalSuccess(final StringBuilder sb,
            final AccumulatedOutcome allOutcomes) {
        final long commitPatches = allOutcomes.commitPatches();
        final long commitSuccessNormal = allOutcomes.commitSuccessNormal();
        sb.append(String.format("%d of %d commit-sized patch applications succeeded (%s)",
                commitSuccessNormal, commitPatches, percentage(commitSuccessNormal, commitPatches)))
                .append(LINE_SEP);

        final long fileNormal = allOutcomes.fileNormal;
        final long fileSuccessNormal = allOutcomes.fileSuccessNormal;

        sb.append(String.format("%d of %d file-sized patch applications succeeded (%s)",
                fileSuccessNormal, fileNormal, percentage(fileSuccessNormal, fileNormal)))
                .append(LINE_SEP);

        final long lineNormal = allOutcomes.lineNormal;
        final long lineSuccessNormal = allOutcomes.lineSuccessNormal;
        sb.append(String.format("%d of %d line-sized patch applications succeeded (%s)",
                lineSuccessNormal, lineNormal, percentage(lineSuccessNormal, lineNormal)))
                .append(LINE_SEP);

        // -------------------
        final long lineFiltered = allOutcomes.lineFiltered;
        final long lineSuccessFiltered = allOutcomes.lineSuccessFiltered;
        sb.append(String.format(
                "%d of %d line-sized patch applications succeeded after filtering (%s)%n",
                lineSuccessFiltered, lineFiltered, percentage(lineSuccessFiltered, lineFiltered)))
                .append(LINE_SEP);

    }

    private static void printPrecisionRecall(StringBuilder sb, final long tp, final long fp,
            final long tn, final long fn) {
        final double precision = (double) tp / ((double) tp + fp);
        final double recall = (double) tp / ((double) tp + fn);
        final double f_measure = (2 * precision * recall) / (precision + recall);

        sb.append("TP: ").append(tp).append(LINE_SEP);
        sb.append("FP: ").append(fp).append(LINE_SEP);
        sb.append("TN: ").append(tn).append(LINE_SEP);
        sb.append("FN: ").append(fn).append(LINE_SEP);
        sb.append(String.format("Precision: %1.2f", precision)).append(LINE_SEP);
        sb.append(String.format("Recall: %1.2f", recall)).append(LINE_SEP);
        sb.append(String.format("F-Measure: %1.2f", f_measure)).append(LINE_SEP);
    }

    private static void printCorrectness(StringBuilder sb, AccumulatedResult result) {
        final double correct = result.correctCount();
        final double incorrect = result.incorrectCount();
        final double total = result.resultCount();

        final double correctPerc = 100d * correct / total;
        final double incorrectPerc = 100d * incorrect / total;

        final double appliedP = 100d * (double) result.getApplied() / total;
        final double invalidP = 100d * (double) result.getInvalid() / total;
        final double wrongLocationP = 100d * (double) result.getWrongLocation() / total;
        final double missingP = 100d * (double) result.getMissing() / total;
        final double filteredCorrectlyP = 100d * (double) result.getFilteredCorrectly() / total;
        final double filteredIncorrectlyP = 100d * (double) result.getFilteredIncorrectly() / total;
        final double mitigatedInvalidP = 100d * (double) result.getMitigatedInvalid() / total;
        final double mitigatedMissingP = 100d * (double) result.getMitigatedMissing() / total;


        sb.append(String.format("Correct: %1.2f%%  (%d of %d)", correctPerc, (long) correct, (long) total))
                .append(LINE_SEP);
        sb.append(String.format("Incorrect: %1.2f%% (%d of %d)", incorrectPerc, (long) incorrect, (long) total))
                .append(LINE_SEP);
        sb.append("++ Distribution ++").append(LINE_SEP);
        sb.append(String.format("%1.2f%% applied, %1.2f%% invalid", appliedP, invalidP))
                .append(LINE_SEP);
        sb.append(String.format("%1.2f%% invalid, %1.2f%% wrong location",
                missingP, wrongLocationP)).append(LINE_SEP);
        sb.append(String.format("%1.2f%% filtered correctly, %1.2f%% filtered incorrectly", filteredCorrectlyP, filteredIncorrectlyP))
                .append(LINE_SEP);
        sb.append(String.format("%1.2f%% mitigated invalid, %1.2f%% mitigated missing", mitigatedInvalidP, mitigatedMissingP))
                .append(LINE_SEP);
    }


    public static AccumulatedOutcome loadResultObjects(final Path path) throws IOException {
        long commitPatches = 0;
        long commitSuccessNormal = 0;
        long commitSuccessFiltered = 0;

        long fileNormal = 0;
        long fileFiltered = 0;
        long fileSuccessNormal = 0;
        long fileSuccessFiltered = 0;

        long lineNormal = 0;
        long lineFiltered = 0;
        long lineSuccessNormal = 0;
        long lineSuccessFiltered = 0;

        AccumulatedResult accumulatedNormal = new AccumulatedResult();
        AccumulatedResult accumulatedFiltered = new AccumulatedResult();

        try (BufferedReader reader = Files.newBufferedReader(path)) {
            List<String> outcomeLines = new ArrayList<>();
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                if (line.isEmpty()) {
                    PatchOutcome outcome = parseResult(outcomeLines);
                    accumulatedNormal.add(outcome.getNormalResult());
                    accumulatedFiltered.add(outcome.getFilteredResult());

                    commitPatches++;
                    if (outcome.getLineSuccessNormal() == outcome.getLineNormal()) {
                        commitSuccessNormal++;
                    }
                    if (outcome.getLineSuccessFiltered() == outcome.getLineFiltered()) {
                        commitSuccessFiltered++;
                    }

                    fileNormal += outcome.getFileNormal();
                    fileSuccessNormal += outcome.getFileSuccessNormal();
                    fileFiltered += outcome.getFileFiltered();
                    fileSuccessFiltered += outcome.getFileSuccessFiltered();

                    lineNormal += outcome.getLineNormal();
                    lineSuccessNormal += outcome.getLineSuccessNormal();
                    lineFiltered += outcome.getLineFiltered();
                    lineSuccessFiltered += outcome.getLineSuccessFiltered();

                    outcomeLines.clear();
                } else {
                    outcomeLines.add(line);
                }
            }
        }

        System.out.printf("Read a total of %d results.", commitPatches);

        return new AccumulatedOutcome(accumulatedNormal, accumulatedFiltered,
                commitPatches, commitSuccessNormal, commitSuccessFiltered, fileNormal, fileFiltered,
                fileSuccessNormal, fileSuccessFiltered, lineNormal, lineFiltered, lineSuccessNormal,
                lineSuccessFiltered);
    }

    public static PatchOutcome parseResult(final List<String> lines) {
        final Gson gson = new Gson();
        final StringBuilder sb = new StringBuilder();
        lines.forEach(l -> sb.append(l).append("\n"));
        final JsonObject object = gson.fromJson(sb.toString(), JsonObject.class);
        return PatchOutcome.FromJSON(object);
    }

    public static String percentage(final long x, final long y) {
        final double percentage;
        if (y == 0) {
            percentage = 0;
        } else {
            percentage = 100 * ((double) x / (double) y);
        }
        return String.format("%3.1f%s", percentage, "%");
    }

    private record AccumulatedOutcome(AccumulatedResult normalResult, AccumulatedResult filteredResult, long commitPatches,
            long commitSuccessNormal, long commitSuccessFiltered, long fileNormal,
            long fileFiltered, long fileSuccessNormal, long fileSuccessFiltered, long lineNormal,
            long lineFiltered, long lineSuccessNormal, long lineSuccessFiltered) {
    }
}
