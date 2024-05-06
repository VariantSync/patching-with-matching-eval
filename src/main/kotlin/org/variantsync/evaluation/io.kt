package org.variantsync.evaluation

import org.tinylog.kotlin.Logger
import org.variantsync.evaluation.analysis.CherryPickPatchOutcome
import org.variantsync.evaluation.analysis.SyncStudyPatchOutcome
import org.variantsync.evaluation.cherries.CherryPick
import org.variantsync.evaluation.syncstudy.panic
import org.variantsync.vevos.simulation.feature.Variant
import java.io.IOException
import java.nio.file.Path

fun saveResult(
    patchOutcome: SyncStudyPatchOutcome,
    resultFile: Path,
    runID: ULong,
    source: Variant,
    target: Variant
) {
    try {
        patchOutcome.writeAsJSON(resultFile, true)
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

fun saveResult(
    patchOutcome: CherryPickPatchOutcome,
    cherryPick: CherryPick,
    resultFile: Path,
    runID: ULong,
) {
    try {
        patchOutcome.writeAsJSON(resultFile, true)
    } catch (e: IOException) {
        panic(
            "Was not able to write filtered patch result file for run "
                    + runID, e
        )
    }
    Logger.debug(
        "Finished patching for cherry " + cherryPick.cherryCommit + " and target "
                + cherryPick.targetCommit
    )
}