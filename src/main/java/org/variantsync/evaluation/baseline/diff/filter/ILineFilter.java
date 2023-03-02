package org.variantsync.evaluation.baseline.diff.filter;

import java.nio.file.Path;

/**
 * A filter operator that determines whether changes and context lines in a patch's hunk are to be kept in the patch.
 */
public interface ILineFilter {
    /**
     * Decide whether a change to a file at a certain location should be kept in a patch. A change is either an added
     * line or a line that is to be removed.
     *
     * @param filePath The path to the file which is changed
     * @param index    The line number of the change
     * @return true, if the change is to be kept, otherwise false
     */
    boolean keepLineChange(Path filePath, int index);

    /**
     * Decide whether a line in the context of a patch's hunk should be kept in the patch.
     *
     * @param filePath The path to the file which is changed
     * @param index    The line number of the context line
     * @return true, if the context line is to be kept, otherwise false
     */
    default boolean keepContextLine(final Path filePath, final int index) {
        return keepLineChange(filePath, index);
    }
}