package org.variantsync.evaluation.util.shell;

import java.util.Arrays;

/**
 * Represents a shell 'echo' command that can be executed using a ShellExecutor
 */
public class EchoCommand extends ShellCommand {
    private final String COMMAND = "echo";
    private final String message;

    public EchoCommand(final String echoMessage) {
        this.message = echoMessage;
    }

    @Override
    public String[] parts() {
        final String[] parts = new String[2];
        parts[0] = COMMAND;
        parts[1] = message;
        return parts;
    }

    @Override
    public String toString() {
        return "echo: " + Arrays.toString(parts());
    }
}