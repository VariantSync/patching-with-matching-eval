package org.variantsync.evaluation.baseline.diff.lines;

import java.nio.file.Path;

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
}
