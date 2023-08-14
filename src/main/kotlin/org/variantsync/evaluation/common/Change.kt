package org.variantsync.evaluation.common

import org.variantsync.evaluation.baseline.diff.components.Hunk
import org.variantsync.evaluation.baseline.diff.lines.AddedLine
import org.variantsync.evaluation.baseline.diff.lines.ChangedLine
import org.variantsync.evaluation.baseline.diff.lines.Line
import org.variantsync.evaluation.baseline.diff.lines.RemovedLine
import java.nio.file.Path


class Change(val lineChange: Line, val hunk: Hunk, val path: Path){
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Change

        if (lineChange != other.lineChange) return false
        if (hunk.location() != other.hunk.location()) return false
        return path == other.path
    }

    override fun hashCode(): Int {
        var result = lineChange.hashCode()
        result = 31 * result + hunk.location().hashCode()
        result = 31 * result + path.hashCode()
        return result
    }

    fun asChangedLine(): ChangedLine {
        return ChangedLine(path, lineChange)
    }

    fun inverse(): Change {
        val changedText: String = lineChange.line().substring(1)

        val l = if (lineChange is AddedLine) {
            RemovedLine("-$changedText")
        } else {
            AddedLine("+$changedText")
        }
        return Change(l, hunk, path)
    }
}