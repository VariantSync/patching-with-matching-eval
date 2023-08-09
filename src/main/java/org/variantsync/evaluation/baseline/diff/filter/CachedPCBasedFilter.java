package org.variantsync.evaluation.baseline.diff.filter;

import org.variantsync.vevos.simulation.feature.Variant;
import org.variantsync.vevos.simulation.variability.pc.Artefact;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * CachedPCBasedFilter extends PCBasedFilter by caching previously made decisions regarding keeping files or changed lines. This
 * effectively reduces the number of required IO operations leading to improved runtime.
 */
public class CachedPCBasedFilter extends PCBasedFilter {
    // Cache for previously made filter decisions
    private final Map<ChangeLocation, Boolean> keepDecisions = new HashMap<>();

    /**
     * @param oldTraces      The presence conditions of the old version of the source variant
     * @param newTraces      The presence conditions of the new version of the source variant
     * @param targetVariant  The target variant
     * @param oldVersionRoot The path to the source code of the old version of the source variant
     * @param newVersionRoot The path to the source code of the new version of the source variant
     * @param strip          The number of leading components in each path that are to be ignored when determining the location of a patch
     */
    public CachedPCBasedFilter(final Artefact oldTraces, final Artefact newTraces, final Variant targetVariant, final Path oldVersionRoot, final Path newVersionRoot, final int strip) {
        super(oldTraces, newTraces, targetVariant, oldVersionRoot, newVersionRoot, strip);
    }

    @Override
    public boolean keepLineChange(final Path filePath, final int index) {
        final ChangeLocation editLocation = new ChangeLocation(filePath, index);
        final boolean keep = super.keepLineChange(filePath, index);
        keepDecisions.put(editLocation, keep);
        return keep;
    }

    @Override
    public boolean keepContextLine(final Path filePath, final int index) {
        final ChangeLocation changeLocation = new ChangeLocation(filePath, index);
        return keepDecisions.getOrDefault(changeLocation, true);
    }

    /**
     * An ChangeLocation is determined by the file and line number
     *
     * @param filePath The path to the changed file
     * @param index    The line number of the changed line
     */
    protected record ChangeLocation(Path filePath, int index) {
        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            final ChangeLocation that = (ChangeLocation) o;
            return index == that.index && Objects.equals(filePath, that.filePath);
        }

        @Override
        public int hashCode() {
            return Objects.hash(filePath, index);
        }
    }
}
