package org.variantsync.evaluation.baseline.diff.filter;

import java.nio.file.Path;

/**
 * A default filter operator for line-level patches. A filter decides whether a change to a specific line should be part
 * of a patch or not. The default operator always returns true; thus, all line-level patches are kept.
 */
public class DefaultLineFilter implements ILineFilter {
    @Override
    public boolean keepLineChange(final Path filePath, final int index) {
        return true;
    }
}