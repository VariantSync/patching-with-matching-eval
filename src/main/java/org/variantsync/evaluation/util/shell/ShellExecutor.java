package org.variantsync.evaluation.util.shell;

import kotlin.text.Charsets;
import org.tinylog.Logger;
import org.variantsync.evaluation.error.SetupError;
import org.variantsync.evaluation.error.ShellException;
import org.variantsync.functjonal.Result;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Class for executing shell commands under Linux
 */
public class ShellExecutor {
    private final Consumer<String> outputReader;
    private final Consumer<String> errorReader;
    private final Path workDir;
    private final long timeout;
    private final TimeUnit timeoutUnit;

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
     * Initialize a new ShellExecutor that executes all commands in the given
     * working directory
     *
     * @param outputReader Consumer for shell's normal output
     * @param errorReader  Consumer for shell's error output
     * @param workDir      The working directory
     */
    public ShellExecutor(final Consumer<String> outputReader, final Consumer<String> errorReader, final Path workDir) {
        this.workDir = workDir;
        this.outputReader = outputReader;
        this.errorReader = errorReader;
        this.timeout = 0;
        this.timeoutUnit = null;
    }

    /**
     * Initialize a new ShellExecutor
     *
     * @param outputReader Consumer for shell's normal output
     * @param errorReader  Consumer for shell's error output
     */
    public ShellExecutor(final Consumer<String> outputReader, final Consumer<String> errorReader, long timeout, TimeUnit timeoutUnit) {
        this(outputReader, errorReader, null, timeout, timeoutUnit);
    }

    /**
     * Initialize a new ShellExecutor that executes all commands in the given
     * working directory
     *
     * @param outputReader Consumer for shell's normal output
     * @param errorReader  Consumer for shell's error output
     * @param workDir      The working directory
     */
    public ShellExecutor(final Consumer<String> outputReader, final Consumer<String> errorReader, final Path workDir, long timeout, TimeUnit timeoutUnit) {
        this.workDir = workDir;
        this.outputReader = outputReader;
        this.errorReader = errorReader;
        this.timeout = timeout;
        this.timeoutUnit = timeoutUnit;
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
            throw new SetupError("The evaluation can only be executed under Linux!");
        }

        final ProcessBuilder builder = new ProcessBuilder();
        if (executionDir != null) {
            builder.directory(executionDir.toFile());
        }
        Logger.debug("Executing '" + command + "' in directory " + builder.directory());
        builder.command(command.parts());

        Process process;
        final List<String> output = new ArrayList<>();
        final Consumer<String> shareOutput = s -> {
            output.add(s);
            outputReader.accept(s);
        };

        final int exitCode;
        try {
            process = builder.start();
        } catch (final IOException e) {
            Logger.warn("Was not able to execute " + command, e);
            e.printStackTrace();
            return Result.Failure(new ShellException(e));
        }
        try(ExecutorService executor = Executors.newFixedThreadPool(2)) {
            executor.submit(() -> collectOutput(process.inputReader(), shareOutput));
            executor.submit(() -> collectOutput(process.errorReader(), errorReader));
            if (timeout > 0 && timeoutUnit != null) {
                boolean completed = process.waitFor(timeout, timeoutUnit);
                if (!completed) {
                    Logger.debug("Command timed out after 60 seconds:");
                    Logger.debug(command.toString());
                    process.destroy();
                    executor.shutdown();
                }
            }
            exitCode = process.waitFor();
        } catch (final InterruptedException e) {
            Logger.warn("Interrupted while waiting for process to end.", e);
            return Result.Failure(new ShellException(e));
        } finally {
            if (process.isAlive()) {
                // Make sure the process is killed in case of an error
                process.destroy();
            }
        }

        Logger.debug("Command '" + command + "' returned with exit code " + exitCode);
        return command.interpretResult(exitCode, output);
    }

private void collectOutput(final BufferedReader reader, final Consumer<String> consumer) {
        try (reader) {
            String line;
            while ((line = reader.readLine()) != null) {
                consumer.accept(line);
            }
        } catch (final IOException e) {
            Logger.debug("Could not read output stream of Shell command. Command probably reached the configured timeout of %s %s.".formatted(timeout, timeoutUnit), e);
        }
    }
}
