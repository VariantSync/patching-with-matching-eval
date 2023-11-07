package org.variantsync.evaluation.baseline.diff.splitting;

import org.jetbrains.annotations.NotNull;
import org.variantsync.evaluation.baseline.diff.components.*;
import org.variantsync.evaluation.baseline.diff.filter.DefaultFileDiffFilter;
import org.variantsync.evaluation.baseline.diff.filter.DefaultLineFilter;
import org.variantsync.evaluation.baseline.diff.filter.IFileDiffFilter;
import org.variantsync.evaluation.baseline.diff.filter.ILineFilter;
import org.variantsync.evaluation.baseline.diff.lines.*;

import java.util.ArrayList;
import java.util.Collections;
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
        final List<FileDiff> resultDiffs = new ArrayList<>();

        for (final Hunk hunk : fileDiff.hunks()) {
            int changesTotal = hunk.changeCount();
            int changeCount = 1;
            // Index that points to the location of the current line in the current hunk
            int oldIndex = 0;
            int newIndex = 0;
            for (final Line line : hunk.content()) {
                if (line instanceof RemovedLine) {
                    if (lineFilter.keepLineChange(fileDiff.oldFile(), hunk.location().startLineSource() + oldIndex)) {
                        final int leadContextStart = hunk.location().startLineTarget() + newIndex - 1;
                        final int trailContextStart = hunk.location().startLineSource() + oldIndex + 1;
                        final HunkLocation location = getRemoveLocation(hunk, oldIndex);
                        resultDiffs.add(calculateMiniDiff(contextProvider, lineFilter, fileDiff, hunk, line, trailContextStart, leadContextStart, location, changeCount == changesTotal));
                    }
                    oldIndex++;
                    changeCount++;
                } else if (line instanceof AddedLine) {
                    if (lineFilter.keepLineChange(fileDiff.newFile(), hunk.location().startLineTarget() + newIndex)) {
                        final int leadContextStart = hunk.location().startLineTarget() + newIndex - 1;
                        final int trailContextStart = hunk.location().startLineSource() + oldIndex;
                        final HunkLocation location = getAddLocation(hunk, newIndex);
                        resultDiffs.add(calculateMiniDiff(contextProvider, lineFilter, fileDiff, hunk, line, trailContextStart, leadContextStart, location, changeCount == changesTotal));
                    }
                    newIndex++;
                    changeCount++;
                } else if (line instanceof ContextLine) {
                    // Increase the index
                    oldIndex++;
                    newIndex++;
                }
            }
        }
        return resultDiffs;
    }

    @NotNull
    private static HunkLocation getAddLocation(Hunk hunk, int newIndex) {
        final int hunkLocationSource;
        if (hunk.location().startLineSource() == 0 && newIndex == 0) {
            // If it's the first added line a file being created, the hunk starts at 0
            hunkLocationSource = 0;
        } else {
            // If it's any other added line the hunk starts at least at 1
            hunkLocationSource = Integer.max(hunk.location().startLineSource(), 1);
        }
        final int hunkLocationTarget = Integer.max(hunk.location().startLineTarget(), 1);
        return new HunkLocation(hunkLocationSource, hunkLocationTarget);
    }

    @NotNull
    private static HunkLocation getRemoveLocation(Hunk hunk, int oldIndex) {
        final int hunkLocationSource = Integer.max(hunk.location().startLineSource(), 1);
        final int hunkLocationTarget;
        if (hunk.location().startLineTarget() == 0 && oldIndex == (hunk.size()-(hunk.hasMetaLine() ? 2 : 1))) {
            // If it's the last removed line a file being removed, the hunk starts at 0
            // We can determine whether it is the last line by comparing oldIndex and the hunks size minus a offset
            // that depends on the existence of a meta line
            hunkLocationTarget = 0;
        } else {
            // If it's any other removed line, the hunk starts at least at 1
            hunkLocationTarget = Integer.max(hunk.location().startLineTarget(), 1);
        }
        return new HunkLocation(hunkLocationSource, hunkLocationTarget);
    }

    // Construct the difference for a single line change
    private static FileDiff calculateMiniDiff(final IContextProvider contextProvider, final ILineFilter lineFilter,
                                              final FileDiff fileDiff, final Hunk hunk, final Line line, final int trailContextStart,
                                              final int leadContextStart,
                                              final HunkLocation hunkLocation,
                                              boolean isLastChange) {
        final List<Line> leadingContext = contextProvider.leadingContext(lineFilter, fileDiff, leadContextStart);
        final List<Line> trailingContext = contextProvider.trailingContext(lineFilter, fileDiff, trailContextStart, line, isLastChange);

        // Add the leading context
        final List<Line> content = new ArrayList<>(leadingContext);

        // Add the change
        content.add(line);

        // Add the trailing context
        content.addAll(trailingContext);

        final Hunk miniHunk = new Hunk(hunkLocation, hunk.rawLocation(), content);
        return new FileDiff(fileDiff.header(), Collections.singletonList(miniHunk), fileDiff.oldFile(), fileDiff.newFile());
    }


}