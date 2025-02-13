package org.XXXX-1.evaluation.util.shell;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;

public class GitApplyCommand extends ShellCommand {
    private static final String COMMAND = "git";
    private static final String SUB_COMMAND = "apply";
    private final Path patchFile;
    private final ArrayList<String> args = new ArrayList<>();

    public GitApplyCommand(Path patchFile) {
        this.patchFile = patchFile;
    }

    /**
     * A MPatchCommand configured as recommended in the documentation of 'patch'
     *
     * @param patchFile The file containing the patch
     * @return A configured MPatchCommand
     */
    public static GitApplyCommand Recommended(final Path patchFile) {
        return new GitApplyCommand(patchFile);
    }

    /**
     * Remove <n> leading path components (separated by slashes) from traditional diff
     * paths. E.g., with -p2, a patch against a/dir/file will be applied directly to file.
     * The default is 1.
     *
     * @param number how many leading slashes to slash from the file name
     * @return this command
     */
    public GitApplyCommand strip(final int number) {
        this.args.add("-p" + number);
        return this;
    }

    /**
     * For atomicity, git apply by default fails the whole patch and does not touch the
     * working tree when some of the hunks do not apply. This option makes it apply the
     * parts of the patch that are applicable, and leave the rejected hunks in
     * corresponding *.rej files.
     **/
    public GitApplyCommand reject() {
        this.args.add("--reject");
        return this;
    }

    @Override
    public String[] parts() {
        final String[] parts = new String[args.size() + 3];

        parts[0] = COMMAND;
        parts[1] = SUB_COMMAND;
        int index = 0;
        for (; index < args.size(); index++) {
            parts[index + 2] = args.get(index);
        }

        parts[index + 2] = this.patchFile.toString();
        return parts;
    }

    @Override
    public String toString() {
        return "git apply: " + Arrays.toString(parts());
    }
}