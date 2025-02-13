package org.XXXX-1.evaluation.util.shell;

import java.util.ArrayList;
import java.util.Arrays;

public class GitCheckoutCommand extends ShellCommand{
    private static final String COMMAND = "git";
    private static final String SUB_COMMAND = "checkout";
    private final String commitID;
    private final ArrayList<String> args = new ArrayList<>();

    public GitCheckoutCommand(String cherry) {
        this.commitID = cherry;
    }

    public static GitCheckoutCommand Recommended(final String cherry) {
        return new GitCheckoutCommand(cherry).force();
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

        parts[index + 2] = this.commitID;
        return parts;
    }

        public GitCheckoutCommand force() {
        this.args.add("--force");
        return this;
    }

    @Override
    public String toString() {
        return "git checkout: " + Arrays.toString(parts());
    }
}
