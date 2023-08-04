package org.variantsync.evaluation

import org.variantsync.evaluation.baseline.diff.components.FineDiff
import org.variantsync.evaluation.common.Change

class ChangeMap {
    private val changeMap: HashMap<Change, Int>

    constructor(diff: FineDiff) {
        changeMap = HashMap.newHashMap(diff.changeCount())
        for (change in diff.intoChanges()) {
            val currentCount = changeMap.getOrDefault(change, 0)
            changeMap[change] = currentCount + 1
        }
    }

    fun removeOne(change: Change): Boolean {
        val currentCount = changeMap.getOrDefault(change, 0)
        return if (currentCount > 0) {
            changeMap[change] = currentCount - 1
            true
        } else {
            false
        }
    }

    fun contains(change: Change): Boolean {
        return changeMap.getOrDefault(change, 0) > 0
    }

    fun keys(): MutableSet<Change> {
        return changeMap.keys
    }
}