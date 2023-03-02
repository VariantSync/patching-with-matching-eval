package org.variantsync.evaluation.baseline.diff.filter;

import org.variantsync.evaluation.baseline.diff.components.FileDiff;

/**
 * A filter operator that decides whether the changes to a specific file should be part
 * of a patch or not.
 */
public interface IFileDiffFilter {

    /**
     * Decide whether the given file difference should be kept in a patch.
     *
     * @param fileDiff The file difference to consider
     * @return true, if the difference should be kept in the patch
     */
    boolean keepFileDiff(FileDiff fileDiff);
}