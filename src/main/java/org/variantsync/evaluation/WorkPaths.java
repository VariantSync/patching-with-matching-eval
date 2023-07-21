package org.variantsync.evaluation;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.tinylog.Logger;
import org.variantsync.evaluation.baseline.shell.ShellExecutor;
import org.variantsync.vevos.simulation.util.io.CaseSensitivePath;

public class WorkPaths {

    // Working directory
    public final Path workDir;
    // Debug directory
    public final Path debugDir;
    // Path to the first copy of the SPL. We require copy to consider different versions
    public final Path splCopyA;
    // Path to the second copy of the SPL
    public final Path splCopyB;
    // Path to the directory containing the variants generated for the parent commit
    public final CaseSensitivePath variantsDirV0;
    // Path to the directory containing the variants generated for the child commit
    public final CaseSensitivePath variantsDirV1;
    // The directory to which patches are applied. A copy of the target variant is created in this
    // directory.
    public final Path patchDir;
    // Path to the patch file containing the patches without filtering
    public final Path normalPatchFile;
    // Path to the patch file containing the patches with filtering
    public final Path filteredPatchFile;
    // Path to the rejects file created by patching without filtering
    public final Path rejectsNormalFile;
    // Path to the rejects file created by patching with filtering
    public final Path rejectsFilteredFile;
    // ShellExecutor for executing shell commands
    protected final ShellExecutor shell;

    public WorkPaths(Path mainDir, Path resultsDir) {

        try {
            if (mainDir.toFile().mkdirs()) {
                Logger.info("Created main directory " + mainDir);
            }
            this.workDir = Files.createTempDirectory(mainDir, "workdir");
        } catch (final IOException e) {
            Logger.error("Was not able to initialize this.workDir", e);
            throw new UncheckedIOException(e);
        }
        this.debugDir = this.workDir.resolve("DEBUG");
        this.splCopyA = this.workDir.resolve("SPL-A");
        this.splCopyB = this.workDir.resolve("SPL-B");
        this.variantsDirV0 = new CaseSensitivePath(this.workDir.resolve("V0Variants"));
        this.variantsDirV1 = new CaseSensitivePath(this.workDir.resolve("V1Variants"));
        this.patchDir = this.workDir.resolve("TARGET/V0");
        this.normalPatchFile = this.workDir.resolve("patch.txt");
        this.filteredPatchFile = this.workDir.resolve("filtered-patch.txt");
        this.rejectsNormalFile = this.workDir.resolve("rejects-normal.txt");
        this.rejectsFilteredFile = this.workDir.resolve("rejects-filtered.txt");
        this.shell = new ShellExecutor(Logger::debug, Logger::warn, this.workDir);
    }
}
