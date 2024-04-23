package org.variantsync.evaluation.analysis

import com.google.gson.JsonObject
import org.variantsync.evaluation.*
import org.variantsync.evaluation.analysis.*
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
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
    private val dataset: String,
    private val runID: ULong,
    private val commitV0: String,
    private val commitV1: String,
    private val sourceVariant: String,
    private val targetVariant: String,
    private val normalActualVsExpected: Long,
    private val filteredActualVsExpected: Long,
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
) {

    @Throws(IOException::class)
    fun writeAsJSON(pathToFile: Path, append: Boolean) {
        val jsonBuilder = StringBuilder()
        jsonBuilder.append("{").append("\n")
        jsonBuilder.append(toJSON("dataset", dataset)).append(",\n")
        jsonBuilder.append(toJSON("runID", runID)).append(",\n")
        jsonBuilder.append(toJSON("commitV0", commitV0)).append(",\n")
        jsonBuilder.append(toJSON("commitV1", commitV1)).append(",\n")
        jsonBuilder.append(toJSON("sourceVariant", sourceVariant)).append(",\n")
        jsonBuilder.append(toJSON("targetVariant", targetVariant)).append(",\n")
        jsonBuilder.append(toJSON("normalAsExpected", normalActualVsExpected)).append(",\n")
        jsonBuilder.append(toJSON("filteredAsExpected", filteredActualVsExpected)).append(",\n")
        jsonBuilder.append(toJSON("fileNormal", fileNormal)).append(",\n")
        jsonBuilder.append(toJSON("lineNormal", lineNormal)).append(",\n")
        jsonBuilder.append(toJSON("fileSuccessNormal", fileSuccessNormal)).append(",\n")
        jsonBuilder.append(toJSON("lineSuccessNormal", lineSuccessNormal)).append(",\n")
        jsonBuilder.append(toJSON("fileFiltered", fileFiltered)).append(",\n")
        jsonBuilder.append(toJSON("lineFiltered", lineFiltered)).append(",\n")
        jsonBuilder.append(toJSON("fileSuccessFiltered", fileSuccessFiltered)).append(",\n")
        jsonBuilder.append(toJSON("lineSuccessFiltered", lineSuccessFiltered)).append(",\n")
        jsonBuilder.append(toJSON("normalApplied", normalResult.applied.v)).append(",\n")
        jsonBuilder.append(toJSON("normalInvalid", normalResult.invalid.v)).append(",\n")
        jsonBuilder.append(toJSON("normalWrongLocation", normalResult.wrongLocation.v)).append(",\n")
        jsonBuilder.append(toJSON("normalMissing", normalResult.missing.v)).append(",\n")
        jsonBuilder.append(toJSON("normalFilteredCorrectly", normalResult.filteredCorrectly.v)).append(",\n")
        jsonBuilder.append(toJSON("normalFilteredIncorrectly", normalResult.filteredIncorrectly.v)).append(",\n")
        jsonBuilder.append(toJSON("normalMitigatedInvalid", normalResult.mitigatedInvalid.v)).append(",\n")
        jsonBuilder.append(toJSON("normalMitigatedMissing", normalResult.mitigatedMissing.v)).append(",\n")
        jsonBuilder.append(toJSON("filteredApplied", filteredResult.applied.v)).append(",\n")
        jsonBuilder.append(toJSON("filteredInvalid", filteredResult.invalid.v)).append(",\n")
        jsonBuilder.append(toJSON("filteredWrongLocation", filteredResult.wrongLocation.v)).append(",\n")
        jsonBuilder.append(toJSON("filteredMissing", filteredResult.missing.v)).append(",\n")
        jsonBuilder.append(toJSON("filteredFilteredCorrectly", filteredResult.filteredCorrectly.v)).append(",\n")
        jsonBuilder.append(toJSON("filteredFilteredIncorrectly", filteredResult.filteredIncorrectly.v))
            .append(",\n")
        jsonBuilder.append(toJSON("filteredMitigatedInvalid", filteredResult.mitigatedInvalid.v)).append(",\n")
        jsonBuilder.append(toJSON("filteredMitigatedMissing", filteredResult.mitigatedMissing.v)).append(",\n")
        jsonBuilder.append(toJSON("normalDuration", normalDuration.toMillis())).append(",\n")
        jsonBuilder.append(toJSON("filteredDuration", filteredDuration.toMillis())).append("\n")
        jsonBuilder.append("}").append("\n\n")

        synchronized(SyncStudyPatchOutcome) {
            if (Files.notExists(pathToFile)) {
                Files.createFile(pathToFile)
            }
        }
        if (append) {
            Files.writeString(pathToFile, jsonBuilder.toString(), StandardOpenOption.APPEND)
        } else {
            Files.writeString(pathToFile, jsonBuilder.toString(), StandardOpenOption.TRUNCATE_EXISTING)
        }
    }

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

    companion object {
        fun toJSON(key: String, value: Any): String {
            return "\"$key\": $value"
        }

        fun toJSON(key: String, value: Long): String {
            return "\"$key\": $value"
        }

        @JvmStatic
        fun fromJSON(`object`: JsonObject): SyncStudyPatchOutcome {
            return SyncStudyPatchOutcome(
                `object`["dataset"].asString,
                `object`["runID"].asLong.toULong(),
                `object`["commitV0"].asString,
                `object`["commitV1"].asString,
                `object`["sourceVariant"].asString,
                `object`["targetVariant"].asString,
                `object`["normalAsExpected"].asLong,
                `object`["filteredAsExpected"].asLong,
                `object`["fileNormal"].asLong,
                `object`["lineNormal"].asLong,
                `object`["fileSuccessNormal"].asLong,
                `object`["lineSuccessNormal"].asLong,
                `object`["fileFiltered"].asLong,
                `object`["lineFiltered"].asLong,
                `object`["fileSuccessFiltered"].asLong,
                `object`["lineSuccessFiltered"].asLong,
                EvaluationResult(
                    Applied(`object`["normalApplied"].asLong),
                    Invalid(`object`["normalInvalid"].asLong),
                    WrongLocation(`object`["normalWrongLocation"].asLong),
                    Missing(`object`["normalMissing"].asLong),
                    FilteredCorrectly(`object`["normalFilteredCorrectly"].asLong),
                    FilteredIncorrectly(`object`["normalFilteredIncorrectly"].asLong),
                    MitigatedInvalid(`object`["normalMitigatedInvalid"].asLong),
                    MitigatedMissing(`object`["normalMitigatedMissing"].asLong)
                ),
                EvaluationResult(
                    Applied(`object`["filteredApplied"].asLong),
                    Invalid(`object`["filteredInvalid"].asLong),
                    WrongLocation(`object`["filteredWrongLocation"].asLong),
                    Missing(`object`["filteredMissing"].asLong),
                    FilteredCorrectly(`object`["filteredFilteredCorrectly"].asLong),
                    FilteredIncorrectly(`object`["filteredFilteredIncorrectly"].asLong),
                    MitigatedInvalid(`object`["filteredMitigatedInvalid"].asLong),
                    MitigatedMissing(`object`["filteredMitigatedMissing"].asLong)
                ),
                Duration.ofMillis(`object`["normalDuration"].asLong),
                Duration.ofMillis(`object`["filteredDuration"].asLong),
            )
        }
    }
}