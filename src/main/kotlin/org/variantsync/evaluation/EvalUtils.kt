package org.variantsync.evaluation

import org.variantsync.evaluation.baseline.diff.components.FileDiff
import org.variantsync.evaluation.baseline.diff.components.OriginalDiff
import org.variantsync.evaluation.patching.*
import java.nio.file.Path

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
    patchers.add(UnixPatch("unix_patch", strip))
    patchers.add(MPatch("mpatch", strip))
    patchers.add(GitApply("git_apply", strip))
    patchers.add(GitCP("git_cherry_default", strip, MergeStrategy.Default))
    patchers.add(GitCP("git_cherry_ours", strip, MergeStrategy.Ours))
    patchers.add(GitCP("git_cherry_theirs", strip, MergeStrategy.Theirs))
    return patchers
}