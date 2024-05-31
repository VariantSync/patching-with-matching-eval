package org.variantsync.evaluation

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.tinylog.kotlin.Logger
import org.tinylog.kotlin.Logger.debug
import org.variantsync.diffdetective.util.Assert
import org.variantsync.evaluation.analysis.*
import org.variantsync.evaluation.baseline.diff.components.FileDiff
import org.variantsync.evaluation.baseline.diff.components.OriginalDiff
import org.variantsync.evaluation.baseline.diff.lines.ChangedLine
import org.variantsync.evaluation.patching.Change
import org.variantsync.evaluation.patching.Rejects
import org.variantsync.vevos.simulation.variability.SPLCommit
import java.io.File
import java.io.IOException
import java.io.UncheckedIOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.function.Consumer
import java.util.stream.Collectors
import kotlin.io.path.name

/**
 * Performs the result analysis presented in our paper.
 */
object SyncStudyResultAnalysis {
    private const val DIV = "++++++++++++++++++++++++++++++++++++++"
    private val LINE_SEP = System.lineSeparator()
    private const val STRIP = 2

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
     * result
     * @param resultDiffFiltered The difference between the patched target variant and the expected
     * result (with filtering)
     * @param rejectsNormal The rejected patches (aka. failed patches) without filtering
     * @param rejectsFiltered The rejected patches (aka. failed patches) with filtering
     * @param targetChanges The difference between the two versions of the target variant
     * @return The patch outcome
     */
    fun processOutcome(
        workdir: Operations,
        dataset: String, runID: ULong, sourceVariant: String,
        targetVariant: String, commitV0: SPLCommit, commitV1: SPLCommit,
        normalPatch: OriginalDiff, filteredPatch: OriginalDiff,
        requiredChanges: CountingMap<Change>,
        resultDiffNormal: OriginalDiff, resultDiffFiltered: OriginalDiff,
        rejectsNormal: Rejects, rejectsFiltered: Rejects, targetChanges: OriginalDiff,
        normalDuration: Duration,
        filteredDuration: Duration,
        patchIsTrivial: Boolean,
    ): SyncStudyPatchOutcome {
        debug("Processing outcome of $runID for patch process in " + workdir.workDir())
        // evaluate patch rejects
        // number of tried file-level patches
        val fileNormal = HashSet(
            normalPatch.fileDiffs.stream()
                .map { fd: FileDiff -> fd.oldFile.toString() }.collect(Collectors.toList())
        ).size
        // number of tried line-level patches
        val lineNormal = OriginalDiff.determineChangedLines(normalPatch, STRIP)
        // number of failed patches

        // Determine the number of failed file-level patches (without filtering)
        val fileNormalFailed: Long = HashSet(
            rejectsNormal.rejects.stream()
                .map { cl: Change -> cl.path.toString() }.collect(Collectors.toSet())
        ).size.toLong()
        debug(
            "$fileNormalFailed of $fileNormal normal file-sized patches failed."
        )

        // Determine the number of failed line-level patches (without filtering)
        val lineNormalFailed: MutableList<ChangedLine> = rejectsNormal.intoChangedLines().toMutableList()
        debug(
            "${lineNormalFailed.size} of ${lineNormal.size} normal line-sized patches failed"
        )

        // Number of tried file-level patches (with filtering)
        val fileFiltered = HashSet(
            filteredPatch.fileDiffs.stream()
                .map { fd: FileDiff -> fd.oldFile.toString() }.collect(Collectors.toList())
        ).size
        // Number of tried line-level patches (with filtering)
        val lineFiltered = OriginalDiff.determineChangedLines(filteredPatch, STRIP)
        // Number of failed patches

        // Determine the number of failed file-level patches (with filtering)
        val fileFilteredFailed: Long = HashSet(
            rejectsFiltered.rejects.stream()
                .map { cl: Change -> cl.path.toString() }.collect(Collectors.toList())
        ).size.toLong()
        debug(
            "" + fileFilteredFailed + " of " + fileFiltered
                    + " filtered file-sized patches failed."
        )

        // Determine the number of failed line-level patches (with filtering)
        val lineFilteredFailed: MutableList<ChangedLine> = rejectsFiltered.intoChangedLines().toMutableList()
        debug(
            "" + lineFilteredFailed.size + " of " + lineFiltered.size
                    + " filtered line-sized patches failed"
        )
        val scenario = initScenario(normalPatch, requiredChanges, targetChanges)
        val normalResult: EvaluationResult = scenario.evaluate(
            CountingMap(normalPatch.intoChanges(STRIP)),
            CountingMap(rejectsNormal.intoChanges()),
            CountingMap(OriginalDiff.determineChangedLines(resultDiffNormal, STRIP))
        )
        val filteredResult: EvaluationResult = scenario.evaluate(
            CountingMap(filteredPatch.intoChanges(STRIP)),
            CountingMap(rejectsFiltered.intoChanges()),
            CountingMap(OriginalDiff.determineChangedLines(resultDiffFiltered, STRIP))
        )
        Assert.assertEquals(normalResult.resultCount(), filteredResult.resultCount())
        Assert.assertEquals(normalResult.resultCount(), lineNormal.size.toLong())
        Assert.assertEquals(filteredResult.resultCount(), lineNormal.size.toLong())


        return SyncStudyPatchOutcome(
            dataset, runID, commitV0.id(), commitV1.id(), sourceVariant,
            targetVariant, resultDiffNormal.fileDiffs.size.toLong(),
            resultDiffFiltered.fileDiffs.size.toLong(), fileNormal.toLong(), lineNormal.size.toLong(),
            (
                    fileNormal - fileNormalFailed),
            (lineNormal.size - lineNormalFailed.size).toLong(),
            fileFiltered.toLong(), lineFiltered.size.toLong(), (fileFiltered - fileFilteredFailed),
            (
                    lineFiltered.size - lineFilteredFailed.size).toLong(),
            normalResult, filteredResult,
            normalDuration,
            filteredDuration,
            patchIsTrivial,
        )
    }

    private fun initScenario(
        unfilteredPatch: OriginalDiff,
        requiredChanges: CountingMap<Change>,
        targetEvolutionDiff: OriginalDiff
    ): EvaluationScenario {
        debug("Calculating result table with TP, FP, TN, and FN.")
        val changesToClassify = CountingMap<Change>(unfilteredPatch.intoChanges(STRIP))
        val changesInEvolution =
            CountingMap<ChangedLine>(OriginalDiff.determineChangedLines(targetEvolutionDiff, STRIP))

        // Changes in the target variant's evolution that cannot be
        // synchronized, because they are not part of the source variant and therefore not of the
        // patch
        val unpatchableChanges: CountingMap<ChangedLine> = CountingMap()
        // Expected changes, i.e., changes in the target variant's
        // evolution that can be synchronized
        run {
            val tempChanges: CountingMap<ChangedLine> =
                CountingMap(OriginalDiff.determineChangedLines(unfilteredPatch, STRIP))
            for (evolutionChange in changesInEvolution) {
                if (!tempChanges.contains(evolutionChange)) {
                    unpatchableChanges.addOne(evolutionChange)
                } else {
                    tempChanges.removeOne(evolutionChange)
                }
            }
        }

        // Determine undesired changes, i.e., changes in the evolution of the source but not the
        // target
        val undesiredChanges = determineUndesired(changesToClassify, requiredChanges)
        val derivedElementCount = requiredChanges.elementCount() + undesiredChanges.elementCount()
        Assert.assertEquals(changesToClassify.elementCount(), derivedElementCount)
        return EvaluationScenario(
            requiredChanges,
            undesiredChanges,
            unpatchableChanges
        )
    }

    private fun determineUndesired(
        changesToClassify: CountingMap<Change>,
        requiredChanges: CountingMap<Change>
    ): CountingMap<Change> {
        val undesiredChanges: CountingMap<Change> = run {
            val tempChanges: CountingMap<Change> = CountingMap(changesToClassify)
            for (requiredChange in requiredChanges) {
                Assert.assertTrue(tempChanges.removeOne(requiredChange))
            }
            tempChanges
        }
        Assert.assertEquals(
            requiredChanges.elementCount() + undesiredChanges.elementCount(),
            changesToClassify.elementCount()
        )
        return undesiredChanges
    }

    /**
     * Run the result analysis on the collected results. This method is called by the Docker
     * container after the study has been run.
     *
     * @param args CL arguments
     * @throws IOException If the results cannot be loaded
     */
    @Throws(IOException::class)
    @JvmStatic
    fun main(args: Array<String>) {
        if (args.isEmpty()) {
            System.err.println(
                "The first argument should provide the path to the configuration file that is to be used"
            )
        }
        val config = EvalConfig(File(args[0]))
        val resultsDir = config.EXPERIMENT_DIR_RESULTS()
        val resultFiles = ArrayList<Path>()
        Files.list(resultsDir).use { files ->
            files.filter { f: Path ->
                val fileName = f.fileName.toString()
                fileName.endsWith(".results")
            }.forEach { f: Path ->
                try {
                    resultFiles.add(f)
                } catch (e: IOException) {
                    throw UncheckedIOException(e)
                }
            }
        }

        resultFiles.sort()


        val unixPatchResults = ArrayList<Path>()
        val mpatchResults = ArrayList<Path>()
        val gitApplyResults = ArrayList<Path>()
        for (path in resultFiles) {
            if (path.name.endsWith("unix_patch.results")) {
                unixPatchResults.add(path)
            } else if (path.name.endsWith("mpatch.results")) {
                mpatchResults.add(path)
            } else if (path.name.endsWith("cherry_pick.results")) {
                gitApplyResults.add(path)
            }
        }

        // Determine the overall results
        println()
        println("+++++++++++++++++++++++++++")
        println("RESULTS FOR TRIVIAL - MPATCH")
        println("+++++++++++++++++++++++++++")
        println()
        analyze(mpatchResults, "mpatch_overview", AnalysisMode.Trivial)

        // Determine the overall results
        println()
        println("+++++++++++++++++++++++++++")
        println("RESULTS FOR TRIVIAL - UNIX PATCH")
        println("+++++++++++++++++++++++++++")
        println()
        analyze(unixPatchResults, "unix_patch_overview", AnalysisMode.Trivial)

        // Determine the overall results
        println()
        println("+++++++++++++++++++++++++++")
        println("RESULTS FOR TRIVIAL - GIT APPLY")
        println("+++++++++++++++++++++++++++")
        println()
        analyze(unixPatchResults, "cherry_pick_overview", AnalysisMode.Trivial)

        println()
        println("+++++++++++++++++++++++++++")
        println("RESULTS FOR NON-TRIVIAL - MPATCH")
        println("+++++++++++++++++++++++++++")
        println()
        analyze(mpatchResults, "mpatch_overview", AnalysisMode.NonTrivial)

        // Determine the overall results
        println()
        println("+++++++++++++++++++++++++++")
        println("RESULTS FOR NON-TRIVIAL - UNIX PATCH")
        println("+++++++++++++++++++++++++++")
        println()
        analyze(unixPatchResults, "unix_patch_overview", AnalysisMode.NonTrivial)

        // Determine the overall results
        println()
        println("+++++++++++++++++++++++++++")
        println("RESULTS FOR NON-TRIVIAL - GIT APPLY")
        println("+++++++++++++++++++++++++++")
        println()
        analyze(unixPatchResults, "cherry_pick_overview", AnalysisMode.NonTrivial)
    }

    @Throws(IOException::class)
    private fun analyze(resultFiles: List<Path>, summaryFileName: String, analysisMode: AnalysisMode) {
        val resultSummaryFile = resultFiles.first().parent.resolve("%s.summary".format(summaryFileName))
        val sb = StringBuilder()
        val accumulatedOutcome = loadResultObjects(resultFiles, analysisMode)
        sb.append(LINE_SEP)
        sb.append(DIV).append(LINE_SEP)
        sb.append("File: ").append(summaryFileName).append(LINE_SEP)
        sb.append(LINE_SEP)
        sb.append(DIV).append(LINE_SEP)
        var normalTP: Long = accumulatedOutcome.normalResult.applied.v
        normalTP += accumulatedOutcome.normalResult.mitigatedMissing.v
        var normalFP: Long = accumulatedOutcome.normalResult.invalid.v
        normalFP += accumulatedOutcome.normalResult.wrongLocation.v
        var normalTN: Long = accumulatedOutcome.normalResult.filteredCorrectly.v
        normalTN += accumulatedOutcome.normalResult.mitigatedInvalid.v
        var normalFN: Long = accumulatedOutcome.normalResult.wrongLocation.v
        normalFN += accumulatedOutcome.normalResult.missing.v
        var filteredTP: Long = accumulatedOutcome.filteredResult.applied.v
        filteredTP += accumulatedOutcome.filteredResult.mitigatedMissing.v
        val filteredFP: Long = accumulatedOutcome.filteredResult.invalid.v
        var filteredTN: Long = accumulatedOutcome.filteredResult.filteredCorrectly.v
        filteredTN += accumulatedOutcome.filteredResult.mitigatedInvalid.v
        var filteredFN: Long = accumulatedOutcome.filteredResult.wrongLocation.v
        filteredFN += accumulatedOutcome.filteredResult.missing.v
        sb.append(DIV).append(LINE_SEP)
        sb.append("Correctness").append(LINE_SEP)
        sb.append(DIV).append(LINE_SEP)
        sb.append("Without Domain Knowledge").append(LINE_SEP)
        printCorrectness(sb, accumulatedOutcome.normalResult)
        sb.append(LINE_SEP)
        //  sb.append(DIV).append(LINE_SEP)
        //  sb.append("With Domain Knowledge").append(LINE_SEP)
        //  sb.append(LINE_SEP)
        //  printCorrectness(sb, accumulatedOutcome.filteredResult)
        // sb.append(LINE_SEP)
        sb.append(DIV).append(LINE_SEP)
        sb.append("Precision / Recall").append(LINE_SEP)
        sb.append(DIV).append(LINE_SEP)
        sb.append("Without Domain Knowledge").append(LINE_SEP)
        printPrecisionRecall(sb, normalTP, normalFP, normalTN, normalFN)
        sb.append(LINE_SEP)
        //  sb.append(DIV).append(LINE_SEP)
        //  sb.append("With Domain Knowledge").append(LINE_SEP)
        //  sb.append(LINE_SEP)
        //  printPrecisionRecall(sb, filteredTP, filteredFP, filteredTN, filteredFN)
        sb.append(DIV).append(LINE_SEP)
        sb.append("Edit Distance").append(LINE_SEP)
        sb.append(DIV).append(LINE_SEP)
        sb.append("Edit Distance Normal: ").append(accumulatedOutcome.normalResult.editDistance.v).append(
            LINE_SEP
        )
        sb.append("Average Edit Distance Normal: ")
            .append(String.format("%.2f%%", accumulatedOutcome.normalResult.averageEditDistance()))
            .append(
                LINE_SEP
            )
        sb.append("Fully-Correct Commit Percentage Normal: ")
            .append(String.format("%.2f%%", accumulatedOutcome.normalResult.fullyCorrectPercentage()))
            .append(" of ")
            .append(accumulatedOutcome.normalResult.resultCount())
            .append(
                LINE_SEP
            )
        // sb.append("Edit Distance Filtered: ").append(accumulatedOutcome.filteredResult.editDistance.v).append(
        //     LINE_SEP
        // )
        // sb.append("Average Edit Distance Filtered: ")
        //     .append(String.format("%.2f%%", accumulatedOutcome.filteredResult.averageEditDistance()))
        //     .append(
        //         LINE_SEP
        //     )
        // sb.append("Fully-Correct Commit Percentage Filtered: ")
        //     .append(String.format("%.2f%%", accumulatedOutcome.filteredResult.fullyCorrectPercentage()))
        //     .append(" of ")
        //     .append(accumulatedOutcome.filteredResult.resultCount())
        //     .append(
        //         LINE_SEP
        //     )
        sb.append(DIV).append(LINE_SEP)
        sb.append(DIV).append(LINE_SEP)
        print(sb)
        Files.writeString(resultSummaryFile, sb)
    }

    @Throws(IOException::class)
    private fun analyze(resultFile: Path, analysisMode: AnalysisMode) {
        val fileName = resultFile.fileName.getName(0).toString()
        val list = ArrayList<Path>()
        list.add(resultFile)
        analyze(list, fileName, analysisMode)
    }

    private fun printAccuracy(
        sb: StringBuilder, tp: Long, fp: Long, tn: Long, fn: Long,
        name: String
    ) {
        val expectedCount = tp + tn
        val allPositives = tp + fn
        val allNegative = fp + tn
        val truePositiveRate = tp.toDouble() / allPositives.toDouble()
        val trueNegativeRate = tn.toDouble() / allNegative.toDouble()
        val all = tp + fp + tn + fn
        sb.append(
            String.format(
                "%s patching achieved the expected result %d out of %d times", name,
                expectedCount, all
            )
        ).append(LINE_SEP)
        sb.append(String.format("Accuracy: %s", percentage(expectedCount, all))).append(LINE_SEP)
        sb.append(
            String.format(
                "Balanced Accuracy: %1.2f",
                (truePositiveRate + trueNegativeRate) / 2.0
            )
        ).append(LINE_SEP).append(LINE_SEP)
    }

    private fun printTechnicalSuccess(
        sb: StringBuilder,
        allOutcomes: AccumulatedOutcome
    ) {
        val commitPatches = allOutcomes.commitPatches
        val commitSuccessNormal = allOutcomes.commitSuccessNormal
        sb.append(
            String.format(
                "%d of %d commit-sized patch applications succeeded (%s)",
                commitSuccessNormal, commitPatches, percentage(commitSuccessNormal, commitPatches)
            )
        )
            .append(LINE_SEP)
        val fileNormal = allOutcomes.fileNormal
        val fileSuccessNormal = allOutcomes.fileSuccessNormal
        sb.append(
            String.format(
                "%d of %d file-sized patch applications succeeded (%s)",
                fileSuccessNormal, fileNormal, percentage(fileSuccessNormal, fileNormal)
            )
        )
            .append(LINE_SEP)
        val lineNormal = allOutcomes.lineNormal
        val lineSuccessNormal = allOutcomes.lineSuccessNormal
        sb.append(
            String.format(
                "%d of %d line-sized patch applications succeeded (%s)",
                lineSuccessNormal, lineNormal, percentage(lineSuccessNormal, lineNormal)
            )
        )
            .append(LINE_SEP)

        // -------------------
        val lineFiltered = allOutcomes.lineFiltered
        val lineSuccessFiltered = allOutcomes.lineSuccessFiltered
        sb.append(
            String.format(
                "%d of %d line-sized patch applications succeeded after filtering (%s)%n",
                lineSuccessFiltered, lineFiltered, percentage(lineSuccessFiltered, lineFiltered)
            )
        )
            .append(LINE_SEP)
    }

    fun printPrecisionRecall(
        sb: StringBuilder, tp: Long, fp: Long,
        tn: Long, fn: Long
    ) {
        val precision = tp.toDouble() / (tp.toDouble() + fp)
        val recall = tp.toDouble() / (tp.toDouble() + fn)
        val fMeasure = 2 * precision * recall / (precision + recall)
        sb.append("TP: ").append(tp).append(LINE_SEP)
        sb.append("FP: ").append(fp).append(LINE_SEP)
        sb.append("TN: ").append(tn).append(LINE_SEP)
        sb.append("FN: ").append(fn).append(LINE_SEP)
        sb.append(String.format("Precision: %1.2f", precision)).append(LINE_SEP)
        sb.append(String.format("Recall: %1.2f", recall)).append(LINE_SEP)
        sb.append(String.format("F-Measure: %1.2f", fMeasure)).append(LINE_SEP)
    }

    fun printCorrectness(sb: StringBuilder, result: AccumulatedResult) {
        val correct = result.correctCount().toDouble()
        val incorrect = result.incorrectCount().toDouble()
        val total = result.resultCount().toDouble()
        val correctPerc = 100.0 * correct / total
        val incorrectPerc = 100.0 * incorrect / total
        val appliedP: Double = 100.0 * result.applied.v.toDouble() / total
        val invalidP: Double = 100.0 * result.invalid.v.toDouble() / total
        val wrongLocationP: Double = 100.0 * result.wrongLocation.v.toDouble() / total
        val missingP: Double = 100.0 * result.missing.v.toDouble() / total
        val filteredCorrectlyP: Double = 100.0 * result.filteredCorrectly.v.toDouble() / total
        val filteredIncorrectlyP: Double = 100.0 * result.filteredIncorrectly.v.toDouble() / total
        val mitigatedInvalidP: Double = 100.0 * result.mitigatedInvalid.v.toDouble() / total
        val mitigatedMissingP: Double = 100.0 * result.mitigatedMissing.v.toDouble() / total
        sb.append(String.format("Correct: %1.2f%%  (%d of %d)", correctPerc, correct.toLong(), total.toLong()))
            .append(LINE_SEP)
        sb.append(String.format("Incorrect: %1.2f%% (%d of %d)", incorrectPerc, incorrect.toLong(), total.toLong()))
            .append(LINE_SEP)
        sb.append("++ Distribution ++").append(LINE_SEP)
        sb.append(String.format("%1.2f%% applied, %1.2f%% invalid", appliedP, invalidP))
            .append(LINE_SEP)
        sb.append(
            String.format(
                "%1.2f%% missing, %1.2f%% wrong location",
                missingP, wrongLocationP
            )
        ).append(LINE_SEP)
        sb.append(
            String.format(
                "%1.2f%% filtered correctly, %1.2f%% filtered incorrectly",
                filteredCorrectlyP,
                filteredIncorrectlyP
            )
        )
            .append(LINE_SEP)
        sb.append(
            String.format(
                "%1.2f%% mitigated invalid, %1.2f%% mitigated missing",
                mitigatedInvalidP,
                mitigatedMissingP
            )
        )
            .append(LINE_SEP)
    }

    @Throws(IOException::class)
    fun loadResultObjects(paths: List<Path>, analysisMode: AnalysisMode): AccumulatedOutcome {
        var commitPatches: Long = 0
        var commitSuccessNormal: Long = 0
        var commitSuccessFiltered: Long = 0
        var fileNormal: Long = 0
        var fileFiltered: Long = 0
        var fileSuccessNormal: Long = 0
        var fileSuccessFiltered: Long = 0
        var lineNormal: Long = 0
        var lineFiltered: Long = 0
        var lineSuccessNormal: Long = 0
        var lineSuccessFiltered: Long = 0
        val accumulatedNormal = AccumulatedResult()
        val accumulatedFiltered = AccumulatedResult()

        for (path in paths) {
            Files.newBufferedReader(path).use { reader ->
                val outcomeLines: MutableList<String> = ArrayList()
                var line = reader.readLine()
                while (line != null) {
                    if (line.isEmpty()) {
                        val outcome = parseResult(outcomeLines)

                        if (analysisMode == AnalysisMode.NonTrivial && outcome.patchIsTrivial) {
                            // Nothing
                        } else if (analysisMode == AnalysisMode.Trivial && !outcome.patchIsTrivial) {
                            // Nothing
                        } else {
                            accumulatedNormal.add(outcome.normalResult)
                            accumulatedFiltered.add(outcome.filteredResult)
                            commitPatches++
                            if (outcome.lineSuccessNormal == outcome.lineNormal) {
                                commitSuccessNormal++
                            }
                            if (outcome.lineSuccessFiltered == outcome.lineFiltered) {
                                commitSuccessFiltered++
                            }
                            fileNormal += outcome.fileNormal
                            fileSuccessNormal += outcome.fileSuccessNormal
                            fileFiltered += outcome.fileFiltered
                            fileSuccessFiltered += outcome.fileSuccessFiltered
                            lineNormal += outcome.lineNormal
                            lineSuccessNormal += outcome.lineSuccessNormal
                            lineFiltered += outcome.lineFiltered
                            lineSuccessFiltered += outcome.lineSuccessFiltered
                        }
                        outcomeLines.clear()
                    } else {
                        outcomeLines.add(line)
                    }
                    line = reader.readLine()
                }
            }
        }
        System.out.printf("Read a total of %d results.", commitPatches)
        return AccumulatedOutcome(
            accumulatedNormal, accumulatedFiltered,
            commitPatches, commitSuccessNormal, commitSuccessFiltered, fileNormal, fileFiltered,
            fileSuccessNormal, fileSuccessFiltered, lineNormal, lineFiltered, lineSuccessNormal,
            lineSuccessFiltered
        )
    }

    private fun parseResult(lines: List<String>): SyncStudyPatchOutcome {
        val sb = StringBuilder()
        lines.forEach(Consumer { l: String? -> sb.append(l).append("\n") })
        val mapper = jacksonObjectMapper()
        try {
            mapper.registerModule(JavaTimeModule())
            return mapper.readValue(sb.toString(), SyncStudyPatchOutcome::class.java)
        } catch (e: Exception) {
            Logger.error(e)
            throw e
        }
    }

    fun percentage(x: Long, y: Long): String {
        val percentage: Double = if (y == 0L) {
            0.0
        } else {
            100 * (x.toDouble() / y.toDouble())
        }
        return String.format("%3.1f%s", percentage, "%")
    }

    data class AccumulatedOutcome(
        val normalResult: AccumulatedResult,
        val filteredResult: AccumulatedResult,
        val commitPatches: Long,
        val commitSuccessNormal: Long,
        val commitSuccessFiltered: Long,
        val fileNormal: Long,
        val fileFiltered: Long,
        val fileSuccessNormal: Long,
        val fileSuccessFiltered: Long,
        val lineNormal: Long,
        val lineFiltered: Long,
        val lineSuccessNormal: Long,
        val lineSuccessFiltered: Long
    )
}
