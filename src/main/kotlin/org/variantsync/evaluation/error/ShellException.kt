package org.variantsync.evaluation.error;

import java.util.Collection;
import java.util.ArrayList;
import java.util.List;

/**
 * Custom Exception for representing errors caused by shell commands being executed.
 */
public class ShellException extends Exception {
    private final List<String> output;

    public ShellException(final Exception e) {
        super(e);
        this.output = new ArrayList<>();
    }

    public ShellException(final List<String> output) {
        super(convert(output));
        this.output = output;
    }

    private static String convert(final Collection<String> output) {
        final StringBuilder sb = new StringBuilder();
        output.forEach(l -> sb.append(l).append(System.lineSeparator()));
        return sb.toString();
    }

    public List<String> getOutput() {
        return output;
    }
}