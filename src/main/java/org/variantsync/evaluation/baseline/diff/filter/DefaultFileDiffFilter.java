package org.variantsync.evaluation.baseline.diff.filter;

import org.variantsync.evaluation.baseline.diff.components.FileDiff;

/**
 * A default filter operator for file-level patches. A filter decides whether the changes to a specific file should be part
 * of a patch or not. The default operator always returns true; thus, all file patches are kept.
 */
public class DefaultFileDiffFilter implements IFileDiffFilter {

    @Override
    public boolean keepFileDiff(final FileDiff fileDiff) {
        return true;
    }
}