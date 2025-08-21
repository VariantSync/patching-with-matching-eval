package org.variantsync.evaluation.patching

import java.nio.file.Path
import org.variantsync.evaluation.util.diff.components.Hunk
import org.variantsync.evaluation.util.diff.lines.AddedLine
import org.variantsync.evaluation.util.diff.lines.ChangedLine
import org.variantsync.evaluation.util.diff.lines.Line
import org.variantsync.evaluation.util.diff.lines.RemovedLine

class Change(val lineChange: Line, val hunk: Hunk, val path: Path) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Change

        if (lineChange != other.lineChange) return false
        if (hunk.rawLocation() != other.hunk.rawLocation()) return false
        return path == other.path
    }

    override fun hashCode(): Int {
        var result = lineChange.hashCode()
        result = 31 * result + hunk.rawLocation().hashCode()
        result = 31 * result + path.hashCode()
        return result
    }

    fun asChangedLine(): ChangedLine {
        return ChangedLine(path, lineChange)
    }

    fun inverse(): Change {
        val changedText: String = lineChange.line().substring(1)

        val l =
                if (lineChange is AddedLine) {
                    RemovedLine("-$changedText")
                } else {
                    AddedLine("+$changedText")
                }
        return Change(l, hunk, path)
    }

    fun asRejectedChange(): Change {
        val location = this.hunk.location()
        val h = Hunk(location, location, this.hunk.content())
        return Change(this.lineChange, h, this.path)
    }

    override fun toString(): String {
        val sb = StringBuilder()
        sb.appendLine(this.path)
        sb.appendLine(
                String.format(
                        "@@ -%d,%d +%d,%d @@",
                        this.hunk.rawLocation().startLineSource,
                        1,
                        this.hunk.rawLocation().startLineTarget,
                        1
                )
        )
        sb.appendLine(this.lineChange)
        return super.toString()
    }
}

