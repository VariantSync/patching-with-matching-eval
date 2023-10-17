package org.variantsync.evaluation.baseline.diff.splitting;

import org.variantsync.evaluation.baseline.diff.components.FileDiff;
import org.variantsync.evaluation.baseline.diff.filter.ILineFilter;
import org.variantsync.evaluation.baseline.diff.lines.Line;

import java.util.List;

/**
 * A context provider is responsible for providing the correct context whenever lines in a patch are filtered.
 * This becomes necessary, because filtering changes from a patch results in a new context for adjacent changes that have
 * not been filtered.
 */
public interface IContextProvider {

    /**
     * Determine the lines in the leading context of a change and return them. The context is determined based on a provided
     * filter that may remove lines from the context.
     *
     * @param lineFilter The line filter that has been used to adjust the patch for which the context is provided
     * @param fileDiff   The file diff containing the hunks for which a new context is to be determined
     * @param index      The line number of the change for which a context is to be provided
     * @return The leading context for the change
     */
    List<Line> leadingContext(ILineFilter lineFilter, FileDiff fileDiff, int index);

    /**
     * Determine the lines in the trailing context of a change and return them. The context is determined based on a provided
     * filter that may remove lines from the context.
     *
     * @param lineFilter The line filter that has been used to adjust the patch for which the context is provided
     * @param fileDiff   The file diff containing the hunks for which a new context is to be determined
     * @param index      The line number of the change for which a context is to be provided
     * @return The trailing context for the change
     */
    List<Line> trailingContext(ILineFilter lineFilter, FileDiff fileDiff, int index, boolean requiresMetaLine);
}