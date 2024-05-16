package org.variantsync.evaluation.baseline.diff.components;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A FileDiff holds the difference between two versions of a specific file.
 *
 * @param header  The header of UNIX diff's output
 * @param hunks   The hunks (i.e., change groups) of the file's difference
 * @param oldFile The path to the old version of the file
 * @param newFile The path to the new version of the file
 */
public record FileDiff(List<String> header, List<Hunk> hunks, Path oldFile, Path newFile) implements IDiffComponent {
    @Override
    public List<String> toLines() {
        final List<String> lines = new ArrayList<>(header);
        hunks.stream().map(IDiffComponent::toLines).forEach(lines::addAll);
        return lines;
    }

    @Override
    public int changeCount() {
        return this.hunks.stream().mapToInt(Hunk::changeCount).sum();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (String line : toLines()) {
            sb.append(line);
            sb.append(System.lineSeparator());
        }
        return sb.toString();
    }

    public boolean partiallyEquals(FileDiff other, int strip) {
        if (other == null) {
            return false;
        }
        if (!strippedPathsAreEqual(this.oldFile, other.oldFile, strip)) {
            return false;
        }
        if (!strippedPathsAreEqual(this.newFile, other.newFile, strip)) {
            return false;
        }

        return PartiallyEquals.subsetPartiallyEquals(this.hunks, other.hunks, Hunk::partiallyEquals);
    }

    private boolean strippedPathsAreEqual(final Path a, final Path b, int strip) {
        return a.subpath(strip, a.getNameCount()).equals(b.subpath(strip, b.getNameCount()));
    }
}