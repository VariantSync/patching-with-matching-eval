package org.variantsync.evaluation.baseline.diff.components;

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