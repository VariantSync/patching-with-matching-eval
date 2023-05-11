package org.variantsync.evaluation.baseline.diff.filter;

import org.variantsync.evaluation.baseline.diff.components.*;
import org.variantsync.evaluation.baseline.diff.lines.AddedLine;
import org.variantsync.evaluation.baseline.diff.lines.ContextLine;
import org.variantsync.evaluation.baseline.diff.lines.Line;
import org.variantsync.evaluation.baseline.diff.lines.RemovedLine;
import org.tinylog.Logger;


import java.util.ArrayList;
import java.util.List;

public class DiffFilter {

    /**
     * Filter the hunks in the given difference depending on the decision of the provided filters.
     *
     * @param originalDiff    The difference that is to be filtered
     * @param fileFilter      A file filter that determines whether the difference of specific files is to be kept
     * @param lineFilter      A line filter that determines whether changes to certain lines are to be kept
     * @return The filtered diff
     */
    public static FineDiff filter(final OriginalDiff originalDiff, IFileDiffFilter fileFilter, ILineFilter lineFilter) {
        fileFilter = fileFilter == null ? new DefaultFileDiffFilter() : fileFilter;
        lineFilter = lineFilter == null ? new DefaultLineFilter() : lineFilter;

        // The list in which we will collect the
        final List<FileDiff> filteredFileDiffs = new ArrayList<>();
        Logger.debug("Filtering %d file diffs".formatted(originalDiff.fileDiffs().size()));

        // Go over all FileDiff in diff
        for (final FileDiff fileDiff : originalDiff.fileDiffs()) {
            // Only process file diffs that should be taken into account according to the filter
            if (fileFilter.keepFileDiff(fileDiff)) {
                // Split FileDiff into line-sized FileDiffs
                var filteredDiff = filter(fileDiff, lineFilter);
                if (filteredDiff == null) {
                    // if all hunks have been filtered
                    continue;
                }
                filteredFileDiffs.add(filteredDiff);
            }
        }

        return new FineDiff(filteredFileDiffs);
    }

    // Filter the hunks in the given FileDiff depending on the
    // decision of the provided filters.
    private static FileDiff filter(final FileDiff fileDiff, final ILineFilter lineFilter) {
        final List<Hunk> filteredHunks = new ArrayList<>();

        for (final Hunk hunk : fileDiff.hunks()) {
            final List<Line> filteredLines = new ArrayList<>();
            boolean atLeastOneChange = false;
            // Index that points to the location of the current line in the current hunk
            int oldIndex = 0;
            int newIndex = 0;
            for (final Line line : hunk.content()) {
                if (line instanceof RemovedLine) {
                    if (lineFilter.keepLineChange(fileDiff.oldFile(), hunk.location().startLineSource() + oldIndex)) {
                        filteredLines.add(line);
                        atLeastOneChange = true;
                    }
                    oldIndex++;
                } else if (line instanceof AddedLine) {
                    if (lineFilter.keepLineChange(fileDiff.newFile(), hunk.location().startLineTarget() + newIndex)) {
                        filteredLines.add(line);
                        atLeastOneChange = true;
                    }
                    newIndex++;
                } else {
                    // Increase the index
                    oldIndex++;
                    newIndex++;
                    filteredLines.add(line);
                }
            }
            if (atLeastOneChange) {
                HunkLocation location = new HunkLocation(hunk.location().startLineSource(), hunk.location().startLineTarget());
                filteredHunks.add(new Hunk(location, filteredLines));
            }
        }
        if (filteredHunks.isEmpty()) {
            return null;
        } else {
            return new FileDiff(fileDiff.header(), filteredHunks, fileDiff.oldFile(), fileDiff.newFile());
        }
    }
}
