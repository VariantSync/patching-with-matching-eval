package org.variantsync.evaluation.patching

import java.util.stream.Collectors
import org.variantsync.evaluation.util.diff.lines.ChangedLine

data class Rejects(val rejects: MutableList<Change>) {
    fun toLines(): List<String> {
        val lines: MutableList<String> = ArrayList()
        rejects.stream().map { obj: Change -> obj.toString() }.forEach { c: String? ->
            lines.add(c!!)
        }
        return lines
    }

    fun intoChanges(): List<Change> {
        return rejects.stream().collect(Collectors.toList())
    }

    fun intoChangedLines(): List<ChangedLine> {
        return rejects.stream()
                .map { c: Change -> ChangedLine(c.path, c.lineChange) }
                .collect(Collectors.toList())
    }
}

