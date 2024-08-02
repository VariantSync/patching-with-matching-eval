package org.anon.evaluation.util.shell;

import java.nio.file.Path;
import java.util.ArrayList;

/**
 * Represents a shell 'rm' command that can be executed using a ShellExecutor
 */
public class RmCommand extends ShellCommand {
    private static final String COMMAND = "rm";
    private final String file;
    private final ArrayList<String> args = new ArrayList<>();

    public RmCommand(final Path file) {
        this.file = file.toString();
    }

    public RmCommand(final String file) {
        this.file = file;
    }

    @Override
    public String[] parts() {
        final String[] parts = new String[args.size() + 2];

        parts[0] = COMMAND;
        int index = 0;
        for (; index < args.size(); index++) {
            parts[index + 1] = args.get(index);
        }

        parts[index + 1] = file;
        return parts;
    }

    public RmCommand recursive() {
        this.args.add("-r");
        return this;
    }

    public RmCommand force() {
        this.args.add("-f");
        return this;
    }
}
