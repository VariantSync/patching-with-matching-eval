package org.variantsync.evaluation.baseline.diff.lines;

import java.nio.file.Path;

/**
 * Represents a change to a file
 *
 * @param file the changed file
 * @param line the changed line in the file
 */
public record ChangeLine(Path file, Line line) {
}
