package org.variantsync.evaluation.analysis

import java.time.Duration


/**
 * Represents the outcome of a single experimental run in the study.
 */
class SyncStudyPatchOutcome
/**
 * @param dataset                  The considered subject
 * @param runID                    The id of this run
 * @param commitV0                 The id of the parent commit
 * @param commitV1                 The id of the child commit
 * @param sourceVariant            The name of the source variant
 * @param targetVariant            The name of the target variant
 * @param normalActualVsExpected   Number of differences between the patched target variant and the expected result (without filtering)
 * @param filteredActualVsExpected Number of differences between the patched target variant and the expected result (with filtering)
 * @param fileNormal               Number of unfiltered file-level patches
 * @param lineNormal               Number of unfiltered line-level patches
 * @param fileSuccessNormal        Number of successful file-level patches
 * @param lineSuccessNormal        Number of successful line-level patches
 * @param fileFiltered             Number of filtered file-level patches
 * @param lineFiltered             Number of filtered line-level patches
 * @param fileSuccessFiltered      Number of successful file-level patches
 * @param lineSuccessFiltered      Number of successful line-level patches
 */(
    val dataset: String,
    val runID: ULong,
    val commitV0: String,
    val commitV1: String,
    val sourceVariant: String,
    val targetVariant: String,
    val normalActualVsExpected: Long,
    val filteredActualVsExpected: Long,
    val fileNormal: Long,
    val lineNormal: Long,
    val fileSuccessNormal: Long,
    val lineSuccessNormal: Long,
    val fileFiltered: Long,
    val lineFiltered: Long,
    val fileSuccessFiltered: Long,
    val lineSuccessFiltered: Long,
    val normalResult: EvaluationResult,
    val filteredResult: EvaluationResult,
    val normalDuration: Duration,
    val filteredDuration: Duration,
    val patchIsTrivial: Boolean,
) {
    override fun toString(): String {
        return "PatchOutcome{" +
                "dataset='" + dataset + '\'' +
                ", runID=" + runID +
                ", commitV0='" + commitV0 + '\'' +
                ", commitV1='" + commitV1 + '\'' +
                ", sourceVariant='" + sourceVariant + '\'' +
                ", targetVariant='" + targetVariant + '\'' +
                ", normalActualVsExpected=" + normalActualVsExpected +
                ", filteredActualVsExpected=" + filteredActualVsExpected +
                ", fileNormal=" + fileNormal +
                ", lineNormal=" + lineNormal +
                ", fileSuccessNormal=" + fileSuccessNormal +
                ", lineSuccessNormal=" + lineSuccessNormal +
                ", fileFiltered=" + fileFiltered +
                ", lineFiltered=" + lineFiltered +
                ", fileSuccessFiltered=" + fileSuccessFiltered +
                ", lineSuccessFiltered=" + lineSuccessFiltered +
                ", normalResult=" + normalResult +
                ", filteredResult=" + filteredResult +
                '}'
    }
}