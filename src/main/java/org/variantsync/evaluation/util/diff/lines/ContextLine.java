package org.variantsync.evaluation.util.diff.lines;

/**
 * Represents a context line in a hunk of the difference between two files.
 */
public class ContextLine extends Line {
    public ContextLine(final String line) {
        super(line);
    }

    /**
     * Create a context line from an added line. This is required to represent the context of an adjacent change after
     * the line has actually been added.
     *
     * @param line the added line
     */
    public ContextLine(final AddedLine line) {
        super(line.line().replaceFirst("\\+", " "));
    }

    /**
     * Create a context line from a removed line. This is required to represent the context of an adjacent change before
     * the line has actually been removed.
     *
     * @param removedLine the removed line
     */
    public ContextLine(final RemovedLine removedLine) {
        super(removedLine.line().replaceFirst("-", " "));
    }
}