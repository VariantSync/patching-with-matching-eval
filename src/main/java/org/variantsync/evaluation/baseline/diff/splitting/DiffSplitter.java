package org.variantsync.evaluation.baseline.diff.splitting;

import org.variantsync.evaluation.baseline.diff.components.*;
import org.variantsync.evaluation.baseline.diff.filter.IFileDiffFilter;
import org.variantsync.evaluation.baseline.diff.lines.AddedLine;
import org.variantsync.evaluation.baseline.diff.lines.ContextLine;
import org.variantsync.evaluation.baseline.diff.lines.Line;
import org.variantsync.evaluation.baseline.diff.lines.RemovedLine;
import org.variantsync.evaluation.baseline.diff.filter.DefaultFileDiffFilter;
import org.variantsync.evaluation.baseline.diff.filter.DefaultLineFilter;
import org.variantsync.evaluation.baseline.diff.filter.ILineFilter;

import java.util.Collections;
import java.util.ArrayList;
import java.util.List;

/**
 * DiffSplitter splits the hunks in the difference of two files to create line-level patches that are stored in a FineDiff.
 */
public class DiffSplitter {

    /**
     * Split the hunks in the given difference into line-level patches.
     *
     * @param diff            The difference that is to be split
     * @param contextProvider A context provider used to provide the correct context for the line-level patches.
     * @return A FineDiff containing the line-level patches.
     */
    public static FineDiff split(final OriginalDiff diff, final IContextProvider contextProvider) {
        return split(diff, null, null, contextProvider);
    }

    /**
     * Split the hunks in the given difference into line-level patches while filtering certain patches depending on the
     * decision of the provided filters.
     *
     * @param originalDiff    The difference that is to be split
     * @param fileFilter      A file filter that determines whether the difference of specific files is to be kept
     * @param lineFilter      A line filter that determines whether changes to certain lines are to be kept
     * @param contextProvider A context provider used to provide the correct context for the line-level patches.
     * @return The split and filtered patches
     */
    public static FineDiff split(final OriginalDiff originalDiff, IFileDiffFilter fileFilter, ILineFilter lineFilter, final IContextProvider contextProvider) {
        fileFilter = fileFilter == null ? new DefaultFileDiffFilter() : fileFilter;
        lineFilter = lineFilter == null ? new DefaultLineFilter() : lineFilter;

        // The list in which we will collect the
        final List<FileDiff> splitFileDiffs = new ArrayList<>();

        // Go over all FileDiff in diff
        for (final FileDiff fileDiff : originalDiff.fileDiffs()) {
            // Only process file diffs that should be taken into account according to the filter
            if (fileFilter.keepFileDiff(fileDiff)) {
                // Split FileDiff into line-sized FileDiffs
                splitFileDiffs.addAll(split(fileDiff, contextProvider, lineFilter));
            }
        }

        return new FineDiff(splitFileDiffs);
    }

    // Split the hunks in the given difference into line-level patches while filtering certain patches depending on the
    // decision of the provided filters.
    private static List<FileDiff> split(final FileDiff fileDiff, final IContextProvider contextProvider, final ILineFilter lineFilter) {
        final List<FileDiff> fileDiffs = new ArrayList<>();

        int hunkLocationOffset = 0;
        for (final Hunk hunk : fileDiff.hunks()) {
            // Index that points to the location of the current line in the current hunk
            int oldIndex = 0;
            int newIndex = 0;
            for (final Line line : hunk.content()) {
                if (line instanceof RemovedLine) {
                    if (lineFilter.keepLineChange(fileDiff.oldFile(), hunk.location().startLineSource() + oldIndex)) {
                        final int leadContextStart = hunk.location().startLineTarget() + newIndex - 1;
                        final int trailContextStart = hunk.location().startLineSource() + oldIndex + 1;
                        fileDiffs.add(calculateMiniDiff(contextProvider, lineFilter, fileDiff, hunk, line, trailContextStart, leadContextStart, hunkLocationOffset));
                    }
                    oldIndex++;
                } else if (line instanceof AddedLine) {
                    if (lineFilter.keepLineChange(fileDiff.newFile(), hunk.location().startLineTarget() + newIndex)) {
                        final int leadContextStart = hunk.location().startLineTarget() + newIndex - 1;
                        final int trailContextStart = hunk.location().startLineSource() + oldIndex;
                        fileDiffs.add(calculateMiniDiff(contextProvider, lineFilter, fileDiff, hunk, line, trailContextStart, leadContextStart, hunkLocationOffset));
                        // Handle creation of new files. An offset of 1 has to be added after the file has been created with the first line
                        if (hunk.location().startLineSource() == 0) {
                            hunkLocationOffset = 1;
                        }
                    }
                    newIndex++;
                } else if (line instanceof ContextLine) {
                    // Increase the index
                    oldIndex++;
                    newIndex++;
                }
            }
        }
        return fileDiffs;
    }

    // Construct the difference for a single line change
    private static FileDiff calculateMiniDiff(final IContextProvider contextProvider, final ILineFilter lineFilter,
                                              final FileDiff fileDiff, final Hunk hunk, final Line line, final int trailContextStart,
                                              final int leadContextStart,
                                              final int hunkLocationOffset) {
        final List<Line> leadingContext = contextProvider.leadingContext(lineFilter, fileDiff, leadContextStart);
        final List<Line> trailingContext = contextProvider.trailingContext(lineFilter, fileDiff, trailContextStart);
        final List<Line> content = new ArrayList<>(leadingContext);
        content.add(line);
        content.addAll(trailingContext);

        final HunkLocation location = new HunkLocation(hunk.location().startLineSource() + hunkLocationOffset, hunk.location().startLineTarget() + hunkLocationOffset);

        final Hunk miniHunk = new Hunk(location, content);
        return new FileDiff(fileDiff.header(), Collections.singletonList(miniHunk), fileDiff.oldFile(), fileDiff.newFile());

    }
}