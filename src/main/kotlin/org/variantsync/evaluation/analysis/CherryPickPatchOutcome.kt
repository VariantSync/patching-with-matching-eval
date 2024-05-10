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
class CherryPickPatchOutcome
/**
 * @param dataset                  The considered subject
 * @param runID                    The id of this run
 * @param cherry                 The id of the parent commit
 * @param target                 The id of the child commit
 * @param normalActualVsExpected   Number of differences between the patched target variant and the expected result (without filtering)
 * @param lineNormal               Number of unfiltered line-level patches
 * @param lineSuccessNormal        Number of successful line-level patches
 */(
    private val dataset: String,
    private val runID: ULong,
    private val cherry: String,
    private val target: String,
    private val normalActualVsExpected: Long,
    val lineNormal: Long,
    val lineSuccessNormal: Long,
    val normalResult: EvaluationResult,
    private val patchDuration: Duration,
    private val patchIsTrivial: Boolean,
) {

    @Throws(IOException::class)
    fun writeAsJSON(pathToFile: Path, append: Boolean) {
        val jsonBuilder = StringBuilder()
        jsonBuilder.append("{").append("\n")
        jsonBuilder.append(toJSON("dataset", dataset)).append(",\n")
        jsonBuilder.append(toJSON("runID", runID)).append(",\n")
        jsonBuilder.append(toJSON("commitV0", cherry)).append(",\n")
        jsonBuilder.append(toJSON("commitV1", target)).append(",\n")
        jsonBuilder.append(toJSON("normalActualVsExpected", normalActualVsExpected)).append(",\n")
        jsonBuilder.append(toJSON("lineNormal", lineNormal)).append(",\n")
        jsonBuilder.append(toJSON("lineSuccessNormal", lineSuccessNormal)).append(",\n")
        jsonBuilder.append(toJSON("normalApplied", normalResult.applied.v)).append(",\n")
        jsonBuilder.append(toJSON("normalInvalid", normalResult.invalid.v)).append(",\n")
        jsonBuilder.append(toJSON("normalWrongLocation", normalResult.wrongLocation.v)).append(",\n")
        jsonBuilder.append(toJSON("normalMissing", normalResult.missing.v)).append(",\n")
        jsonBuilder.append(toJSON("normalFilteredCorrectly", normalResult.filteredCorrectly.v)).append(",\n")
        jsonBuilder.append(toJSON("normalFilteredIncorrectly", normalResult.filteredIncorrectly.v)).append(",\n")
        jsonBuilder.append(toJSON("normalMitigatedInvalid", normalResult.mitigatedInvalid.v)).append(",\n")
        jsonBuilder.append(toJSON("normalMitigatedMissing", normalResult.mitigatedMissing.v)).append(",\n")
        jsonBuilder.append(toJSON("patchDuration", patchDuration.toMillis())).append(",\n")
        jsonBuilder.append(toJSON("patchIsTrivial", patchIsTrivial)).append("\n")
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
                ", commitV0='" + cherry + '\'' +
                ", commitV1='" + target + '\'' +
                ", normalActualVsExpected=" + normalActualVsExpected +
                ", lineNormal=" + lineNormal +
                ", lineSuccessNormal=" + lineSuccessNormal +
                ", normalResult=" + normalResult +
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
        fun fromJSON(`object`: JsonObject): CherryPickPatchOutcome {
            return CherryPickPatchOutcome(
                `object`["dataset"].asString,
                `object`["runID"].asLong.toULong(),
                `object`["commitV0"].asString,
                `object`["commitV1"].asString,
                `object`["normalActualVsExpected"].asLong,
                `object`["lineNormal"].asLong,
                `object`["lineSuccessNormal"].asLong,
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
                Duration.ofMillis(`object`["patchDuration"].asLong),
                `object`["patchIsTrivial"].asBoolean,
            )
        }
    }
}