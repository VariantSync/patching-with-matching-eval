package org.variantsync.evaluation.patching

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Consumer
import org.tinylog.kotlin.Logger
import org.variantsync.evaluation.error.ShellException
import org.variantsync.evaluation.execution.Operations
import org.variantsync.evaluation.execution.panic
import org.variantsync.evaluation.execution.readContentSafely
import org.variantsync.evaluation.util.diff.DiffParser
import org.variantsync.evaluation.util.diff.components.OriginalDiff
import org.variantsync.evaluation.util.shell.PatchCommand
import org.variantsync.vevos.simulation.feature.Variant

class GNUPatch(private val name: String, private val strip: Int) : Patcher {
    override fun applyPatch(
            operations: Operations,
            sourceVariant: Variant,
            targetVariant: Variant,
            withFiler: Boolean,
    ): Rejects {
        val rejectFile =
                if (withFiler) {
                    operations.rejectsFileFiltered()
                } else {
                    operations.rejectsFile()
                }

        val pathToPatchFile =
                if (withFiler) {
                    operations.filteredPatchFile()
                } else {
                    operations.patchFile()
                }

        val patch = DiffParser.toOriginalDiff(readContentSafely(pathToPatchFile))

        if (!Files.exists(pathToPatchFile)) {
            // If there is nothing to patch, there is nothing to reject
            return Rejects(ArrayList())
        }

        // apply patch to target variant
        val patchCommand =
                PatchCommand.Recommended(pathToPatchFile)
                        .strip(strip)
                        .rejectFile(rejectFile)
                        .force()
                        .ignoreWhitespace()
        val result = operations.shell().execute(patchCommand, operations.patchDir())

        val rejects = Rejects(ArrayList())
        if (result.isSuccess) {
            result.success.forEach(Consumer { message: String? -> Logger.debug(message) })
        } else {
            rejects.rejects.addAll(readRejectsFromOutput(result.failure, patch).rejects)
        }

        rejects.rejects.addAll(readRejectsFromFile(operations, rejectFile, patch).rejects)

        return rejects
    }

    private fun readRejectsFromOutput(
            patchError: ShellException,
            patch: OriginalDiff,
    ): Rejects {
        // Handle rejects
        val skippedFiles: MutableSet<Path> = HashSet()
        val lines = patchError.output
        Logger.debug(
                "Failed to apply part of patch. See debug log and rejects file for more information"
        )
        var oldFile: Path
        for (nextLine in lines) {
            Logger.debug(nextLine)
            if (nextLine.startsWith("|---")) {
                oldFile =
                        Path.of(
                                nextLine.split("\\s+".toRegex())
                                        .dropLastWhile { it.isEmpty() }
                                        .toTypedArray()[1]
                        )
                oldFile = oldFile.subpath(strip, oldFile.nameCount)
                skippedFiles.add(oldFile)
            }
        }

        val rejects = ArrayList<Change>()
        // Add all rejects determined from the output
        try {
            for (change in patch.intoChanges(strip)) {
                if (skippedFiles.contains(change.path)) {
                    rejects.add(change)
                    skippedFiles.remove(change.path)
                }
            }
        } catch (e: IOException) {
            panic("Was not able to read patch file.", e)
        }

        if (skippedFiles.isNotEmpty()) {
            for (file in skippedFiles) {
                Logger.error(file)
            }
            panic("Not all skipped files processed!")
        }

        return Rejects(rejects)
    }

    override fun name(): String {
        return this.name
    }

    // Read a rejects file
    private fun readRejectsFromFile(
            operations: Operations,
            rejectFile: Path,
            patch: OriginalDiff
    ): Rejects {
        var rejectsDiff: OriginalDiff? = null
        if (Files.exists(rejectFile)) {
            try {
                val rejects = readContentSafely(rejectFile)
                rejectsDiff = DiffParser.toOriginalDiff(rejects)
            } catch (e: IOException) {
                panic("Was not able to read rejects file.", e)
            }
        }
        val result: OriginalDiff = rejectsDiff ?: OriginalDiff(ArrayList())

        if (operations.appliedPatchTracker().hasAnyError()) {
            Logger.error(
                    "patch that caused the error: {}",
                    patch.fileDiffs()[operations.appliedPatchTracker().patchId]
            )
        }
        if (operations.appliedPatchTracker().hasCriticalError()) {
            // There was a critical error due to a bug in patch
            // We have to read which file caused the error from our tracker, and then add all
            // patches that came afterward
            // to the rejects, because patching was aborted
            val file = operations.appliedPatchTracker().lastPatchTarget()
            var afterError = false
            for (fd in patch.fileDiffs) {
                if (fd.oldFile.endsWith(file)) {
                    afterError = true
                }
                if (afterError) {
                    result.fileDiffs.add(fd)
                }
            }
        }
        if (operations.appliedPatchTracker().hasNormalError()) {
            // A normal error causes only the problematic patch to fail.
            // We can add this patch to the rejects.
            result.fileDiffs.add(patch.fileDiffs()[operations.appliedPatchTracker().patchId])
        }
        operations.appliedPatchTracker().reset()

        return Rejects(result.intoChanges(0))
    }
}

