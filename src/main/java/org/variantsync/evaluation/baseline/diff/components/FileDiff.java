package org.variantsync.evaluation.baseline.diff.components;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A FileDiff holds the difference between two versions of a specific file.
 *
 * @param header  The header of UNIX diff's output
 * @param hunks   The hunks (i.e., change groups) of the file's difference
 * @param oldFile The path to the old version of the file
 * @param newFile The path to the new version of the file
 */
public record FileDiff(List<String> header, List<Hunk> hunks, Path oldFile, Path newFile) implements IDiffComponent {
    @Override
    public List<String> toLines() {
        final List<String> lines = new ArrayList<>(header);
        hunks.stream().map(IDiffComponent::toLines).forEach(lines::addAll);
        return lines;
    }
}