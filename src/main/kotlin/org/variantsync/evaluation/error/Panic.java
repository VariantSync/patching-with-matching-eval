package org.variantsync.evaluation.error;

/**
 * Custom Error class to represent critical errors due to requirements of the study not being fulfilled.
 */
public class Panic extends Error {
    public Panic(final String message) {
        super(message);
    }
}
