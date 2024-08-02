package org.anon.evaluation.analysis

import org.tinylog.kotlin.Logger
import org.anon.diffdetective.util.Assert
import org.anon.evaluation.util.diff.components.OriginalDiff
import org.anon.evaluation.util.diff.lines.ChangedLine
import org.anon.evaluation.execution.CherryPick
import org.anon.evaluation.execution.Operations
import org.anon.evaluation.patching.Change
import org.anon.evaluation.patching.Rejects
import java.time.Duration

object ResultAnalysis {
    private const val STRIP = 1

    fun processCherriesOutcome(
        workdir: Operations,
        cherryPick: CherryPick,
        dataset: String, runID: ULong,
        normalPatch: OriginalDiff,
        resultDiffNormal: OriginalDiff,
        rejectsNormal: Rejects, evolutionChanges: OriginalDiff,
        patchDuration: Duration,
        patchIsTrivial: Boolean,
    ): PatchOutcome {
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
        return PatchOutcome(
            dataset, runID, cherryPick.cherryCommit, cherryPick.expectedResultCommit, OriginalDiff.determineChangedLines(resultDiffNormal, STRIP).size.toLong(),
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
        val changesInEvolution =
            CountingMap<ChangedLine>(OriginalDiff.determineChangedLines(targetEvolutionDiff, STRIP))

        // Changes in the target variant's evolution that cannot be
        // synchronized, because they are not part of the source variant and therefore not of the
        // patch
        val unpatchableChanges: CountingMap<ChangedLine> = CountingMap()
        // Expected changes, i.e., changes in the target variant's
        // evolution that can be synchronized
        run {
            val tempChanges: CountingMap<ChangedLine> = CountingMap(
                OriginalDiff.determineChangedLines(patch, STRIP))
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

    data class AccumulatedOutcome(
        val normalResult: AccumulatedResult,
        val commitPatches: Long,
        val commitSuccessNormal: Long,
        val lineNormal: Long,
        val lineSuccessNormal: Long,
    )

    fun percentage(x: Long, y: Long): String {
        val percentage: Double = if (y == 0L) {
            0.0
        } else {
            100 * (x.toDouble() / y.toDouble())
        }
        return String.format("%3.1f%s", percentage, "%")
    }
}