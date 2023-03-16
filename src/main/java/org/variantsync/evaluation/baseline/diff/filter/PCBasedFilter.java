package org.variantsync.evaluation.baseline.diff.filter;

import org.prop4j.Node;
import org.variantsync.evaluation.baseline.diff.components.FileDiff;
import org.variantsync.evaluation.error.Panic;
import org.variantsync.functjonal.Result;
import org.variantsync.vevos.simulation.feature.Variant;
import org.tinylog.Logger;
import org.variantsync.vevos.simulation.util.io.CaseSensitivePath;
import org.variantsync.vevos.simulation.variability.pc.Artefact;

import java.nio.file.Path;

/**
 * A PCBasedFilter is a IFileDiffFilter and ILineFilter that determines which parts of a patch should be kept based on
 * the presence conditions of the source code and the configuration of the target variant.
 */
public class PCBasedFilter implements IFileDiffFilter, ILineFilter {
    // Presence conditions of the old version of the variant
    protected final Artefact oldTraces;
    // Presence conditions of the new versions of the variant
    protected final Artefact newTraces;
    // The target variant
    protected final Variant targetVariant;
    // The path to the source code of the old version of the source variant
    protected final Path oldVersion;
    // The path to the source code of the new version of the source variant
    protected final Path newVersion;
    // The number of leading components in each path that are to be ignored when determining the location of a patch
    // e.g., a strip of 3 would lead to the patch only considering 'file.c' in the path '/home/user/variant1/file.c'
    protected final int strip;

    /**
     * @param oldTraces      The presence conditions of the old version of the source variant
     * @param newTraces      The presence conditions of the new version of the source variant
     * @param targetVariant  The target variant
     * @param oldVersionRoot The path to the source code of the old version of the source variant
     * @param newVersionRoot The path to the source code of the new version of the source variant
     */
    public PCBasedFilter(final Artefact oldTraces, final Artefact newTraces, final Variant targetVariant, final Path oldVersionRoot, final Path newVersionRoot) {
        this.oldTraces = oldTraces;
        this.newTraces = newTraces;
        this.targetVariant = targetVariant;
        this.oldVersion = oldVersionRoot;
        this.newVersion = newVersionRoot;
        this.strip = 0;
    }

    /**
     * @param oldTraces      The presence conditions of the old version of the source variant
     * @param newTraces      The presence conditions of the new version of the source variant
     * @param targetVariant  The target variant
     * @param oldVersionRoot The path to the source code of the old version of the source variant
     * @param newVersionRoot The path to the source code of the new version of the source variant
     * @param strip          The number of leading components in each path that are to be ignored when determining the location of a patch
     */
    public PCBasedFilter(final Artefact oldTraces, final Artefact newTraces, final Variant targetVariant, final Path oldVersionRoot, final Path newVersionRoot, final int strip) {
        this.oldTraces = oldTraces;
        this.newTraces = newTraces;
        this.targetVariant = targetVariant;
        this.oldVersion = oldVersionRoot;
        this.newVersion = newVersionRoot;
        this.strip = strip;
    }

    /**
     * Determine whether a change to a line is to be kept in a patch depending on the given presence conditions.
     *
     * @param targetVariant The variant that is to be patched
     * @param traces        The presence conditions that are to be checked
     * @param filePath      The path to the file in which the change occurs
     * @param index         The line number of the changed line
     * @return true, if the change is to be kept in the patch
     */
    protected boolean keepLineChange(final Variant targetVariant, final Artefact traces, Path filePath, final int index) {
        filePath = filePath.subpath(strip, filePath.getNameCount());
        final Result<Node, Exception> pc = traces
                .getPresenceConditionOf(new CaseSensitivePath(filePath), index);
        if (pc.isSuccess()) {
            return targetVariant.isImplementing(pc.getSuccess());
        } else {
            Logger.debug("There is no PC for line " + index + " of " + filePath);
            return false;
        }
    }

    /**
     * Determine whether all changes to a file are to be kept in a patch depending on the given presence conditions
     *
     * @param targetVariant The variant that is to be patched
     * @param traces        The presence conditions that are to be considered
     * @param filePath      The path to the file whose difference is to be considered
     * @return true, if the file's difference is to be kept in the patch
     */
    protected boolean keepFileDiff(final Variant targetVariant, final Artefact traces, Path filePath) {
        filePath = filePath.subpath(strip, filePath.getNameCount());
        final Result<Node, Exception> result = traces.getPresenceConditionOf(new CaseSensitivePath(filePath));
        if (result.isFailure()) {
            Logger.debug("There is no PC found for " + filePath);
            return false;
        } else {
            final Node pc = result.getSuccess();
            return targetVariant.isImplementing(pc);
        }
    }

    @Override
    public boolean keepFileDiff(final FileDiff fileDiff) {
        // We keep the file's difference if the file is part of the old or the new version of the source variant
        return keepFileDiff(targetVariant, oldTraces, fileDiff.oldFile()) || keepFileDiff(targetVariant, newTraces, fileDiff.newFile());
    }

    @Override
    public boolean keepLineChange(final Path filePath, final int index) {
        if (oldVersion.endsWith(filePath.getName(0))) {
            return keepLineChange(targetVariant, oldTraces, filePath, index);
        } else if (newVersion.endsWith(filePath.getName(0))) {
            return keepLineChange(targetVariant, newTraces, filePath, index);
        } else {
            final String message = "The given path '" + filePath + "' does not match any of the versions' paths";
            Logger.error(message);
            throw new Panic(message);
        }
    }
}
