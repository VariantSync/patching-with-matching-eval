package org.variantsync.evaluation.util.diff.lines;

/**
 * Represents a removed line of source code in the difference of two files.
 */
public class RemovedLine extends Line {
    public RemovedLine(final String line) {
        super(line);
    }
}