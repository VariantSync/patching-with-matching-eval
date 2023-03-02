package org.variantsync.evaluation.experiment;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;
import org.variantsync.evaluation.baseline.diff.components.FineDiff;
import org.variantsync.evaluation.baseline.diff.components.OriginalDiff;
import org.variantsync.evaluation.baseline.diff.lines.AddedLine;
import org.variantsync.evaluation.baseline.diff.lines.Change;
import org.variantsync.evaluation.baseline.diff.lines.RemovedLine;
import org.variantsync.vevos.simulation.util.Logger;
import org.variantsync.vevos.simulation.variability.SPLCommit;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Performs the result analysis presented in our paper.
 */
public class ResultAnalysis {
    private static final String DIV = "++++++++++++++++++++++++++++++++++++++";
    private static final String LINE_SEP = System.lineSeparator();

    /**
     * Analyze the outcome of applying patches to a target variant
     *
     * @param dataset              The considered SPL
     * @param runID                The id of the analyzed run
     * @param sourceVariant        The source variant's name
     * @param targetVariant        The target variant's name
     * @param commitV0             The id of the parent commit
     * @param commitV1             The id of the child commit
     * @param normalPatch          The FineDiff with all prepared changes
     * @param filteredPatch        The FineDiff with all prepared changes after filtering
     * @param resultDiffNormal     The difference between the patched target variant and the expected result
     * @param resultDiffFiltered   The difference between the patched target variant and the expected result (with filtering)
     * @param rejectsNormal        The rejected patches (aka. failed patches) without filtering
     * @param rejectsFiltered      The rejected patches (aka. failed patches) with filtering
     * @param sourceChanges        The difference between the two versions of the source variant
     * @param skippedFilesNormal   List of files that were not found by patch and therefore not patched
     * @param skippedFilesFiltered List of files that were not found by patch and therefore not patched
     * @return The patch outcome
     */
    public static PatchOutcome processOutcome(final String dataset,
                                              final long runID,
                                              final String sourceVariant,
                                              final String targetVariant,
                                              final SPLCommit commitV0, final SPLCommit commitV1,
                                              final FineDiff normalPatch, final FineDiff filteredPatch,
                                              final FineDiff resultDiffNormal, final FineDiff resultDiffFiltered,
                                              OriginalDiff rejectsNormal, OriginalDiff rejectsFiltered,
                                              final FineDiff sourceChanges,
                                              final Set<String> skippedFilesNormal,
                                              final Set<String> skippedFilesFiltered) {
        // evaluate patch rejects
        // number of tried file-level patches
        final int fileNormal = new HashSet<>(normalPatch.content().stream().map(fd -> fd.oldFile().toString()).collect(Collectors.toList())).size();
        // number of tried line-level patches
        final int lineNormal = normalPatch.content().stream().mapToInt(fd -> fd.hunks().size()).sum();
        // number of failed patches
        int fileNormalFailed;
        int lineNormalFailed;
        if (rejectsNormal == null) {
            // If there is no rejects file, because all patches were applied successfully
            rejectsNormal = new OriginalDiff(new LinkedList<>());
        }

        // Determine the number of failed file-level patches (without filtering)
        fileNormalFailed = new HashSet<>(rejectsNormal.fileDiffs().stream().map(fd -> fd.oldFile().toString()).collect(Collectors.toSet())).size();
        fileNormalFailed += skippedFilesNormal.size();
        Logger.info("" + fileNormalFailed + " of " + fileNormal + " normal file-sized patches failed.");

        // Determine the number of failed line-level patches (without filtering)
        lineNormalFailed = rejectsNormal.fileDiffs().stream().mapToInt(fd -> fd.hunks().size()).sum();
        lineNormalFailed += normalPatch.content().stream().filter(fd -> skippedFilesNormal.contains(fd.oldFile().toString())).mapToInt(fd -> fd.hunks().size()).sum();
        Logger.info("" + lineNormalFailed + " of " + lineNormal + " normal line-sized patches failed");

        // Number of tried file-level patches (with filtering)
        final int fileFiltered = new HashSet<>(filteredPatch.content().stream().map(fd -> fd.oldFile().toString()).collect(Collectors.toList())).size();
        // Number of tried line-level patches (with filtering)
        final int lineFiltered = filteredPatch.content().stream().mapToInt(fd -> fd.hunks().size()).sum();
        // Number of failed patches
        int fileFilteredFailed;
        int lineFilteredFailed;
        if (rejectsFiltered == null) {
            // If there is no rejects file, because all patches were applied successfully
            rejectsFiltered = new OriginalDiff(new LinkedList<>());
        }

        // Determine the number of failed file-level patches (with filtering)
        fileFilteredFailed = new HashSet<>(rejectsFiltered.fileDiffs().stream().map(fd -> fd.oldFile().toString()).collect(Collectors.toList())).size();
        fileFilteredFailed += skippedFilesFiltered.size();
        Logger.info("" + fileFilteredFailed + " of " + fileFiltered + " filtered file-sized patches failed.");

        // Determine the number of failed line-level patches (with filtering)
        lineFilteredFailed = rejectsFiltered.fileDiffs().stream().mapToInt(fd -> fd.hunks().size()).sum();
        lineFilteredFailed += filteredPatch.content().stream().filter(fd -> skippedFilesFiltered.contains(fd.oldFile().toString())).mapToInt(fd -> fd.hunks().size()).sum();
        Logger.info("" + lineFilteredFailed + " of " + lineFiltered + " filtered line-sized patches failed");

        // Calculate the condition table (without filtering): true positives, false positive, true negatives, and false negatives
        final ConditionTable normalConditionTable = calculateConditionTable(normalPatch, normalPatch, resultDiffNormal, sourceChanges);
        final long normalTP = normalConditionTable.tpCount();
        final long normalFP = normalConditionTable.fpCount();
        final long normalTN = normalConditionTable.tnCount();
        final long normalFN = normalConditionTable.fnCount();
        // Number of line-level patches applied to the wrong location
        final long normalWrongLocation = normalTN + normalFN - lineNormalFailed;

        // Calculate the condition table (with filtering): true positives, false positive, true negatives, and false negatives
        final ConditionTable filteredConditionTable = calculateConditionTable(filteredPatch, normalPatch, resultDiffFiltered, sourceChanges);
        final long filteredTP = filteredConditionTable.tpCount();
        final long filteredFP = filteredConditionTable.fpCount();
        final long filteredTN = filteredConditionTable.tnCount();
        final long filteredFN = filteredConditionTable.fnCount();
        // Number of line-level patches applied to the wrong location
        final long filteredWrongLocation = filteredTN + filteredFN - (/*filtered lines*/ lineNormal - lineFiltered) - lineFilteredFailed;

        // Some sanity checks
        assert normalTP + normalFP + normalFN + normalTN == filteredTP + filteredFP + filteredTN + filteredFN;
        assert normalTN + normalFN - normalWrongLocation <= lineNormalFailed;
        assert filteredTP + filteredFP + filteredFN + filteredTN == lineFiltered + (lineNormal - lineFiltered);

        return new PatchOutcome(dataset,
                runID,
                commitV0.id(),
                commitV1.id(),
                sourceVariant,
                targetVariant,
                resultDiffNormal.content().size(),
                resultDiffFiltered.content().size(),
                fileNormal,
                lineNormal,
                fileNormal - fileNormalFailed,
                lineNormal - lineNormalFailed,
                fileFiltered,
                lineFiltered,
                fileFiltered - fileFilteredFailed,
                lineFiltered - lineFilteredFailed,
                normalTP,
                normalFP,
                normalTN,
                normalFN,
                normalWrongLocation,
                filteredTP,
                filteredFP,
                filteredTN,
                filteredFN,
                filteredWrongLocation);
    }

    // Calculate true positives, false positives, true negatives, and false negatives
    private static ConditionTable calculateConditionTable(FineDiff evaluatedPatch, FineDiff unfilteredPatch, FineDiff resultDiff, FineDiff evolutionDiff) {
        List<Change> changesInPatch = FineDiff.determineChangedLines(evaluatedPatch);
        List<Change> changesToClassify = FineDiff.determineChangedLines(unfilteredPatch);
        List<Change> changesInResult = FineDiff.determineChangedLines(resultDiff);
        List<Change> changesInEvolution = FineDiff.determineChangedLines(evolutionDiff);

        // Determine changes in the target variant's de.variantsync.studies.evolution that cannot be synchronized, because they are not part of the source variant and therefore not of the patch
        final List<Change> unpatchableChanges = new LinkedList<>();
        // Determine expected changes, i.e., changes in the target variant's de.variantsync.studies.evolution that can be synchronized
        final List<Change> requiredChanges = new LinkedList<>();
        {
            final List<Change> tempChanges = new LinkedList<>(changesToClassify);
            for (Change evolutionChange : changesInEvolution) {
                if (!tempChanges.contains(evolutionChange)) {
                    unpatchableChanges.add(evolutionChange);
                } else {
                    requiredChanges.add(evolutionChange);
                    tempChanges.remove(evolutionChange);
                }
            }
        }

        // Determine undesired changes, i.e., changes in the patch but not de.variantsync.studies.evolution
        final List<Change> undesiredChanges = new LinkedList<>();
        {
            final List<Change> tempChanges = new LinkedList<>(requiredChanges);
            for (Change patchChange : changesToClassify) {
                if (!tempChanges.contains(patchChange)) {
                    undesiredChanges.add(patchChange);
                } else {
                    tempChanges.remove(patchChange);
                }
            }
        }

        // Determine actual differences between result and expected result,
        // i.e., changes that should have been synchronized but were not, or changes that should not have been synchronized
        List<Change> actualDifferences = new LinkedList<>(changesInResult);
        unpatchableChanges.forEach(actualDifferences::remove);

        assert changesToClassify.size() >= changesInPatch.size();
        assert changesInEvolution.size() - changesToClassify.size() <= unpatchableChanges.size();
        assert changesInEvolution.size() - unpatchableChanges.size() <= changesToClassify.size();

        // We first want to account for the remaining differences between the actual and the expected result. They are either
        // false positives, i.e. changes in the patch that were applied but should not have been, or the mirror change
        // of a false negative that was applied to the wrong location.
        List<Change> remainingDifferences = new LinkedList<>();
        List<Change> fpChanges = new LinkedList<>();
        for (Change actualDifference : actualDifferences) {
            Change oppositeChange = getOppositeChange(actualDifference);
            if (undesiredChanges.contains(oppositeChange)) {
                // The patch contained a false positive, as the difference was not expected.
                changesInPatch.remove(oppositeChange);
                changesToClassify.remove(oppositeChange);
                undesiredChanges.remove(oppositeChange);
                fpChanges.add(oppositeChange);
            } else {
                remainingDifferences.add(actualDifference);
            }
        }

        // Now account for false negative
        actualDifferences = remainingDifferences;
        remainingDifferences = new LinkedList<>();
        List<Change> fnChanges = new LinkedList<>();
        for (Change actualDifference : actualDifferences) {
            // Is it a false negative?
            if (requiredChanges.contains(actualDifference)) {
                // Each line in the patch must only be considered once, all additional difference are false positives
                changesToClassify.remove(actualDifference);
                requiredChanges.remove(actualDifference);
                fnChanges.add(actualDifference);
            } else {
                remainingDifferences.add(actualDifference);
            }
        }

        // Now account for the remaining lines in the patch file and determine whether they are true positive or true negative
        List<Change> tpChanges = new LinkedList<>();
        List<Change> tnChanges = new LinkedList<>();
        for (var patchLine : changesToClassify) {
            if (requiredChanges.contains(patchLine)) {
                // In Patch & Expected: It is a true positive
                tpChanges.add(patchLine);
                // Remove the line from the expected changes to account for similar changes
                requiredChanges.remove(patchLine);
            } else if (undesiredChanges.contains(patchLine)) {
                // In Patch & Unexpected: It is a true negative
                tnChanges.add(patchLine);
            }
        }
        long tp = tpChanges.size();
        long fp = fpChanges.size();
        long tn = tnChanges.size();
        long fn = fnChanges.size();
        assert tp + fp + tn + fn == FineDiff.determineChangedLines(unfilteredPatch).size();
        return new ConditionTable(tpChanges, fpChanges, tnChanges, fnChanges);
    }

    // Determine the inverse change (i.e., removed line for an added line, and added line for a removed line
    @NotNull
    private static Change getOppositeChange(Change actualDifference) {
        String changedText = actualDifference.line().line().substring(1);
        Change oppositeChange;
        if (actualDifference.line() instanceof AddedLine) {
            oppositeChange = new Change(actualDifference.file(), new RemovedLine("-" + changedText));
        } else {
            oppositeChange = new Change(actualDifference.file(), new AddedLine("+" + changedText));
        }
        return oppositeChange;
    }

    /**
     * Run the result analysis on the collected results (i.e., results.txt). This method is called by the Docker container
     * after the study has been run.
     *
     * @param args CL arguments
     * @throws IOException If the results cannot be loaded
     */
    public static void main(final String... args) throws IOException {
        if (args.length < 1) {
            System.err.println("The first argument should provide the path to the configuration file that is to be used");
        }
        final StudyConfiguration config = new StudyConfiguration(new File(args[0]));
        final Path resultsDir = Path.of(config.EXPERIMENT_DIR_RESULTS());
        final Path resultFile = resultsDir.toAbsolutePath().resolve("results.txt");
        final Path resultSummaryFile = resultsDir.toAbsolutePath().resolve("results-summary.txt");

        StringBuilder sb = new StringBuilder();
        final AccumulatedOutcome allOutcomes = loadResultObjects(resultFile);
        sb.append(LINE_SEP);
        sb.append(DIV).append(LINE_SEP);
        sb.append("Patch Success").append(LINE_SEP);
        sb.append(DIV).append(LINE_SEP);
        printTechnicalSuccess(sb, allOutcomes);

        sb.append(LINE_SEP);
        sb.append(DIV).append(LINE_SEP);
        sb.append("Precision / Recall").append(LINE_SEP);
        sb.append(DIV).append(LINE_SEP);

        long normalTP = allOutcomes.normalTP;
        long normalFP = allOutcomes.normalFP;
        long normalTN = allOutcomes.normalTN;
        long normalFN = allOutcomes.normalFN;

        sb.append("Without Domain Knowledge").append(LINE_SEP);
        printPrecisionRecall(sb,
                normalTP,
                normalFP,
                normalTN,
                normalFN);


        sb.append(LINE_SEP);
        sb.append(DIV).append(LINE_SEP);
        sb.append("With Domain Knowledge").append(LINE_SEP);
        sb.append(LINE_SEP);

        long filteredTP = allOutcomes.filteredTP;
        long filteredFP = allOutcomes.filteredFP;
        long filteredTN = allOutcomes.filteredTN;
        long filteredFN = allOutcomes.filteredFN;

        printPrecisionRecall(sb,
                filteredTP,
                filteredFP,
                filteredTN,
                filteredFN);

        sb.append(DIV).append(LINE_SEP);
        sb.append("Accuracy").append(LINE_SEP);
        sb.append(DIV).append(LINE_SEP);

        printAccuracy(sb, normalTP, normalFP, normalTN, normalFN, "Normal");
        printAccuracy(sb, filteredTP, filteredFP, filteredTN, filteredFN, "Filtered");

        sb.append(DIV).append(LINE_SEP);
        System.out.print(sb);
        Files.writeString(resultSummaryFile, sb);
    }

    private static void printAccuracy(StringBuilder sb, long tp, long fp, long tn, long fn, String name) {
        long expectedCount = tp + tn;
        long allPositives = tp + fn;
        long allNegative = fp + tn;
        double truePositiveRate = (double) tp / (double) allPositives;
        double trueNegativeRate = (double) tn / (double) allNegative;
        long all = tp + fp + tn + fn;

        sb.append(String.format("%s patching achieved the expected result %d out of %d times", name, expectedCount, all)).append(LINE_SEP);
        sb.append(String.format("Accuracy: %s", percentage(expectedCount, all))).append(LINE_SEP);
        sb.append(String.format("Balanced Accuracy: %1.2f", ((truePositiveRate + trueNegativeRate) / 2.0))).append(LINE_SEP).append(LINE_SEP);
    }

    private static void printTechnicalSuccess(final StringBuilder sb, final AccumulatedOutcome allOutcomes) {
        final long commitPatches = allOutcomes.commitPatches();
        final long commitSuccessNormal = allOutcomes.commitSuccessNormal();
        sb.append(String.format("%d of %d commit-sized patch applications succeeded (%s)", commitSuccessNormal, commitPatches, percentage(commitSuccessNormal, commitPatches))).append(LINE_SEP);

        final long fileNormal = allOutcomes.fileNormal;
        final long fileSuccessNormal = allOutcomes.fileSuccessNormal;

        sb.append(String.format("%d of %d file-sized patch applications succeeded (%s)", fileSuccessNormal, fileNormal, percentage(fileSuccessNormal, fileNormal))).append(LINE_SEP);

        final long lineNormal = allOutcomes.lineNormal;
        final long lineSuccessNormal = allOutcomes.lineSuccessNormal;
        sb.append(String.format("%d of %d line-sized patch applications succeeded (%s)", lineSuccessNormal, lineNormal, percentage(lineSuccessNormal, lineNormal))).append(LINE_SEP);

        // -------------------
        final long lineFiltered = allOutcomes.lineFiltered;
        final long lineSuccessFiltered = allOutcomes.lineSuccessFiltered;
        sb.append(String.format("%d of %d line-sized patch applications succeeded after filtering (%s)%n", lineSuccessFiltered, lineFiltered, percentage(lineSuccessFiltered, lineFiltered))).append(LINE_SEP);

    }

    private static void printPrecisionRecall(StringBuilder sb, final long tp, final long fp, final long tn, final long fn) {
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

    public static AccumulatedOutcome loadResultObjects(final Path path) throws IOException {
        long normalTP = 0;
        long normalFP = 0;
        long normalTN = 0;
        long normalFN = 0;

        long filteredTP = 0;
        long filteredFP = 0;
        long filteredTN = 0;
        long filteredFN = 0;

        long normalWrongLocation = 0;
        long filteredWrongLocation = 0;

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

        try (BufferedReader reader = Files.newBufferedReader(path)) {
            List<String> outcomeLines = new LinkedList<>();
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                if (line.isEmpty()) {
                    PatchOutcome outcome = parseResult(outcomeLines);
                    normalTP += outcome.normalTP();
                    normalFP += outcome.normalFP();
                    normalTN += outcome.normalTN();
                    normalFN += outcome.normalFN();

                    filteredTP += outcome.filteredTP();
                    filteredFP += outcome.filteredFP();
                    filteredTN += outcome.filteredTN();
                    filteredFN += outcome.filteredFN();

                    normalWrongLocation += outcome.normalWrongLocation();
                    filteredWrongLocation += outcome.filteredWrongLocation();

                    commitPatches++;
                    if (outcome.lineSuccessNormal() == outcome.lineNormal()) {
                        commitSuccessNormal++;
                    }
                    if (outcome.lineSuccessFiltered() == outcome.lineFiltered()) {
                        commitSuccessFiltered++;
                    }

                    fileNormal += outcome.fileNormal();
                    fileSuccessNormal += outcome.fileSuccessNormal();
                    fileFiltered += outcome.fileFiltered();
                    fileSuccessFiltered += outcome.fileSuccessFiltered();

                    lineNormal += outcome.lineNormal();
                    lineSuccessNormal += outcome.lineSuccessNormal();
                    lineFiltered += outcome.lineFiltered();
                    lineSuccessFiltered += outcome.lineSuccessFiltered();

                    outcomeLines.clear();
                } else {
                    outcomeLines.add(line);
                }
            }
        }

        System.out.printf("Read a total of %d results.", commitPatches);

        return new AccumulatedOutcome(
                normalTP, normalFP, normalTN, normalFN,
                filteredTP, filteredFP, filteredTN, filteredFN,
                normalWrongLocation, filteredWrongLocation,
                commitPatches, commitSuccessNormal, commitSuccessFiltered,
                fileNormal, fileFiltered, fileSuccessNormal, fileSuccessFiltered,
                lineNormal, lineFiltered, lineSuccessNormal, lineSuccessFiltered
        );
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

    private record ConditionTable(List<Change> tp, List<Change> fp, List<Change> tn, List<Change> fn) {
        public long tpCount() {
            return tp.size();
        }

        public long fpCount() {
            return fp.size();
        }

        public long tnCount() {
            return tn.size();
        }

        public long fnCount() {
            return fn.size();
        }
    }

    private record AccumulatedOutcome(
            long normalTP,
            long normalFP,
            long normalTN,
            long normalFN,
            long filteredTP,
            long filteredFP,
            long filteredTN,
            long filteredFN,
            long normalWrongLocation,
            long filteredWrongLocation,
            long commitPatches,
            long commitSuccessNormal,
            long commitSuccessFiltered,
            long fileNormal,
            long fileFiltered,
            long fileSuccessNormal,
            long fileSuccessFiltered,
            long lineNormal,
            long lineFiltered,
            long lineSuccessNormal,
            long lineSuccessFiltered
    ) {
    }
}
