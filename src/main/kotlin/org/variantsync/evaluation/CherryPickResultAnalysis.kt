package org.variantsync.evaluation

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.tinylog.kotlin.Logger
import org.variantsync.diffdetective.util.Assert
import org.variantsync.evaluation.SyncStudyResultAnalysis.percentage
import org.variantsync.evaluation.SyncStudyResultAnalysis.printCorrectness
import org.variantsync.evaluation.SyncStudyResultAnalysis.printPrecisionRecall
import org.variantsync.evaluation.analysis.*
import org.variantsync.evaluation.baseline.diff.components.OriginalDiff
import org.variantsync.evaluation.baseline.diff.lines.ChangedLine
import org.variantsync.evaluation.cherries.CherryPick
import org.variantsync.evaluation.patching.Change
import org.variantsync.evaluation.patching.Rejects
import java.io.File
import java.io.IOException
import java.io.UncheckedIOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.function.Consumer
import kotlin.io.path.name

object CherryPickResultAnalysis {
    private const val DIV = "++++++++++++++++++++++++++++++++++++++"
    private val LINE_SEP = System.lineSeparator()
    private const val STRIP = 1

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
        for (path in resultFiles) {
            if (path.name.endsWith("unix_patch.results")) {
                unixPatchResults.add(path)
            } else if (path.name.endsWith("mpatch.results")) {
                mpatchResults.add(path)
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
    }

    fun processCherriesOutcome(
        workdir: Operations,
        cherryPick: CherryPick,
        dataset: String, runID: ULong,
        normalPatch: OriginalDiff,
        resultDiffNormal: OriginalDiff,
        rejectsNormal: Rejects, evolutionChanges: OriginalDiff,
        patchDuration: Duration,
        patchIsTrivial: Boolean,
    ): CherryPickPatchOutcome {
        Logger.debug("Processing outcome of $runID for patch process in " + workdir.workDir())
        // number of tried line-level patches
        val lineNormal = OriginalDiff.determineChangedLines(normalPatch, STRIP)
        // number of failed patches

        // Determine the number of failed line-level patches
        val lineNormalFailed: MutableList<ChangedLine> = rejectsNormal.intoChangedLines().toMutableList()
        Logger.debug(
            "${lineNormalFailed.size} of ${lineNormal.size} normal line-sized patches failed"
        )

        val scenario = initCherryScenario(normalPatch, evolutionChanges)
        val normalResult: EvaluationResult = scenario.evaluate(
            CountingMap(normalPatch.intoChanges(STRIP)),
            CountingMap(rejectsNormal.intoChanges()),
            CountingMap(OriginalDiff.determineChangedLines(resultDiffNormal, STRIP))
        )

        Assert.assertEquals(normalResult.resultCount(), lineNormal.size.toLong())
        return CherryPickPatchOutcome(
            dataset, runID, cherryPick.cherryCommit, cherryPick.targetCommit, resultDiffNormal.fileDiffs.size.toLong(),
            lineNormal.size.toLong(), lineNormal.size.toLong() - lineNormalFailed.size.toLong(),
            normalResult,
            patchDuration,
            patchIsTrivial,
        )
    }

    private fun initCherryScenario(
        patch: OriginalDiff,
        targetEvolutionDiff: OriginalDiff
    ): EvaluationScenario {
        Logger.debug("Calculating result table with TP, FP, TN, and FN.")
        val changesToClassify = CountingMap<Change>(patch.intoChanges(STRIP))
        val changesInEvolution = CountingMap<ChangedLine>(OriginalDiff.determineChangedLines(targetEvolutionDiff, STRIP))

        // Changes in the target variant's evolution that cannot be
        // synchronized, because they are not part of the source variant and therefore not of the
        // patch
        val unpatchableChanges: CountingMap<ChangedLine> = CountingMap()
        // Expected changes, i.e., changes in the target variant's
        // evolution that can be synchronized
        run {
            val tempChanges: CountingMap<ChangedLine> = CountingMap(OriginalDiff.determineChangedLines(patch, STRIP))
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
        val undesiredChanges: CountingMap<Change> = CountingMap()
        val requiredChanges: CountingMap<Change> = CountingMap()
        run {
            val evoChanges: CountingMap<ChangedLine> = CountingMap(changesInEvolution)
            val patchChanges = CountingMap<Change>(patch.intoChanges(STRIP))
            for (patchChange in patchChanges) {
                if (!evoChanges.contains(patchChange.asChangedLine())) {
                    undesiredChanges.addOne(patchChange)
                } else {
                    evoChanges.removeOne(patchChange.asChangedLine())
                    requiredChanges.addOne(patchChange)
                }
            }
        }
        val derivedElementCount = requiredChanges.elementCount() + undesiredChanges.elementCount()
        Assert.assertEquals(changesToClassify.elementCount(), derivedElementCount)
        return EvaluationScenario(
            requiredChanges,
            undesiredChanges,
            unpatchableChanges
        )
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
        val normalFP: Long = accumulatedOutcome.normalResult.invalid.v
        var normalTN: Long = accumulatedOutcome.normalResult.filteredCorrectly.v
        normalTN += accumulatedOutcome.normalResult.mitigatedInvalid.v
        var normalFN: Long = accumulatedOutcome.normalResult.wrongLocation.v
        normalFN += accumulatedOutcome.normalResult.missing.v

        sb.append(DIV).append(LINE_SEP)
        sb.append("Correctness").append(LINE_SEP)
        sb.append(DIV).append(LINE_SEP)
        printCorrectness(sb, accumulatedOutcome.normalResult)
        sb.append(LINE_SEP)
        sb.append(DIV).append(LINE_SEP)
        sb.append("Precision / Recall").append(LINE_SEP)
        sb.append(DIV).append(LINE_SEP)
        printPrecisionRecall(sb, normalTP, normalFP, normalTN, normalFN)
        sb.append(LINE_SEP)
        sb.append("Edit Distance").append(LINE_SEP)
        sb.append(DIV).append(LINE_SEP)
        sb.append("Edit Distance: ").append(accumulatedOutcome.normalResult.editDistance.v).append(LINE_SEP)
        sb.append("Average Edit Distance: ")
            .append(String.format("%.2f", accumulatedOutcome.normalResult.averageEditDistance()))
            .append(
                LINE_SEP
            )
        sb.append("Fully-Correct Commit Percentage: ")
            .append(String.format("%.2f%%", accumulatedOutcome.normalResult.fullyCorrectPercentage()))
            .append(" of ")
            .append(accumulatedOutcome.normalResult.resultCount())
            .append(
                LINE_SEP
            )
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
    ) {
        val expectedCount = tp + tn
        val allPositives = tp + fn
        val allNegative = fp + tn
        val truePositiveRate = tp.toDouble() / allPositives.toDouble()
        val trueNegativeRate = tn.toDouble() / allNegative.toDouble()
        val all = tp + fp + tn + fn
        sb.append(
            String.format(
                "Patching achieved the expected result %d out of %d times",
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
        val lineNormal = allOutcomes.lineNormal
        val lineSuccessNormal = allOutcomes.lineSuccessNormal
        sb.append(
            String.format(
                "%d of %d line-sized patch applications succeeded (%s)",
                lineSuccessNormal, lineNormal, percentage(lineSuccessNormal, lineNormal)
            )
        )
            .append(LINE_SEP)
    }

    @Throws(IOException::class)
    fun loadResultObjects(paths: List<Path>, analysisMode: AnalysisMode): AccumulatedOutcome {
        var commitPatches: Long = 0
        var commitSuccessNormal: Long = 0
        var lineNormal: Long = 0
        var lineSuccessNormal: Long = 0
        val accumulatedNormal = AccumulatedResult()

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
                            commitPatches++
                            if (outcome.lineSuccessNormal == outcome.lineNormal) {
                                commitSuccessNormal++
                            }
                            lineNormal += outcome.lineNormal
                            lineSuccessNormal += outcome.lineSuccessNormal
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
            accumulatedNormal,
            commitPatches, commitSuccessNormal, lineNormal, lineSuccessNormal
        )
    }

    private fun parseResult(lines: List<String>): CherryPickPatchOutcome {
        val sb = StringBuilder()
        lines.forEach(Consumer { l: String? -> sb.append(l).append("\n") })
        val mapper = jacksonObjectMapper()
        mapper.registerModule(JavaTimeModule())
        return mapper.readValue(sb.toString(), CherryPickPatchOutcome::class.java)
    }

    data class AccumulatedOutcome(
        val normalResult: AccumulatedResult,
        val commitPatches: Long,
        val commitSuccessNormal: Long,
        val lineNormal: Long,
        val lineSuccessNormal: Long,
    )

}