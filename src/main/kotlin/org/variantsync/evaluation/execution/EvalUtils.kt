package org.XXXX-1.evaluation.execution

import org.XXXX-1.evaluation.util.diff.components.FileDiff
import org.XXXX-1.evaluation.util.diff.components.OriginalDiff
import org.XXXX-1.evaluation.patching.*
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

fun filterUnpatchedFiles(originalPatch: OriginalDiff, diffToFilter: OriginalDiff, strip: Int): OriginalDiff {
    val oldFiles = HashSet<Path>()
    val newFiles = HashSet<Path>()
    for (fd in originalPatch.fileDiffs) {
        oldFiles.add(fd.oldFile.subpath(strip, fd.oldFile.nameCount))
        newFiles.add(fd.newFile.subpath(strip, fd.newFile.nameCount))
    }

    val filteredDiffs = ArrayList<FileDiff>()
    for (fileDiff in diffToFilter.fileDiffs) {
        val oldPath = fileDiff.oldFile.subpath(strip, fileDiff.oldFile.nameCount)
        val newPath = fileDiff.newFile.subpath(strip, fileDiff.newFile.nameCount)
        if (oldFiles.contains(oldPath) && newFiles.contains(newPath)) {
            filteredDiffs.add(fileDiff)
        }
    }
    return OriginalDiff(filteredDiffs)
}

fun defaultPatchers(strip: Int): List<Patcher> {
    val patchers = ArrayList<Patcher>()
    patchers.add(GNUPatch("unix_patch", strip))
    // patchers.add(MPatch("pwm_f1", strip, 1))
    patchers.add(MPatch("pwm_f2", strip, 2))
    // patchers.add(GitApply("git_apply", strip))
    patchers.add(GitCP("git_cherry", strip, MergeStrategy.Ours))
    return patchers
}

@Throws(IOException::class)
fun readContentSafely(filePath: Path): List<String> {
    val content = Files.readString(filePath)
    if (content.isEmpty()) {
        return emptyList()
    }
    val lines = content.split("\n")
    return if (lines.last().isEmpty()) {
        lines.subList(0, lines.lastIndex)
    } else {
        lines
    }
}


