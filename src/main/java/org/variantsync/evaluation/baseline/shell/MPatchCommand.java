package org.variantsync.evaluation.baseline.shell;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Represents a shell 'patch' command that can be executed using a ShellExecutor
 */
public class MPatchCommand extends ShellCommand {
    private static final String COMMAND = "mpatch";
    private final ArrayList<String> args = new ArrayList<>();

    /**
     * A MPatchCommand configured as recommended in the documentation of 'patch'
     *
     * @param patchFile The file containing the patch
     * @return A configured MPatchCommand
     */
    public static MPatchCommand Recommended(final Path sourceDir, final Path patchFile) {
        return new MPatchCommand().sourceDir(sourceDir).patchFile(patchFile);
    }

    /**
     * Strip the smallest prefix containing num leading slashes from each file name found in the patch
     * file. A sequence of one or more adjacent slashes is counted as a single slash. This controls
     * how file names found in the patch file are treated, in case you keep your files in a different
     * directory than the person who sent out the patch. For example, supposing the file name in the
     * patch file was
     *
     * @param number how many leading slashes to slash from the file name
     * @return this command
     */
    public MPatchCommand strip(final int number) {
        this.args.add("--strip=" + number);
        return this;
    }

    /**
     * Put rejects into rejectPath instead of the default .rej file. When rejectsfile is -, discard rejects.
     **/
    public MPatchCommand rejectsFile(final Path rejectPath) {
        this.args.add("--rejectsfile=" + rejectPath);
        return this;
    }

    /**
     * Read the patch from patchfile.  If patchfile is -, read from standard input, the default.
     *
     * @param patchFile the file to load
     * @return this command
     */
    public MPatchCommand patchFile(final Path patchFile) {
        this.args.add("--patchfile=" + patchFile);
        return this;
    }

    public MPatchCommand sourceDir(final Path sourceDif) {
        this.args.add("--sourcedir=" + sourceDif);
        return this;
    }

    public MPatchCommand dryrun() {
        this.args.add("--dryrun");
        return this;
    }

    public MPatchCommand maxMatchDistance(final int maxMatchDistance) {
        this.args.add("--max_match_distance=" + maxMatchDistance);
        return this;
    }

    @Override
    public String[] parts() {
        final String[] parts = new String[args.size() + 1];

        parts[0] = COMMAND;
        int index = 0;
        for (; index < args.size(); index++) {
            parts[index + 1] = args.get(index);
        }
        //for (var part : parts) {
        //     System.out.print(part + " ");
        //  }
        //  System.out.println();
        return parts;
    }

    @Override
    public String toString() {
        return "mpatch: " + Arrays.toString(parts());
    }
}