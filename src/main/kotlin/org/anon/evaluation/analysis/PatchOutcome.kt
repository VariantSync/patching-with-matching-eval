package org.anon.evaluation.analysis

import java.time.Duration

/**
 * Represents the outcome of a single experimental run in the study.
 */
class PatchOutcome
/**
 * @param dataset                  The considered subject
 * @param runID                    The id of this run
 * @param cherry                 The id of the parent commit
 * @param pick                 The id of the child commit
 * @param normalActualVsExpected   Number of differences between the patched target variant and the expected result (without filtering)
 * @param lineNormal               Number of unfiltered line-level patches
 * @param lineSuccessNormal        Number of successful line-level patches
 */(
    val dataset: String,
    val runID: ULong,
    val cherry: String,
    val pick: String,
    val normalActualVsExpected: Long,
    val lineNormal: Long,
    val lineSuccessNormal: Long,
    val normalResult: EvaluationResult,
    val patchDuration: Duration,
    val patchIsTrivial: Boolean,
) {
    override fun toString(): String {
        return "PatchOutcome{" +
                "dataset='" + dataset + '\'' +
                ", runID=" + runID +
                ", cherry='" + cherry + '\'' +
                ", pick='" + pick + '\'' +
                ", normalActualVsExpected=" + normalActualVsExpected +
                ", lineNormal=" + lineNormal +
                ", lineSuccessNormal=" + lineSuccessNormal +
                ", normalResult=" + normalResult +
                '}'
    }
}