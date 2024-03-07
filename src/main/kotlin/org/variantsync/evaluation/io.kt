package org.variantsync.evaluation

import org.tinylog.kotlin.Logger
import org.variantsync.evaluation.patching.PatchOutcome
import org.variantsync.evaluation.syncstudy.panic
import org.variantsync.vevos.simulation.feature.Variant
import java.io.IOException
import java.nio.file.Path

fun saveResult(
    patchOutcome: PatchOutcome,
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