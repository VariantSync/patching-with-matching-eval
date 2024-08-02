package org.anon.evaluation.util.diff.components;

import org.anon.evaluation.util.diff.lines.AddedLine;
import org.anon.evaluation.util.diff.lines.ChangedLine;
import org.anon.evaluation.util.diff.lines.RemovedLine;
import org.variantsync.evaluation.patching.Change;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A OriginalDiff holds the difference between two versions of a software project. The difference is represented by the
 * patches of all changed files. The patches are grouped by file and represented by FileDiff objects.
 * Each FileDiff contains the patches made to a specific file as specified by UNIX diff.
 *
 * @param fileDiffs The differences of the changed files.
 */
public record OriginalDiff(List<FileDiff> fileDiffs) implements IDiffComponent {
    /**
     * Parse all patches in the difference and extract the changes source code lines without the patches' context lines.
     *
     * @param diff The difference from which changed lines are to be extracted
     * @return A list of all changed lines (i.e., added and removed source code)
     */
    public static List<ChangedLine> determineChangedLines(OriginalDiff diff, int strip) {
        final List<ChangedLine> changedLines = new ArrayList<>();
        for (FileDiff fd : diff.fileDiffs()) {
            // Filter the hunks of each patch to extract changed lines
            fd.hunks().stream().flatMap(hunk -> hunk.content().stream()).forEach(line -> {

                        Path filePath = fd.oldFile().subpath(strip, fd.oldFile().getNameCount());
                        if (line instanceof AddedLine addedLine) {
                            changedLines.add(new ChangedLine(filePath, addedLine));
                        } else if (line instanceof RemovedLine removedLine) {
                            changedLines.add(new ChangedLine(filePath, removedLine));
                        }

                    }
            );
        }
        return changedLines;
    }

    @Override
    public List<String> toLines() {
        final List<String> lines = new ArrayList<>();
        fileDiffs.stream().map(IDiffComponent::toLines).forEach(lines::addAll);
        return lines;
    }

    @Override
    public int changeCount() {
        return this.fileDiffs.stream().mapToInt(FileDiff::changeCount).sum();
    }

    public List<Change> intoChanges(int strip) {
        final List<Change> changes = new ArrayList<>();
        for (FileDiff fd : this.fileDiffs()) {
            changes.addAll(fd.intoChanges(strip));
        }
        return changes;
    }

    public boolean isEmpty() {
        return this.fileDiffs.isEmpty();
    }

    public boolean partiallyEquals(final OriginalDiff other, int strip) {
        if (other == null) {
            return false;
        }

        return PartiallyEquals.subsetPartiallyEquals(this.fileDiffs, other.fileDiffs, (leftDiff, rightDiff)
                -> leftDiff.partiallyEquals(rightDiff, strip));
    }
}