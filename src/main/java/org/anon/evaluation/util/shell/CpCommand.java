package org.anon.evaluation.util.shell;

import java.nio.file.Path;
import java.util.ArrayList;

/**
 * Represent a shell 'cp' command that can be executed using the ShellExecutor
 */
public class CpCommand extends ShellCommand {
    private static final String COMMAND = "cp";
    private final String from;
    private final String to;
    private final ArrayList<String> args = new ArrayList<>();


    public CpCommand(final Path from, final Path to) {
        this.from = from.toString();
        this.to = to.toString();
    }

    /**
     * copy directories recursively
     *
     * @return this command
     */
    public CpCommand recursive() {
        this.args.add("-r");
        return this;
    }

    @Override
    public String[] parts() {
        final String[] parts = new String[args.size() + 3];

        parts[0] = COMMAND;
        int index = 0;
        for (; index < args.size(); index++) {
            parts[index + 1] = args.get(index);
        }
        parts[index + 1] = from;
        parts[index + 2] = to;
        return parts;
    }
}