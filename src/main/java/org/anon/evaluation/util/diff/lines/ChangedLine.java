package org.anon.evaluation.util.diff.lines;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Represents a change to a file
 *
 * @param file the changed file
 * @param line the changed line in the file
 */
public record ChangedLine(Path file, Line line) {

    @Override
    public String toString() {
        return file + "\n" + line;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChangedLine that = (ChangedLine) o;
        return Objects.equals(file, that.file) && Objects.equals(line, that.line);
    }

    @Override
    public int hashCode() {
        return Objects.hash(file, line);
    }
}
