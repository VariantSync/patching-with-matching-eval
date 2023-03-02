package org.variantsync.evaluation.baseline.shell;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedList;

/**
 * Represents a shell 'patch' command that can be executed using a ShellExecutor
 */
public class PatchCommand extends ShellCommand {
    private static final String COMMAND = "patch";
    private final LinkedList<String> args = new LinkedList<>();

    /**
     * A PatchCommand configured as recommended in the documentation of 'patch'
     *
     * @param patchFile The file containing the patch
     * @return A configured PatchCommand
     */
    public static PatchCommand Recommended(final Path patchFile) {
        return new PatchCommand().input(patchFile).forward().strip(1).noBackup();
    }

    /**
     * When a patch does not apply, patch usually checks if the patch looks like it has been applied
     * already by trying to reverse- apply the first hunk. The --forward option prevents that. See
     * also -R.
     *
     * @return this command
     */
    public PatchCommand forward() {
        this.args.add("--forward");
        return this;
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
    public PatchCommand strip(final int number) {
        this.args.add("--strip=" + number);
        return this;
    }

    /**
     * Send output to outfile instead of patching files in place. Do not use this option if outfile is
     * one of the files to be patched. When outfile is -, send output to standard output, and send any
     * messages that would usually go to standard output to standard error.
     *
     * @param outputPath The outfile path
     * @return this command
     */
    public PatchCommand outfile(final Path outputPath) {
        this.args.add("--output=" + outputPath);
        return this;
    }

    /**
     * Put rejects into rejectPath instead of the default .rej file. When rejectfile is -, discard rejects.
     **/
    public PatchCommand rejectFile(final Path rejectPath) {
        this.args.add("--reject-file=" + rejectPath);
        return this;
    }

    /**
     * Read the patch from patchfile.  If patchfile is -, read from standard input, the default.
     *
     * @param patchFile the file to load
     * @return this command
     */
    public PatchCommand input(final Path patchFile) {
        this.args.add("--input=" + patchFile);
        return this;
    }

    /**
     * Do not back up a file if the patch does not match the file
     * exactly and if backups are not otherwise requested.  This is
     * the default if patch is conforming to POSIX.
     *
     * @return this command
     */
    public PatchCommand noBackup() {
        this.args.add("--no-backup-if-mismatch");
        return this;
    }

    /**
     * Assume that the user knows exactly what he or she is doing,
     * and do not ask any questions.  Skip patches whose headers do
     * not say which file is to be patched; patch files even though
     * they have the wrong version for the Prereq: line in the patch;
     * and assume that patches are not reversed even if they look
     * like they are.
     *
     * @return this command
     */
    public PatchCommand force() {
        this.args.add("--force");
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
        return parts;
    }

    @Override
    public String toString() {
        return "patch: " + Arrays.toString(parts());
    }

}