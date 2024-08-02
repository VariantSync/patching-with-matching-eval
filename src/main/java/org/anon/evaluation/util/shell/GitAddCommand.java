package org.anon.evaluation.util.shell;

public class GitAddCommand extends ShellCommand{
    private static final String COMMAND = "git";
    private static final String SUB_COMMAND = "add";
    private final String pattern;

    public GitAddCommand(String pattern) {
        this.pattern = pattern;
    }

    @Override
    public String[] parts() {
        final String[] parts = new String[3];

        parts[0] = COMMAND;
        parts[1] = SUB_COMMAND;
        parts[2] = pattern;
        return parts;
    }
}
