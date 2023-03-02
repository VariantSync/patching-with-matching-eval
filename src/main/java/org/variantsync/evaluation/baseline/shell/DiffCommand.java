package org.variantsync.evaluation.baseline.shell;

import org.variantsync.functjonal.Result;
import org.variantsync.evaluation.error.ShellException;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Represents a shell 'diff' command that can be executed using a ShellExecutor
 */
public class DiffCommand extends ShellCommand {
    private static final String COMMAND = "diff";
    private final String[] files;
    private final LinkedList<String> args = new LinkedList<>();

    public DiffCommand(final String... files) {
        this.files = files;
    }

    /**
     * DiffCommand configured as recommended by the documentation in 'patch' (i.e. 'man patch')
     *
     * @param pathA The path to the source file
     * @param pathB The path to the target file
     * @return A configured DiffCommand
     */
    public static DiffCommand Recommended(final Path pathA, final Path pathB) {
        return new DiffCommand(pathA.toString(), pathB.toString())
                .newFile()
                .text()
                .unified()
                .recursive();
    }

    @Override
    public String[] parts() {
        final String[] parts = new String[files.length + args.size() + 1];

        parts[0] = COMMAND;
        int index = 0;
        for (; index < args.size(); index++) {
            parts[index + 1] = args.get(index);
        }
        for (; index < args.size() + files.length; index++) {
            parts[index + 1] = files[index - args.size()];
        }
        return parts;
    }

    /**
     * Treat absent files as empty
     *
     * @return this command
     */
    public DiffCommand newFile() {
        args.add("-N");
        return this;
    }

    /**
     * Treat all files as text
     *
     * @return this command
     */
    public DiffCommand text() {
        args.add("-a");
        return this;
    }

    /**
     * Output three lines of unified context
     *
     * @return this command
     */
    public DiffCommand unified() {
        args.add("-u");
        return this;
    }

    /**
     * Recursively compare any subdirectories found
     *
     * @return this command
     */
    public DiffCommand recursive() {
        args.add("-r");
        return this;
    }

    @Override
    public String toString() {
        return "diff: " + Arrays.toString(parts());
    }

    @Override
    public Result<List<String>, ShellException> interpretResult(final int code, final List<String> output) {
        return code == 0 || code == 1 ? Result.Success(output) : Result.Failure(new ShellException(output));
    }
}