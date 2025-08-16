package org.variantsync.evaluation.util.shell;

import org.tinylog.Logger;

import java.util.ArrayList;

public class GitConfigCommand extends ShellCommand {
    private static final String COMMAND = "git";
    private static final String SUB_COMMAND = "config";
    private final ArrayList<String> args = new ArrayList<>();

    private GitConfigCommand() {
    }

    @Override
    public String[] parts() {
        final String[] parts = new String[args.size() + 2];

        parts[0] = COMMAND;
        parts[1] = SUB_COMMAND;
        int index = 0;
        for (; index < args.size(); index++) {
            parts[index + 2] = args.get(index);
        }
        return parts;
    }

    public static GitConfigCommand DisableGPGSignLocally() {
        final GitConfigCommand command = new GitConfigCommand();
        command.args.add("--local");
        command.args.add("commit.gpgsign");
        command.args.add("false");
        return command;
    }

}
