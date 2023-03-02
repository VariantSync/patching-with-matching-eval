package org.variantsync.evaluation.baseline.shell;

import org.variantsync.functjonal.Result;
import org.variantsync.evaluation.error.SetupError;
import org.variantsync.evaluation.error.ShellException;
import org.variantsync.vevos.simulation.util.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * Class for executing shell commands under Linux
 */
public class ShellExecutor {
    private final Consumer<String> outputReader;
    private final Consumer<String> errorReader;
    private final Path workDir;
    private final ExecutorService outputCollection;
    private final ExecutorService errorCollection;

    /**
     * Initialize a new ShellExecutor
     *
     * @param outputReader Consumer for shell's normal output
     * @param errorReader  Consumer for shell's error output
     */
    public ShellExecutor(final Consumer<String> outputReader, final Consumer<String> errorReader) {
        this(outputReader, errorReader, null);
    }

    /**
     * Initialize a new ShellExecutor that executes all commands in the given working directory
     *
     * @param outputReader Consumer for shell's normal output
     * @param errorReader  Consumer for shell's error output
     * @param workDir      The working directory
     */
    public ShellExecutor(final Consumer<String> outputReader, final Consumer<String> errorReader, final Path workDir) {
        this.workDir = workDir;
        this.outputReader = outputReader;
        this.errorReader = errorReader;
        outputCollection = Executors.newSingleThreadExecutor();
        errorCollection = Executors.newSingleThreadExecutor();
    }

    /**
     * Execute the given command
     *
     * @return A result that depends on the commands exit code
     */
    public Result<List<String>, ShellException> execute(final ShellCommand command) {
        return execute(command, this.workDir);
    }

    /**
     * Execute the given command.
     *
     * @param command      The command to execute
     * @param executionDir The directory in which to execute the command
     * @return A result that depends on the commands exit code
     */
    public Result<List<String>, ShellException> execute(final ShellCommand command, final Path executionDir) {
        if (System.getProperty("os.name").toLowerCase().startsWith("windows")) {
            throw new SetupError("The synchronization study can only be executed under Linux!");
        }

        final ProcessBuilder builder = new ProcessBuilder();
        if (executionDir != null) {
            builder.directory(executionDir.toFile());
        }
        Logger.debug("Executing '" + command + "' in directory " + builder.directory());
        builder.command(command.parts());

        final Process process;
        final Future<?> outputFuture;
        final Future<?> errorFuture;
        final List<String> output = new LinkedList<>();
        final Consumer<String> shareOutput = s -> {
            output.add(s);
            outputReader.accept(s);
        };

        try {
            process = builder.start();
            outputFuture = outputCollection.submit(collectOutput(process.getInputStream(), shareOutput));
            errorFuture = errorCollection.submit(collectOutput(process.getErrorStream(), errorReader));
        } catch (final IOException e) {
            Logger.error("Was not able to execute " + command, e);
            return Result.Failure(new ShellException(e));
        }

        final int exitCode;
        try {
            exitCode = process.waitFor();
            outputFuture.get();
            errorFuture.get();
        } catch (final InterruptedException | ExecutionException e) {
            Logger.error("Interrupted while waiting for process to end.", e);
            return Result.Failure(new ShellException(e));
        }
        return command.interpretResult(exitCode, output);
    }

    private Runnable collectOutput(final InputStream inputStream, final Consumer<String> consumer) {
        return () -> {
            try (inputStream; final BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                reader.lines().forEach(consumer);
            } catch (final IOException e) {
                Logger.error("Exception thrown while reading stream of Shell command.", e);
            }
        };
    }
}