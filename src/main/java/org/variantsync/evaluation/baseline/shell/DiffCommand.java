package org.variantsync.evaluation.baseline.shell;

import org.variantsync.evaluation.error.ShellException;
import org.variantsync.functjonal.Result;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Represents a shell 'diff' command that can be executed using a ShellExecutor
 */
public class DiffCommand extends ShellCommand {
    private static final String COMMAND = "diff";
    private final String[] files;
    private final ArrayList<String> args = new ArrayList<>();

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
                .recursive()
                .ignoreTrailingSpace();
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

    /**
     * Ignore white space at line end
     *
     * @return this command
     */
    public DiffCommand ignoreTrailingSpace() {
        args.add("-Z");
        return this;
    }

    /**
     * Ignore changes where lines are all blank
     *
     * @return this command
     */
    public DiffCommand ignoreBlankLines() {
        args.add("-B");
        return this;
    }

    public DiffCommand exclude(final String pattern) {
        args.add("--exclude=" + pattern);
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