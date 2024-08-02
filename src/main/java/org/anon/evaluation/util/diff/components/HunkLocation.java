package org.anon.evaluation.util.diff.components;

import java.util.Objects;

/**
 * The location of a hunk which is the combination of its location in the source and target file of UNIX diff.
 *
 * @param startLineSource The line number of the hunk's start in the source file
 * @param startLineTarget The line number of the hunk's start in the target file
 */
public record HunkLocation(int startLineSource, int startLineTarget) {

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final HunkLocation that = (HunkLocation) o;
        return startLineSource == that.startLineSource && startLineTarget == that.startLineTarget;
    }

    @Override
    public int hashCode() {
        return Objects.hash(startLineSource, startLineTarget);
    }
}