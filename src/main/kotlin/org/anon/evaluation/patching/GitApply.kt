package org.anon.evaluation.patching

import org.apache.commons.io.FileUtils
import org.apache.commons.io.filefilter.IOFileFilter
import org.apache.commons.io.filefilter.TrueFileFilter
import org.tinylog.kotlin.Logger
import org.anon.evaluation.execution.Operations
import org.anon.evaluation.util.diff.DiffParser
import org.anon.evaluation.util.diff.components.Hunk
import org.anon.evaluation.util.shell.GitApplyCommand
import org.anon.evaluation.util.shell.ShellExecutor
import org.anon.evaluation.execution.panic
import org.anon.evaluation.execution.readContentSafely
import org.anon.vevos.simulation.feature.Variant
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Consumer


class GitApply(private val name: String, private val strip: Int) : Patcher {
    override fun applyPatch(
        operations: Operations,
        sourceVariant: Variant,
        targetVariant: Variant,
        withFiler: Boolean
    ): Rejects {
        val pathToPatchFile = if (withFiler) {
            operations.filteredPatchFile()
        } else {
            operations.patchFile()
        }

        if (!Files.exists(pathToPatchFile)) {
            // If there is nothing to patch, there is nothing to reject
            return Rejects(ArrayList())
        }

        val patchCommand = GitApplyCommand.Recommended(pathToPatchFile).strip(strip)
            .reject()

        // apply patch to target variant
        val customShell = ShellExecutor(
            Logger::debug,
            Logger::debug,
            operations.workDir()
        )
        val result = customShell.execute(
            patchCommand,
            operations.patchDir()
        )

        val rejects = Rejects(ArrayList())
        if (result.isSuccess) {
            result.success.forEach(Consumer { message: String? -> Logger.debug(message) })
        } else {
            Logger.debug("git apply failed")
            result.failure.output.forEach(Consumer { message: String? -> Logger.debug(message) })
        }

        rejects.rejects.addAll(readRejectsFromFile(operations, withFiler).rejects)

        return rejects
    }

    override fun name(): String {
        return this.name
    }

    // Read a rejects file
    private fun readRejectsFromFile(operations: Operations, withFiler: Boolean): Rejects {
        val rejectFiles = findRejects(operations)
        val rejects = ArrayList<Change>()
        for (rejectFile in rejectFiles) {
            if (Files.exists(rejectFile.toPath())) {
                rejects.addAll(parseRejects(operations, rejectFile.toPath()))
            }
        }
        return Rejects(rejects)
    }

    private fun findRejects(operations: Operations): Collection<File> {
        // Define a file filter to select .rej files
        val rejectFilter: IOFileFilter = object : IOFileFilter {
            override fun accept(file: File): Boolean {
                return file.name.endsWith(".rej")
            }

            override fun accept(dir: File?, name: String): Boolean {
                return name.endsWith(".rej")
            }
        }
        // Search for reject files recursively
        val rejectFiles: Collection<File> = FileUtils.listFiles(
            operations.patchDir().toFile(),
            rejectFilter,
            TrueFileFilter.INSTANCE // This filter accepts all directories for recursive search
        )
        return rejectFiles
    }

    private fun parseRejects(operations: Operations, rejectFile: Path): List<Change> {
        try {
            val rejectContent = readContentSafely(rejectFile)
            var rejectedFileDir = rejectFile.parent
            var rejectedFileName = rejectFile.fileName.toString()
            rejectedFileName = rejectedFileName.substring(0, rejectedFileName.length - 4)
            var rejectedFile = rejectedFileDir.resolve(rejectedFileName)
            rejectedFile = operations.patchDir().relativize(rejectedFile)
            return parseRejects(rejectContent, rejectedFile)
        } catch (e: IOException) {
            panic("Was not able to read rejects file.", e)
        }
        return ArrayList()
    }

    private fun parseRejects(lines: List<String>, path: Path): List<Change> {
        var index = 1
        val hunkStart = "@@ -"
        var nextLine: String = lines[index]

        // Parse the hunks
        val hunks = ArrayList<Hunk>()
        var hunkLines: MutableList<String?> = java.util.ArrayList()
        hunkLines.add(nextLine)
        index += 1
        while (index < lines.size) {
            nextLine = lines[index]
            if (nextLine.startsWith(hunkStart)) {
                hunks.add(DiffParser.parseHunk(hunkLines))
                hunkLines = java.util.ArrayList()
            }
            hunkLines.add(nextLine)
            index++
        }
        // Parse the content of the last hunk
        hunks.add(DiffParser.parseHunk(hunkLines))


        // Filter the hunks of each patch to extract changed lines
        val rejects = ArrayList<Change>()
        for (hunk in hunks) {
            for (changedLine in hunk.changedLines()) {
                rejects.add(Change(changedLine, hunk, path))
            }
        }

        return rejects
    }

    override fun clean(operations: Operations) {
        val rejectFiles = findRejects(operations)
        for (rejectFile in rejectFiles) {
            Logger.debug("Cleaning old rejects file $rejectFile")
            Files.delete(rejectFile.toPath())
        }
    }
}