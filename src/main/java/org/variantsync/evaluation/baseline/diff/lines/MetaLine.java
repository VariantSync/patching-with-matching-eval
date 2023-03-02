package org.variantsync.evaluation.baseline.diff.lines;

/**
 * Represents lines with metadata (e.g., information about EOF) in the difference of two files.
 */
public class MetaLine extends Line {
    public MetaLine(final String line) {
        super(line);
    }

    public MetaLine() {
        super("\\ No newline at end of file");
    }
}