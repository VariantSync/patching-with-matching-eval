package org.XXXX-1.evaluation.util.shell;

import java.util.ArrayList;

public class GitCleanCommand extends ShellCommand {
    private static final String COMMAND = "git";
    private static final String SUB_COMMAND = "clean";
    private final ArrayList<String> args = new ArrayList<>();
    private final String pattern;

    public GitCleanCommand(String pattern) {
        this.pattern = pattern;
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

        parts[index + 2] = pattern;
        return parts;
    }

    /**
     * Sets the -f flag that determines whether cleaning should be forced.
     */
    public GitCleanCommand forced() {
        this.args.add("-f");
        return this;
    }
}
