package org.variantsync.evaluation.baseline.diff.components;

import org.variantsync.evaluation.baseline.diff.lines.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * A Hunk represents a changed text block in the difference between two versions of a file.
 */
public final class Hunk implements IDiffComponent {
    private final HunkLocation location;
    private final HunkLocation rawLocation;
    private final List<Line> allLines;
    private final List<Line> changedLines;
    private final boolean hasMetaLine;

    /**
     * @param location The location of the hunk in the file
     * @param allLines  The content of the hunk (i.e., context and changed lines)
     */
    public Hunk(HunkLocation location, HunkLocation rawLocation, List<Line> allLines) {
        this.location = location;
        this.rawLocation = rawLocation;
        this.allLines = allLines;
        this.changedLines = allLines.stream().filter(l -> (l instanceof AddedLine || l instanceof RemovedLine)).collect(Collectors.toList());
        this.hasMetaLine = allLines.stream().anyMatch(l -> l instanceof MetaLine);
    }

    public List<Line> changedLines() {
        return this.changedLines;
    }

    @Override
    public List<String> toLines() {
        final List<String> lines = new ArrayList<>();
        final int sourceSize = (int) allLines.stream().filter(l -> !(l instanceof AddedLine || l instanceof MetaLine)).count();
        final int targetSize = (int) allLines.stream().filter(l -> !(l instanceof RemovedLine || l instanceof MetaLine)).count();
        lines.add(String.format("@@ -%d,%d +%d,%d @@", location.startLineSource(), sourceSize, location.startLineTarget(), targetSize));
        allLines.stream().map(Line::line).forEach(lines::add);
        return lines;
    }

    @Override
    public int changeCount() {
        return this.changedLines.size();
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

    public HunkLocation location() {
        return location;
    }

    public HunkLocation rawLocation() {
        return rawLocation;
    }

    public List<Line> content() {
        return allLines;
    }

    public boolean hasMetaLine() {
        return hasMetaLine;
    }

    public Hunk inverse() {
        List<Line> lines = new ArrayList<>(this.allLines.size());

        for (Line l : this.allLines) {
            String changedText = l.line().substring(1);
            Line inverse;
            if (l instanceof AddedLine) {
                inverse =
                        new RemovedLine("-" + changedText);
            } else if (l instanceof RemovedLine) {
                inverse = new AddedLine("+" + changedText);
            } else {
                inverse = l;
            }
            lines.add(inverse);
        }

        return new Hunk(this.location, this.rawLocation, lines);
    }

}