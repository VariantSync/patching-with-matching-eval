package org.variantsync.evaluation.baseline.diff.components;

import org.variantsync.evaluation.baseline.diff.lines.AddedLine;
import org.variantsync.evaluation.baseline.diff.lines.Line;
import org.variantsync.evaluation.baseline.diff.lines.MetaLine;
import org.variantsync.evaluation.baseline.diff.lines.RemovedLine;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

/**
 * A Hunk represents a changed text block in the difference between two versions of a file.
 *
 * @param location The location of the hunk in the file
 * @param content  The content of the hunk (i.e., context and changed lines)
 */
public record Hunk(HunkLocation location, List<Line> content) implements IDiffComponent {

    @Override
    public List<String> toLines() {
        final List<String> lines = new LinkedList<>();
        final int sourceSize = (int) content.stream().filter(l -> !(l instanceof AddedLine || l instanceof MetaLine)).count();
        final int targetSize = (int) content.stream().filter(l -> !(l instanceof RemovedLine || l instanceof MetaLine)).count();
        lines.add(String.format("@@ -%d,%d +%d,%d @@", location.startLineSource(), sourceSize, location.startLineTarget(), targetSize));
        content.stream().map(Line::line).forEach(lines::add);
        return lines;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final Hunk hunk = (Hunk) o;
        return Objects.equals(this.toString(), hunk.toString());
    }

    @Override
    public int hashCode() {
        return Objects.hash(toString());
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        for (final String line : this.toLines()) {
            if (!line.contains("\\ No newline at end of file")) {
                sb.append(line).append(System.lineSeparator());
            }
        }
        return sb.toString();
    }
}