package org.variantsync.evaluation;

import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Represents the outcome of a single experimental run in the study.
 */
public final class PatchOutcome {
    private final String dataset;
    private final long runID;
    private final String commitV0;
    private final String commitV1;
    private final String sourceVariant;
    private final String targetVariant;
    private final long normalActualVsExpected;
    private final long filteredActualVsExpected;
    private final long fileNormal;
    private final long lineNormal;
    private final long fileSuccessNormal;
    private final long lineSuccessNormal;
    private final long fileFiltered;
    private final long lineFiltered;
    private final long fileSuccessFiltered;
    private final long lineSuccessFiltered;
    private EvalResult normalResult;
    private EvalResult filteredResult;

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
     */
    public PatchOutcome(String dataset,
                        long runID,
                        String commitV0,
                        String commitV1,
                        String sourceVariant,
                        String targetVariant,
                        long normalActualVsExpected,
                        long filteredActualVsExpected,
                        long fileNormal,
                        long lineNormal,
                        long fileSuccessNormal,
                        long lineSuccessNormal,
                        long fileFiltered,
                        long lineFiltered,
                        long fileSuccessFiltered,
                        long lineSuccessFiltered,
                        EvalResult normalResult,
                        EvalResult filteredResult) {
        this.dataset = dataset;
        this.runID = runID;
        this.commitV0 = commitV0;
        this.commitV1 = commitV1;
        this.sourceVariant = sourceVariant;
        this.targetVariant = targetVariant;
        this.normalActualVsExpected = normalActualVsExpected;
        this.filteredActualVsExpected = filteredActualVsExpected;
        this.fileNormal = fileNormal;
        this.lineNormal = lineNormal;
        this.fileSuccessNormal = fileSuccessNormal;
        this.lineSuccessNormal = lineSuccessNormal;
        this.fileFiltered = fileFiltered;
        this.lineFiltered = lineFiltered;
        this.fileSuccessFiltered = fileSuccessFiltered;
        this.lineSuccessFiltered = lineSuccessFiltered;
        this.normalResult = normalResult;
        this.filteredResult = filteredResult;
    }

    public static String toJSON(final String key, final Object value) {
        return "\"" + key + "\": " + value;
    }

    public static String toJSON(final String key, final long value) {
        return "\"" + key + "\": " + value;
    }

    public static PatchOutcome FromJSON(final JsonObject object) {
        return new PatchOutcome(
                object.get("dataset").getAsString(),
                object.get("runID").getAsLong(),
                object.get("commitV0").getAsString(),
                object.get("commitV1").getAsString(),
                object.get("sourceVariant").getAsString(),
                object.get("targetVariant").getAsString(),
                object.get("normalAsExpected").getAsLong(),
                object.get("filteredAsExpected").getAsLong(),
                object.get("fileNormal").getAsLong(),
                object.get("lineNormal").getAsLong(),
                object.get("fileSuccessNormal").getAsLong(),
                object.get("lineSuccessNormal").getAsLong(),
                object.get("fileFiltered").getAsLong(),
                object.get("lineFiltered").getAsLong(),
                object.get("fileSuccessFiltered").getAsLong(),
                object.get("lineSuccessFiltered").getAsLong(),
                new EvalResult(
                        object.get("normalCorrect").getAsInt(),
                object.get("normalInvalid").getAsInt(),
                object.get("normalWrongLocation").getAsInt(),
                object.get("normalMissing").getAsInt(),
                object.get("normalFilteredCorrectly").getAsInt(),
                object.get("normalFilteredIncorrectly").getAsInt(),
                object.get("normalMitigatedInvalid").getAsInt(),
                object.get("normalMitigatedMissing").getAsInt()),
                new EvalResult(object.get("filteredCorrect").getAsInt(),
                object.get("filteredInvalid").getAsInt(),
                object.get("filteredWrongLocation").getAsInt(),
                object.get("filteredMissing").getAsInt(),
                object.get("filteredFilteredCorrectly").getAsInt(),
                object.get("filteredFilteredIncorrectly").getAsInt(),
                object.get("filteredMitigatedInvalid").getAsInt(),
                object.get("filteredMitigatedMissing").getAsInt())
        );
    }

    public void writeAsJSON(final Path pathToFile, final boolean append) throws IOException {
        final StringBuilder jsonBuilder = new StringBuilder();
        jsonBuilder.append("{").append("\n");
        jsonBuilder.append(toJSON("dataset", dataset)).append(",\n");
        jsonBuilder.append(toJSON("runID", runID)).append(",\n");
        jsonBuilder.append(toJSON("commitV0", commitV0)).append(",\n");
        jsonBuilder.append(toJSON("commitV1", commitV1)).append(",\n");
        jsonBuilder.append(toJSON("sourceVariant", sourceVariant)).append(",\n");
        jsonBuilder.append(toJSON("targetVariant", targetVariant)).append(",\n");
        jsonBuilder.append(toJSON("normalAsExpected", normalActualVsExpected)).append(",\n");
        jsonBuilder.append(toJSON("filteredAsExpected", filteredActualVsExpected)).append(",\n");
        jsonBuilder.append(toJSON("fileNormal", fileNormal)).append(",\n");
        jsonBuilder.append(toJSON("lineNormal", lineNormal)).append(",\n");
        jsonBuilder.append(toJSON("fileSuccessNormal", fileSuccessNormal)).append(",\n");
        jsonBuilder.append(toJSON("lineSuccessNormal", lineSuccessNormal)).append(",\n");
        jsonBuilder.append(toJSON("fileFiltered", fileFiltered)).append(",\n");
        jsonBuilder.append(toJSON("lineFiltered", lineFiltered)).append(",\n");
        jsonBuilder.append(toJSON("fileSuccessFiltered", fileSuccessFiltered)).append(",\n");
        jsonBuilder.append(toJSON("lineSuccessFiltered", lineSuccessFiltered)).append(",\n");
        jsonBuilder.append(toJSON("normalApplied", normalResult.getApplied())).append(",\n");
        jsonBuilder.append(toJSON("normalInvalid", normalResult.getInvalid())).append(",\n");
        jsonBuilder.append(toJSON("normalWrongLocation", normalResult.getWrongLocation())).append(",\n");
        jsonBuilder.append(toJSON("normalMissing", normalResult.getMissing())).append(",\n");
        jsonBuilder.append(toJSON("normalFilteredCorrectly", normalResult.getFilteredCorrectly())).append(",\n");
        jsonBuilder.append(toJSON("normalFilteredIncorrectly", normalResult.getFilteredIncorrectly())).append(",\n");
        jsonBuilder.append(toJSON("normalMitigatedInvalid", normalResult.getMitigatedInvalid())).append(",\n");
        jsonBuilder.append(toJSON("normalMitigatedMissing", normalResult.getMitigatedMissing())).append(",\n");
        jsonBuilder.append(toJSON("filteredApplied", filteredResult.getApplied())).append(",\n");
        jsonBuilder.append(toJSON("filteredInvalid", filteredResult.getInvalid())).append(",\n");
        jsonBuilder.append(toJSON("filteredWrongLocation", filteredResult.getWrongLocation())).append(",\n");
        jsonBuilder.append(toJSON("filteredMissing", filteredResult.getMissing())).append(",\n");
        jsonBuilder.append(toJSON("filteredFilteredCorrectly", filteredResult.getFilteredCorrectly())).append(",\n");
        jsonBuilder.append(toJSON("filteredFilteredIncorrectly", filteredResult.getFilteredIncorrectly())).append(",\n");
        jsonBuilder.append(toJSON("filteredMitigatedInvalid", filteredResult.getMitigatedInvalid())).append(",\n");
        jsonBuilder.append(toJSON("filteredMitigatedMissing", filteredResult.getMitigatedMissing())).append(",\n");
        jsonBuilder.append("}").append("\n\n");
        if (!Files.exists(pathToFile)) {
            Files.createFile(pathToFile);
        }
        if (append) {
            Files.writeString(pathToFile, jsonBuilder.toString(), StandardOpenOption.APPEND);
        } else {
            Files.writeString(pathToFile, jsonBuilder.toString(), StandardOpenOption.TRUNCATE_EXISTING);
        }
    }

    public String getDataset() {
        return dataset;
    }

    public long getRunID() {
        return runID;
    }

    public String getCommitV0() {
        return commitV0;
    }

    public String getCommitV1() {
        return commitV1;
    }

    public String getSourceVariant() {
        return sourceVariant;
    }

    public String getTargetVariant() {
        return targetVariant;
    }

    public long getNormalActualVsExpected() {
        return normalActualVsExpected;
    }

    public long getFilteredActualVsExpected() {
        return filteredActualVsExpected;
    }

    public long getFileNormal() {
        return fileNormal;
    }

    public long getLineNormal() {
        return lineNormal;
    }

    public long getFileSuccessNormal() {
        return fileSuccessNormal;
    }

    public long getLineSuccessNormal() {
        return lineSuccessNormal;
    }

    public long getFileFiltered() {
        return fileFiltered;
    }

    public long getLineFiltered() {
        return lineFiltered;
    }

    public long getFileSuccessFiltered() {
        return fileSuccessFiltered;
    }

    public long getLineSuccessFiltered() {
        return lineSuccessFiltered;
    }

    public EvalResult getNormalResult() {
        return normalResult;
    }

    public EvalResult getFilteredResult() {
        return filteredResult;
    }

    @Override
    public String toString() {
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
                '}';
    }
}
