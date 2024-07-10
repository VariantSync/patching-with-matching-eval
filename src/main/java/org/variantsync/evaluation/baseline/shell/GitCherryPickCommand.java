package org.variantsync.evaluation.baseline.shell;

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
        return new GitCherryPickCommand(cherry).allowEmpty().allowEmptyMessage().ours();
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

        parts[index + 2] = this.cherry;
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

    public GitCherryPickCommand ours() {
        this.args.add("--strategy=ours");
        return this;
    }

    public GitCherryPickCommand cont() {
        this.args.add("--continue");
        return this;
    }

    @Override
    public String toString() {
        return "git apply: " + Arrays.toString(parts());
    }
}
