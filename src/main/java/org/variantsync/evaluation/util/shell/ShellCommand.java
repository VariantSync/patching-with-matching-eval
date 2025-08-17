package org.variantsync.evaluation.util.shell;

import org.variantsync.evaluation.error.ShellException;
import org.variantsync.functjonal.Result;

import java.util.List;

/**
 * Base class for various shell commands.
 */
public abstract class ShellCommand {
    /***
     * Return the String parts that define and configure the command execution (e.g., ["echo", "Hello World"])
     *
     * @return the parts of the shell command.
     */
    public abstract String[] parts();

    /**
     * Interpret the result code returned from a shell command
     *
     * @param resultCode the code that is to be parsed
     * @return the result
     */
    public Result<List<String>, ShellException> interpretResult(final int resultCode, final List<String> output) {
        return resultCode == 0 ? Result.Success(output) : Result.Failure(new ShellException(output));
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (String part : parts()) {
            sb.append(part);
            sb.append(" ");
        }
        return sb.toString();
    }
}
