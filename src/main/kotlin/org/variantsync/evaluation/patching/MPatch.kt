package org.variantsync.evaluation.patching

import org.tinylog.kotlin.Logger
import org.variantsync.evaluation.Operations
import org.variantsync.evaluation.baseline.diff.DiffParser
import org.variantsync.evaluation.baseline.diff.components.OriginalDiff
import org.variantsync.evaluation.baseline.shell.MPatchCommand
import org.variantsync.evaluation.baseline.shell.ShellExecutor
import org.variantsync.evaluation.syncstudy.panic
import org.variantsync.vevos.simulation.feature.Variant
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Consumer

class MPatch(private val name: String, private val strip: Int) : Patcher {

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

        val pathToSourceVariant = operations.sourceV0Path(sourceVariant.name)

        val rejectFile = if (withFiler) {
            operations.rejectsFileFiltered()
        } else {
            operations.rejectsFile()
        }

        val patchCommand = MPatchCommand.Recommended(pathToSourceVariant, pathToPatchFile).strip(strip)
            .rejectsFile(rejectFile)

        // apply patch to target variant
        val customShell = ShellExecutor(Logger::debug, Logger::warn, operations.workDir())
        val result = customShell.execute(
            patchCommand,
            operations.patchDir()
        )

        val rejects = Rejects(ArrayList())
        if (result.isSuccess) {
            result.success.forEach(Consumer { message: String? -> Logger.debug(message) })
        } else {
            Logger.error("mpatch failed")
        }

        rejects.rejects.addAll(readRejectsFromFile(operations, rejectFile, withFiler).rejects)

        return rejects
    }

    override fun name(): String {
        return this.name
    }

    // Read a rejects file
    private fun readRejectsFromFile(operations: Operations, rejectFile: Path, withFiler: Boolean): Rejects {
        val pathToPatchFile = if (withFiler) {
            operations.filteredPatchFile()
        } else {
            operations.patchFile()
        }
        val patch = DiffParser.toOriginalDiff(Files.readAllLines(pathToPatchFile))
        if (Files.exists(rejectFile)) {
            try {
                val rejects = Files.readAllLines(rejectFile)
                return parseRejects(patch, rejects)
            } catch (e: IOException) {
                panic("Was not able to read rejects file.", e)
            }
        }
        return Rejects(ArrayList())
    }

    // Parse and convert the lines belonging to the difference of a specific file
    private fun parseMPatchRejects(fileDiffContent: List<String>?): HashSet<RejectId> {
        var index = 0
        var nextLine = fileDiffContent!![index]

        // Parse the header
        val header: MutableList<String> = ArrayList()
        var oldFile: String? = null
        run {
            var atHeader = true
            while (atHeader) {
                if (nextLine.startsWith("--- ")) {
                    oldFile = nextLine.split("\\s+".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[1]
                } else if (nextLine.startsWith("+++ ")) {
                    atHeader = false
                }
                header.add(nextLine)
                index++
                nextLine = fileDiffContent[index]
            }
        }

        // Parse the rejects
        val rejects: HashSet<RejectId> = HashSet()
        run {
            while (index < fileDiffContent.size) {
                nextLine = fileDiffContent[index]
                val id = nextLine.split(":")[0].toInt()
                val path = Path.of(oldFile!!)
                rejects.add(RejectId(path.subpath(strip, path.nameCount), id))
                index++
            }
        }

        return rejects
    }


    private fun parseRejects(patch: OriginalDiff, lines: List<String>): Rejects {
        // The rejects are empty
        if (lines.isEmpty()) {
            return Rejects(ArrayList())
        }

        // Determine the substring which a FileDiff starts with
        var fileDiffStart = ""
        var fileDiffFollow = ""
        if (lines[0].startsWith("diff")) {
            // Several files were processed, the diff of each file starts with the 'diff' command that was used
            fileDiffStart = "diff"
            fileDiffFollow = "--- "
        } else if (lines[0].startsWith("--- ")) {
            // Only one file was processed, the diff of the file starts with the hunk header
            fileDiffStart = "--- "
            fileDiffFollow = "+++ "
        }

        val mPatchRejects = HashSet<RejectId>()
        var fileDiffContent: MutableList<String>? = null
        var indexNext = 0
        for (line in lines) {
            indexNext++
            if (line.startsWith(fileDiffStart)) {
                if (indexNext < lines.size) {
                    val nextLine = lines[indexNext]
                    if (nextLine.startsWith(fileDiffFollow)) {
                        // Create a FileDiff from the collected lines
                        if (fileDiffContent != null) {
                            mPatchRejects.addAll(parseMPatchRejects(fileDiffContent))
                        }
                        // Reset the lines that should go into the next FileDiff
                        fileDiffContent = ArrayList()
                    }
                }
            } else if (line.contains(fileDiffStart)) {
                if (indexNext < lines.size) {
                    val nextLine = lines[indexNext]
                    if (nextLine.startsWith(fileDiffFollow)) {
                        val additionalContent = line.substring(0, line.indexOf(fileDiffStart))
                        // Create a FileDiff from the collected lines
                        if (fileDiffContent != null) {
                            fileDiffContent.add(additionalContent)
                            mPatchRejects.addAll(parseMPatchRejects(fileDiffContent))
                        }
                        // Reset the lines that should go into the next FileDiff
                        fileDiffContent = ArrayList()
                        fileDiffContent.add(line.substring(line.indexOf(fileDiffStart)))
                        continue
                    }
                }
            }
            requireNotNull(fileDiffContent) { "The provided lines do not contain one of the expected fileDiffStart values" }
            fileDiffContent.add(line)
        }
        // Parse the content of the last file diff
        mPatchRejects.addAll(parseMPatchRejects(fileDiffContent))


        val rejects = Rejects(ArrayList())
        for ((changeId, change) in patch.intoChanges(strip).withIndex()) {
            val id = RejectId(change.path, changeId)
            if (mPatchRejects.contains(id)) {
                mPatchRejects.remove(id)
                rejects.rejects.add(change)
            }
        }

        return rejects
    }

}


private class RejectId(val path: Path, val index: Int) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RejectId

        if (path != other.path) return false
        if (index != other.index) return false

        return true
    }

    override fun hashCode(): Int {
        var result = path.hashCode()
        result = 31 * result + index
        return result
    }
}


