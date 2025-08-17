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
import java.util.function.Consumer;

/**
 * Class for executing shell commands under Linux
 */
public class ShellExecutor {
    private final Consumer<String> outputReader;
    private final Consumer<String> errorReader;
    private final Path workDir;

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

        Process process = null;
        final List<String> output = new ArrayList<>();
        final Consumer<String> shareOutput = s -> {
            output.add(s);
            outputReader.accept(s);
        };

        final int exitCode;
        try {
            process = builder.start();
            collectOutput(process.getInputStream(), shareOutput);
            collectOutput(process.getErrorStream(), errorReader);
            exitCode = process.waitFor();
        } catch (final IOException e) {
            Logger.error("Was not able to execute " + command, e);
            e.printStackTrace();
            return Result.Failure(new ShellException(e));
        } catch (final InterruptedException e) {
            Logger.error("Interrupted while waiting for process to end.", e);
            return Result.Failure(new ShellException(e));
        } finally {
            if (process != null) {
                process.destroy();
            }
        }

        return command.interpretResult(exitCode, output);
    }

private void collectOutput(final InputStream inputStream, final Consumer<String> consumer) {
        try (inputStream; final BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, Charsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                consumer.accept(line);
            }
        } catch (final IOException e) {
            Logger.error("Exception thrown while reading stream of Shell command.", e);
        }
}
}
