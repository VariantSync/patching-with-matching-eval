package org.XXXX-1.evaluation.util.shell;

import java.util.ArrayList;
import java.util.Arrays;

public class GitCherryPickCommand extends ShellCommand{
    private static final String COMMAND = "git";
    private static final String SUB_COMMAND = "cherry-pick";
    private final String cherry;
    private final ArrayList<String> args = new ArrayList<>();

    public GitCherryPickCommand() {
        this.cherry = "";
    }

    public GitCherryPickCommand(String cherry) {
        this.cherry = cherry;
    }

    public static GitCherryPickCommand Recommended(final String cherry) {
        return new GitCherryPickCommand(cherry).allowEmpty().allowEmptyMessage();
    }

    @Override
    public String[] parts() {
        final String[] parts;
        if (cherry.isEmpty()) {
            parts = new String[args.size() + 2];
        } else
        {
            parts = new String[args.size() + 3];
        }

        parts[0] = COMMAND;
        parts[1] = SUB_COMMAND;
        int index = 0;
        for (; index < args.size(); index++) {
            parts[index + 2] = args.get(index);
        }

        if (!cherry.isEmpty()) {
            parts[index + 2] = this.cherry;
        }
        return parts;
    }

    public GitCherryPickCommand allowEmpty() {
        this.args.add("--allow-empty");
        return this;
    }

    public GitCherryPickCommand allowEmptyMessage() {
        this.args.add("--allow-empty-message");
        return this;
    }

    public GitCherryPickCommand cont() {
        this.args.add("--continue");
        return this;
    }

    public GitCherryPickCommand abort() {
        this.args.add("--abort");
        return this;
    }

    @Override
    public String toString() {
        return "git cherry-pick: " + Arrays.toString(parts());
    }
}
