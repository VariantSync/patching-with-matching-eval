package org.variantsync.evaluation.util.diff;

import org.tinylog.Logger;
import org.variantsync.evaluation.util.diff.components.FileDiff;
import org.variantsync.evaluation.util.diff.components.Hunk;
import org.variantsync.evaluation.util.diff.components.HunkLocation;
import org.variantsync.evaluation.util.diff.components.OriginalDiff;
import org.variantsync.evaluation.util.diff.lines.*;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * DiffParser provides the functionality for parsing and converting the difference determined by UNIX diff into our
 * internal representation.
 */
public class DiffParser {

    /**
     * Parse the given lines of text into a OriginalDiff object that represents the difference between two versions of
     * a software project.
     *
     * @param lines The lines from UNIX diff's output that are to be parsed.
     * @return An OriginalDiff instance representing the parsed difference
     */
    public static OriginalDiff toOriginalDiff(final List<String> lines) {
        // The diff is empty, but this is also a valid scenario
        if (lines.isEmpty()) {
            return new OriginalDiff(new ArrayList<>());
        }
        final List<FileDiff> fileDiffs = new ArrayList<>();
        // Determine the substring which a FileDiff starts with
        String fileDiffStart = "";
        String fileDiffFollow = "";
        String firstRelevantLine = null;
        for (final String line : lines) {
            if (!(line.startsWith("Binary files "))) {
                // The first relevant line for determining the file diff start must not belong to a binary file
                firstRelevantLine = line;
                break;
            }
        }

        if (firstRelevantLine == null) {
            // In this case, there are only binary files in the diff, and we can return early
            // We only consider non-binary files, because binary files crash our patchers, which makes a decent evaluation impossible
            return new OriginalDiff(fileDiffs);
        }

        if (firstRelevantLine.startsWith("diff")) {
            // Several files were processed, the diff of each file starts with the 'diff' command that was used
            fileDiffStart = "diff";
            fileDiffFollow = "--- ";
        } else if (firstRelevantLine.startsWith("--- ")) {
            // Only one file was processed, the diff of the file starts with the hunk header
            fileDiffStart = "--- ";
            fileDiffFollow = "+++ ";
        }

        List<String> fileDiffContent = null;
        int indexNext = 0;
        for (final String line : lines) {
            indexNext++;

            if (line.startsWith("Binary files ")) {
                // We only consider non-binary files, because binary files crash our patchers, which makes a decent evaluation impossible
                continue;
            }

            if (line.startsWith(fileDiffStart)) {
                if (indexNext < lines.size()) {
                    final String nextLine = lines.get(indexNext);
                    if (nextLine.startsWith(fileDiffFollow)) {
                        // Create a FileDiff from the collected lines
                        if (fileDiffContent != null) {
                            fileDiffs.add(parseFileDiff(fileDiffContent));
                        }
                        // Reset the lines that should go into the next FileDiff
                        fileDiffContent = new ArrayList<>();
                    }
                }
            }
            if (fileDiffContent == null) {
                throw new IllegalArgumentException("The provided lines do not contain one of the expected fileDiffStart values");
            }
            fileDiffContent.add(line);
        }

        // Parse the content of the last file diff
        if (fileDiffContent != null) {
            fileDiffs.add(parseFileDiff(fileDiffContent));
        }

        return new OriginalDiff(fileDiffs);
    }

    // Parse and convert the lines belonging to the difference of a specific file
    public static FileDiff parseFileDiff(final List<String> fileDiffContent) {
        int index = 0;
        final String HUNK_START = "@@ -";
        String nextLine = fileDiffContent.get(index);

        // Parse the header
        final List<String> header = new ArrayList<>();
        String oldFile = null;
        String newFile = null;
        {
            boolean atHeader = true;
            while (atHeader) {
                if (nextLine.startsWith("--- ")) {
                    oldFile = nextLine.split("\\s+")[1];
                } else if (nextLine.startsWith("+++ ")) {
                    newFile = nextLine.split("\\s")[1];
                }
                header.add(nextLine);
                index++;
                try {
                    nextLine = fileDiffContent.get(index);
                } catch (Exception e) {
                    Logger.warn(e);
                    throw e;
                }
                if (nextLine.startsWith(HUNK_START)) {
                    atHeader = false;
                }
            }
        }

        // Parse the hunks
        final List<Hunk> hunks = new ArrayList<>();
        {
            List<String> hunkLines = new ArrayList<>();
            hunkLines.add(nextLine);
            for (index += 1; index < fileDiffContent.size(); index++) {
                nextLine = fileDiffContent.get(index);
                if (nextLine.startsWith(HUNK_START)) {
                    hunks.add(parseHunk(hunkLines));
                    hunkLines = new ArrayList<>();
                }
                hunkLines.add(nextLine);
            }
            // Parse the content of the last hunk
            hunks.add(parseHunk(hunkLines));
        }

        return new FileDiff(header, hunks, Paths.get(Objects.requireNonNull(oldFile)), Paths.get(Objects.requireNonNull(newFile)));
    }

    // Parse and convert the lines belonging to the difference of a specific hunk
    public static Hunk parseHunk(final List<String> lines) {
        // Parse the header
        final HunkLocation location = parseHunkHeader(lines.get(0));
        final List<Line> content = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            final String line = lines.get(i);
            if (line.startsWith("+")) {
                content.add(new AddedLine(line));
            } else if (line.startsWith("-")) {
                content.add(new RemovedLine(line));
            } else if (line.startsWith("\\")) {
                content.add(new MetaLine(line));
            } else {
                content.add(new ContextLine(line));
            }
        }
        return new Hunk(location, location, content);
    }

    // Parse and convert the header of a hunk
    public static HunkLocation parseHunkHeader(final String line) {
        final String[] parts = line.split("\\s+");
        final String sourceLocationString = parts[1].substring(1);
        final String targetLocationString = parts[2].substring(1);

        return new HunkLocation(Integer.parseInt(sourceLocationString.split(",")[0]), Integer.parseInt(targetLocationString.split(",")[0]));
    }
}