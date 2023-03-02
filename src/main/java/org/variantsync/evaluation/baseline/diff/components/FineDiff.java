package org.variantsync.evaluation.baseline.diff.components;

import org.variantsync.evaluation.baseline.diff.lines.AddedLine;
import org.variantsync.evaluation.baseline.diff.lines.Change;
import org.variantsync.evaluation.baseline.diff.lines.RemovedLine;

import java.util.ArrayList;
import java.util.List;

/**
 * A FineDiff holds the difference between two versions of a software project. The difference is represented by the line-level
 * patches of all changed files. The patches are grouped by file and represented by FileDiff objects.
 * Each FileDiff contains the line-level patches made to a specific file.
 *
 * @param content The line-level changes of the changed files.
 */
public record FineDiff(List<FileDiff> content) implements IDiffComponent {

    /**
     * Parse all patches in the difference and extract the changes source code lines without the patches' context lines.
     *
     * @param diff The difference from which changed lines are to be extracted
     * @return A list of all changed lines (i.e., added and removed source code)
     */
    public static List<Change> determineChangedLines(FineDiff diff) {
        final List<Change> changedLines = new ArrayList<>();
        for (FileDiff fd : diff.content()) {
            // Filter the hunks of each patch to extract changed lines
            fd.hunks().stream().flatMap(hunk -> hunk.content().stream()).forEach(line -> {
                        if (line instanceof AddedLine addedLine) {
                            changedLines.add(new Change(fd.oldFile().subpath(2, fd.oldFile().getNameCount()), addedLine));
                        } else if (line instanceof RemovedLine removedLine) {
                            changedLines.add(new Change(fd.oldFile().subpath(2, fd.oldFile().getNameCount()), removedLine));
                        }
                    }
            );
        }
        return changedLines;
    }

    @Override
    public List<String> toLines() {
        final List<String> lines = new ArrayList<>();
        content.stream().map(IDiffComponent::toLines).forEach(lines::addAll);
        return lines;
    }
}