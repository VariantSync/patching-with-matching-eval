package org.variantsync.evaluation.experiment;

import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Represents the outcome of a single experimental run in the study.
 *
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
 * @param normalTP                 Number of true positives without filtering
 * @param normalFP                 Number of false positives without filtering
 * @param normalTN                 Number of true negatives without filtering
 * @param normalFN                 Number of false negatives without filtering
 * @param normalWrongLocation      Number of patches without filtering applied to the wrong location
 * @param filteredTP               Number of true positives with filtering
 * @param filteredFP               Number of false positives with filtering
 * @param filteredTN               Number of true negatives with filtering
 * @param filteredFN               Number of false negatives with filtering
 * @param filteredWrongLocation    Number of patches with filtering applied to the wrong location
 */
public record PatchOutcome(String dataset,
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
                           long normalTP,
                           long normalFP,
                           long normalTN,
                           long normalFN,
                           long normalWrongLocation,
                           long filteredTP,
                           long filteredFP,
                           long filteredTN,
                           long filteredFN,
                           long filteredWrongLocation) {

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
                object.get("normalTP").getAsLong(),
                object.get("normalFP").getAsLong(),
                object.get("normalTN").getAsLong(),
                object.get("normalFN").getAsLong(),
                object.get("normalWrongLocation").getAsLong(),
                object.get("filteredTP").getAsLong(),
                object.get("filteredFP").getAsLong(),
                object.get("filteredTN").getAsLong(),
                object.get("filteredFN").getAsLong(),
                object.get("filteredWrongLocation").getAsLong()
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
        jsonBuilder.append(toJSON("normalTP", normalTP)).append(",\n");
        jsonBuilder.append(toJSON("normalFP", normalFP)).append(",\n");
        jsonBuilder.append(toJSON("normalTN", normalTN)).append(",\n");
        jsonBuilder.append(toJSON("normalFN", normalFN)).append(",\n");
        jsonBuilder.append(toJSON("normalWrongLocation", normalWrongLocation)).append(",\n");
        jsonBuilder.append(toJSON("filteredTP", filteredTP)).append(",\n");
        jsonBuilder.append(toJSON("filteredFP", filteredFP)).append(",\n");
        jsonBuilder.append(toJSON("filteredTN", filteredTN)).append(",\n");
        jsonBuilder.append(toJSON("filteredFN", filteredFN)).append(",\n");
        jsonBuilder.append(toJSON("filteredWrongLocation", filteredWrongLocation)).append("\n");
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

}
