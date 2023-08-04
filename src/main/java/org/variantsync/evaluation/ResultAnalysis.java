package org.variantsync.evaluation;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;
import org.variantsync.diffdetective.util.Assert;
import org.variantsync.evaluation.baseline.diff.components.FineDiff;
import org.variantsync.evaluation.baseline.diff.lines.AddedLine;
import org.variantsync.evaluation.baseline.diff.lines.ChangeLine;
import org.variantsync.evaluation.baseline.diff.lines.RemovedLine;
import org.tinylog.Logger;

import org.variantsync.vevos.simulation.variability.SPLCommit;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
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
     * @param sourceChanges The difference between the two versions of the source variant
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
            FineDiff rejectsNormal, FineDiff rejectsFiltered, final FineDiff sourceChanges,
            final Set<String> skippedFilesNormal, final Set<String> skippedFilesFiltered) {
        Logger.debug("Processing outcome for patch process in " + workdir.workDir);
        // evaluate patch rejects
        // number of tried file-level patches
        final int fileNormal = new HashSet<>(normalPatch.content().stream()
                .map(fd -> fd.oldFile().toString()).collect(Collectors.toList())).size();
        // number of tried line-level patches
        final List<ChangeLine> lineNormal = FineDiff.determineChangedLines(normalPatch);
        // number of failed patches
        int fileNormalFailed;
        List<ChangeLine> lineNormalFailed;
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
        final List<ChangeLine> lineFiltered = FineDiff.determineChangedLines(filteredPatch);
        // Number of failed patches
        int fileFilteredFailed;
        List<ChangeLine> lineFilteredFailed;
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

        // Calculate the condition table (without filtering): true positives, false positive, true
        // negatives, and false negatives
        final ConditionTable normalConditionTable = calculateConditionTable(normalPatch,
                normalPatch, resultDiffNormal, sourceChanges, lineNormalFailed);
        final long normalTP = normalConditionTable.tpCount();
        final long normalFP = normalConditionTable.fpCount();
        final long normalTN = normalConditionTable.tnCount();
        final long normalFN = normalConditionTable.fnCount();
        // Number of line-level patches applied to the wrong location
        final long normalWrongLocation = normalConditionTable.wrongLocationCount();
        final long normalFilteredIncorrectly = normalConditionTable.filteredIncorrectlyCount();
        Assert.assertTrue(normalFilteredIncorrectly == 0);

        // Calculate the condition table (with filtering): true positives, false positive, true
        // negatives, and false negatives
        final ConditionTable filteredConditionTable = calculateConditionTable(filteredPatch,
                normalPatch, resultDiffFiltered, sourceChanges, lineFilteredFailed);
        final long filteredTP = filteredConditionTable.tpCount();
        final long filteredFP = filteredConditionTable.fpCount();
        final long filteredTN = filteredConditionTable.tnCount();
        final long filteredFN = filteredConditionTable.fnCount();
        // Number of line-level patches applied to the wrong location
        final long filteredWrongLocation = filteredConditionTable.wrongLocationCount();
        final long filteredFilteredIncorrectly = filteredConditionTable.filteredIncorrectlyCount();

        // Some sanity checks
        if (filteredFN > normalFN) {
            // This is an expected case. If there is duplicate code alternatives of a variation
            // point, the filter
            // might remove changes if they come from an undesired alternative, even though these
            // changes might be to
            // the duplicate code and should therefore be applied.
            Logger.debug("There are more false negatives after filtering! " + filteredFN + " vs. "
                    + normalFN);
            if (inDebug) {
                writeFnDebug(workdir, normalConditionTable, filteredConditionTable);
            }
        }
        Assert.assertTrue(normalTP + normalFP + normalFN + normalTN == filteredTP + filteredFP
                + filteredTN + filteredFN);
//        Assert.assertTrue(normalTN + normalFN - normalWrongLocation <= lineNormalFailed.size());
        Assert.assertTrue(filteredTP + filteredFP + filteredFN + filteredTN == lineFiltered.size()
                + (lineNormal.size() - lineFiltered.size()));

        return new PatchOutcome(dataset, runID, commitV0.id(), commitV1.id(), sourceVariant,
                targetVariant, resultDiffNormal.content().size(),
                resultDiffFiltered.content().size(), fileNormal, lineNormal.size(),
                fileNormal - fileNormalFailed, lineNormal.size() - lineNormalFailed.size(),
                fileFiltered, lineFiltered.size(), fileFiltered - fileFilteredFailed,
                lineFiltered.size() - lineFilteredFailed.size(), normalTP, normalFP, normalTN,
                normalFN, normalWrongLocation, normalFilteredIncorrectly, filteredTP, filteredFP,
                filteredTN, filteredFN, filteredWrongLocation, filteredFilteredIncorrectly);
    }

    private static void writeFnDebug(WorkPaths workdir, ConditionTable normalConditionTable,
            ConditionTable filteredConditionTable) {
        Function<List<ChangeLine>, String> changesToLines = (List<ChangeLine> changes) -> {
            StringBuilder sb = new StringBuilder();
            for (ChangeLine change : changes) {
                sb.append("++++++++++++++++++++++++++++++++++");
                sb.append(System.lineSeparator());
                sb.append("File: ");
                sb.append(change.file());
                sb.append(System.lineSeparator());
                sb.append(change.line());
                sb.append(System.lineSeparator());
                sb.append("++++++++++++++++++++++++++++++++++");
                sb.append(System.lineSeparator());
            }
            return sb.toString();
        };
        try {
            Files.writeString(workdir.debugDir.resolve("falseNegatives-normal.txt"),
                    changesToLines.apply(normalConditionTable.fn()));
        } catch (final IOException e) {
            Logger.error("Was not able to save resultDiffOriginal", e);
        }
        try {
            Files.writeString(workdir.debugDir.resolve("falseNegatives-filtered.txt"),
                    changesToLines.apply(filteredConditionTable.fn()));
        } catch (final IOException e) {
            Logger.error("Was not able to save resultDiffFiltered", e);
        }
    }

    // Calculate true positives, false positives, true negatives, and false negatives
    private static ConditionTable calculateConditionTable(FineDiff evaluatedPatch,
            FineDiff unfilteredPatch, FineDiff resultDiff, FineDiff evolutionDiff,
            List<ChangeLine> failedChanges) {
        Logger.debug("Calculating result table with TP, FP, TN, and FN.");
        List<ChangeLine> changesInPatch = FineDiff.determineChangedLines(evaluatedPatch);
        List<ChangeLine> changesToClassify = FineDiff.determineChangedLines(unfilteredPatch);
        List<ChangeLine> changesInResult = FineDiff.determineChangedLines(resultDiff);
        List<ChangeLine> changesInEvolution = FineDiff.determineChangedLines(evolutionDiff);
        // Create a shallow copy to not mutate the given list
        failedChanges = new ArrayList<>(failedChanges);

        List<ChangeLine> tpChanges = new ArrayList<>();
        List<ChangeLine> fpChanges = new ArrayList<>();
        List<ChangeLine> tnChanges = new ArrayList<>();
        List<ChangeLine> fnChanges = new ArrayList<>();
        List<ChangeLine> wrongLocationChanges = new ArrayList<>();
        List<ChangeLine> filteredIncorrectlyChanges = new ArrayList<>();


        // Changes in the target variant's evolution that cannot be
        // synchronized, because they are not part of the source variant and therefore not of the
        // patch
        final List<ChangeLine> unpatchableChanges = new ArrayList<>();
        // Expected changes, i.e., changes in the target variant's
        // evolution that can be synchronized
        List<ChangeLine> requiredChanges = new ArrayList<>();
        {
            final List<ChangeLine> tempChanges = new ArrayList<>(changesToClassify);
            for (ChangeLine evolutionChange : changesInEvolution) {
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
        List<ChangeLine> undesiredChangesTotal = determineUndesired(changesToClassify, requiredChanges);

        // Determine undesired changes in the patch
        List<ChangeLine> undesiredChangesInPatch = determineUndesired(changesInPatch, requiredChanges);

        // Determine actual differences between result and expected result,
        // i.e., changes that should have been synchronized but were not, or changes that should not
        // have been synchronized
        List<ChangeLine> actualDifferences = new ArrayList<>(changesInResult);
        unpatchableChanges.forEach(actualDifferences::remove);

        Assert.assertTrue(changesToClassify.size() >= changesInPatch.size());
        Assert.assertTrue(
                changesInEvolution.size() - changesToClassify.size() <= unpatchableChanges.size());
        Assert.assertTrue(
                changesInEvolution.size() - unpatchableChanges.size() <= changesToClassify.size());

        // We first want to account for changes that could not be applied
        for (ChangeLine failedChange : failedChanges) {
            if (requiredChanges.contains(failedChange)
                    && actualDifferences.contains(failedChange)) {
                // required but failed -> FN
                // we always handle failed changes that lead to a difference in the result first!
                Assert.assertTrue(requiredChanges.remove(failedChange));
                Assert.assertTrue(actualDifferences.remove(failedChange));
                fnChanges.add(failedChange);
            } else if (undesiredChangesTotal.contains(failedChange)) {
                // undesired but failed -> TN
                Assert.assertTrue(undesiredChangesTotal.remove(failedChange));
                Assert.assertTrue(undesiredChangesInPatch.remove(failedChange));
                tnChanges.add(failedChange);
            } else if (requiredChanges.contains(failedChange)) {
                // Changes that were required and failed but did not result in an unexpected
                // difference in the result
                // This might happen because of other changes being applied and the diff operator
                // applying
                // internal heuristics
                // required but failed -> FN
                Assert.assertTrue(requiredChanges.remove(failedChange));
                fnChanges.add(failedChange);
            } else {
                // A change must be either required or undesired
                throw new IllegalStateException("Unclassified change!");
            }

            Assert.assertTrue(changesToClassify.remove(failedChange));
            Assert.assertTrue(changesInPatch.remove(failedChange));
        }


        // All remaining undesired changes in the patch have been applied and are likely false
        // positives
        for (ChangeLine undesired : undesiredChangesInPatch) {
            // Clean the undesired change from all collections where it is tracked
            Assert.assertTrue(changesToClassify.remove(undesired));
            Assert.assertTrue(changesInPatch.remove(undesired));
            Assert.assertTrue(undesiredChangesTotal.remove(undesired));
            // An applied undesired change should leave an opposite change in the difference
            ChangeLine oppositeChange = getOppositeChange(undesired);
            if (actualDifferences.contains(oppositeChange)) {
                Assert.assertTrue(actualDifferences.remove(oppositeChange));
                fpChanges.add(undesired);
            } else {
                // In rare cases, multiple changes might interact and negate each other
                // In such cases, there is no difference between the expected and observed result
                tnChanges.add(undesired);
            }
        }
        undesiredChangesInPatch.clear();

        // All remaining undesired changes were not part of the patch and are true negative
        // (filtered correctly)
        for (ChangeLine undesired : undesiredChangesTotal) {
            // Clean the undesired change from all collections where it is tracked
            Assert.assertTrue(changesToClassify.remove(undesired));
            tnChanges.add(undesired);
        }
        undesiredChangesTotal.clear();

        // We next want to account for the remaining differences between the actual and the
        // expected result. Because we handled all undesired changes, they must be false negatives
        // due to a change having been applied to the wrong location
        List<ChangeLine> remainingChanges = new ArrayList<>();
        for (ChangeLine requiredChange : requiredChanges) {
            ChangeLine oppositeChange = getOppositeChange(requiredChange);
            if (!(actualDifferences.contains(requiredChange)
                    && actualDifferences.contains(oppositeChange))) {
                remainingChanges.add(requiredChange);
                continue;
            }
            Assert.assertTrue(changesToClassify.remove(requiredChange));
            Assert.assertTrue(changesInPatch.remove(requiredChange));
            Assert.assertTrue(actualDifferences.remove(requiredChange));
            Assert.assertTrue(actualDifferences.remove(oppositeChange));
            fnChanges.add(requiredChange);
            wrongLocationChanges.add(requiredChange);
        }
        requiredChanges = remainingChanges;

        // Now account for the remaining false negatives
        // These are required changes that were filtered incorrectly
        remainingChanges = new ArrayList<>();
        for (ChangeLine requiredChange : requiredChanges) {
            if (!actualDifferences.contains(requiredChange) || changesInPatch.contains(requiredChange)) {
                remainingChanges.add(requiredChange);
                continue;
            }
            Assert.assertTrue(changesToClassify.remove(requiredChange));
            Assert.assertTrue(actualDifferences.remove(requiredChange));
            fnChanges.add(requiredChange);
            filteredIncorrectlyChanges.add(requiredChange);
        }
        requiredChanges = remainingChanges;

        // Now account for the remaining lines in the patch file. These must be true positives
        for (ChangeLine requiredChange : requiredChanges) {
            Assert.assertTrue(changesToClassify.remove(requiredChange));
            tpChanges.add(requiredChange);
        }

        long tp = tpChanges.size();
        long fp = fpChanges.size();
        long tn = tnChanges.size();
        long fn = fnChanges.size();

        Assert.assertTrue(changesToClassify.isEmpty());

        Assert.assertTrue(
                tp + fp + tn + fn == FineDiff.determineChangedLines(unfilteredPatch).size());
        return new ConditionTable(tpChanges, fpChanges, tnChanges, fnChanges, wrongLocationChanges,
                filteredIncorrectlyChanges);
    }

    private static List<ChangeLine> determineUndesired(List<ChangeLine> changesToClassify,
                                                       List<ChangeLine> requiredChanges) {
        final List<ChangeLine> undesiredChanges = new ArrayList<>();
        {
            final List<ChangeLine> tempChanges = new ArrayList<>(requiredChanges);
            for (ChangeLine patchChange : changesToClassify) {
                if (!tempChanges.contains(patchChange)) {
                    undesiredChanges.add(patchChange);
                } else {
                    tempChanges.remove(patchChange);
                }
            }
        }
        return undesiredChanges;
    }

    // Determine the inverse change (i.e., removed line for an added line, and added line for a
    // removed line
    @NotNull
    private static ChangeLine getOppositeChange(ChangeLine actualDifference) {
        String changedText = actualDifference.line().line().substring(1);
        ChangeLine oppositeChange;
        if (actualDifference.line() instanceof AddedLine) {
            oppositeChange =
                    new ChangeLine(actualDifference.file(), new RemovedLine("-" + changedText));
        } else {
            oppositeChange = new ChangeLine(actualDifference.file(), new AddedLine("+" + changedText));
        }
        return oppositeChange;
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
        final AccumulatedOutcome allOutcomes = loadResultObjects(resultFile);
        sb.append(LINE_SEP);
        sb.append(DIV).append(LINE_SEP);
        sb.append("Patch Success").append(LINE_SEP);
        sb.append(DIV).append(LINE_SEP);
        printTechnicalSuccess(sb, allOutcomes);

        long normalTP = allOutcomes.normalTP;
        long normalFP = allOutcomes.normalFP;
        long normalTN = allOutcomes.normalTN;
        long normalFN = allOutcomes.normalFN;
        long normalWrongLocation = allOutcomes.normalWrongLocation;

        long filteredTP = allOutcomes.filteredTP;
        long filteredFP = allOutcomes.filteredFP;
        long filteredTN = allOutcomes.filteredTN;
        long filteredFN = allOutcomes.filteredFN;
        long filteredWrongLocation = allOutcomes.filteredWrongLocation;

        sb.append(LINE_SEP);
        sb.append(DIV).append(LINE_SEP);
        sb.append("Correctness").append(LINE_SEP);
        sb.append(DIV).append(LINE_SEP);

        sb.append("Without Domain Knowledge").append(LINE_SEP);
        printCorrectness(sb, normalTP, normalFP, normalTN, normalFN - normalWrongLocation,
                normalWrongLocation);

        sb.append(LINE_SEP);
        sb.append(DIV).append(LINE_SEP);
        sb.append("With Domain Knowledge").append(LINE_SEP);
        sb.append(LINE_SEP);

        printCorrectness(sb, filteredTP, filteredFP, filteredTN, filteredFN - filteredWrongLocation,
                filteredWrongLocation);

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

    private static void printCorrectness(StringBuilder sb, final long applied, final long invalid,
            final long discarded, final long missing, final long wrongLocation) {

        final long correct = applied + discarded;
        final long incorrect = missing + invalid + wrongLocation;
        final long total = correct + incorrect;

        final double correctPerc = 100d * (double) correct / (double) total;
        final double incorrectPerc = 100d * (double) incorrect / (double) total;

        final double appliedP = 100d * (double) applied / (double) total;
        final double discardedP = 100d * (double) discarded / (double) total;
        final double invalidP = 100d * (double) invalid / (double) total;
        final double missingP = 100d * (double) missing / (double) total;
        final double wrongLocationP = 100d * (double) wrongLocation / (double) total;


        sb.append(String.format("Correct: %1.2f%%  (%d of %d)", correctPerc, correct, total))
                .append(LINE_SEP);
        sb.append(String.format("Incorrect: %1.2f%% (%d of %d)", incorrectPerc, incorrect, total))
                .append(LINE_SEP);
        sb.append("++ Distribution ++").append(LINE_SEP);
        sb.append(String.format("%1.2f%% applied, %1.2f%% discarded", appliedP, discardedP))
                .append(LINE_SEP);
        sb.append(String.format("%1.2f%% invalid, %1.2f%% missing, %1.2f%% wrong location",
                invalidP, missingP, wrongLocationP)).append(LINE_SEP);
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
            List<String> outcomeLines = new ArrayList<>();
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

        return new AccumulatedOutcome(normalTP, normalFP, normalTN, normalFN, filteredTP,
                filteredFP, filteredTN, filteredFN, normalWrongLocation, filteredWrongLocation,
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

    private record ConditionTable(List<ChangeLine> tp, List<ChangeLine> fp, List<ChangeLine> tn,
                                  List<ChangeLine> fn, List<ChangeLine> wrongLocation, List<ChangeLine> filteredIncorrectly) {
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

        public long wrongLocationCount() {
            return wrongLocation.size();
        }

        public long filteredIncorrectlyCount() {
            return filteredIncorrectly.size();
        }
    }

    private record AccumulatedOutcome(long normalTP, long normalFP, long normalTN, long normalFN,
            long filteredTP, long filteredFP, long filteredTN, long filteredFN,
            long normalWrongLocation, long filteredWrongLocation, long commitPatches,
            long commitSuccessNormal, long commitSuccessFiltered, long fileNormal,
            long fileFiltered, long fileSuccessNormal, long fileSuccessFiltered, long lineNormal,
            long lineFiltered, long lineSuccessNormal, long lineSuccessFiltered) {
    }
}
