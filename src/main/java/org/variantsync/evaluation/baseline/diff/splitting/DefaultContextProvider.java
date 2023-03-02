package org.variantsync.evaluation.baseline.diff.splitting;

import org.variantsync.evaluation.baseline.diff.components.FileDiff;
import org.variantsync.evaluation.baseline.diff.lines.ContextLine;
import org.variantsync.evaluation.baseline.diff.lines.Line;
import org.variantsync.evaluation.baseline.diff.lines.MetaLine;
import org.variantsync.evaluation.baseline.diff.filter.ILineFilter;
import org.variantsync.vevos.simulation.util.Logger;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;

/**
 * A context provider is responsible for providing the correct context whenever lines in a patch are filtered.
 * This becomes necessary, because filtering changes from a patch results in a new context for adjacent changes that have
 * not been filtered. The DefaultContextProvider determines a new context based on the filters that are used to adjust
 * a patch.
 */
public class DefaultContextProvider implements IContextProvider {
    // The size of the provided context, 3 by default
    private final int contextSize;
    // The working directory in which the files for which the context is to be determined can be found
    private final Path rootDir;

    /**
     * @param rootDir The working directory containing the files for which a context is to be determined
     */
    public DefaultContextProvider(final Path rootDir) {
        // Three is the default size set in unix diff
        this(rootDir, 3);
    }

    /**
     * @param rootDir     The working directory containing the files for which a context is to be determined
     * @param contextSize The size of the provided context (i.e., number of leading/trailing lines)
     */
    public DefaultContextProvider(final Path rootDir, final int contextSize) {
        this.rootDir = rootDir;
        this.contextSize = contextSize;
    }

    @Override
    public List<Line> leadingContext(final ILineFilter lineFilter, final FileDiff fileDiff, final int index) {
        final LinkedList<Line> context = new LinkedList<>();
        final List<String> lines;
        try {
            // Read the file's content
            if (Files.exists(rootDir.resolve(fileDiff.newFile()))) {
                lines = Files.readAllLines(rootDir.resolve(fileDiff.newFile()));
                if (lines.isEmpty()) {
                    return new LinkedList<>();
                }
            } else {
                return new LinkedList<>();
            }

            // Consider the lines coming before the considered change, until the start of the file has been reached, or
            // until all required context lines have been determined
            for (int i = index - 1; i >= 0; i--) {
                final String currentLine = " " + lines.get(i);
                // Apply the line filter to ignore certain lines
                if (lineFilter.keepContextLine(fileDiff.newFile(), i + 1)) {
                    if (context.size() >= contextSize) {
                        break;
                    }
                    context.addFirst(new ContextLine(currentLine));
                }
            }
            return context;
        } catch (final IOException e) {
            Logger.error("Was not able to load file:" + rootDir.resolve(fileDiff.newFile()), e);
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public List<Line> trailingContext(final ILineFilter lineFilter, final FileDiff fileDiff, final int index) {
        final LinkedList<Line> context = new LinkedList<>();
        final List<String> lines;
        try {
            // Read the file's content
            if (Files.exists(rootDir.resolve(fileDiff.oldFile()))) {
                lines = Files.readAllLines(rootDir.resolve(fileDiff.oldFile()));
                if (lines.isEmpty()) {
                    return new LinkedList<>();
                }
            } else {
                return new LinkedList<>();
            }

            // Consider the lines coming after the considered change, until the end of the file has been reached, or
            // until all required context lines have been determined
            for (int i = index - 1; i < lines.size(); i++) {
                final String currentLine = " " + lines.get(i);
                // Apply the line filter to ignore certain lines
                if (lineFilter.keepContextLine(fileDiff.oldFile(), i + 1)) {
                    if (context.size() >= contextSize) {
                        break;
                    }
                    context.addLast(new ContextLine(currentLine));
                }
                if (i == lines.size() - 1) {
                    // Add a meta-line stating EOF 
                    context.addLast(new MetaLine());
                }
            }
            return context;
        } catch (final IOException e) {
            Logger.error("Was not able to load file:" + rootDir.resolve(fileDiff.newFile()), e);
            throw new UncheckedIOException(e);
        }
    }
}