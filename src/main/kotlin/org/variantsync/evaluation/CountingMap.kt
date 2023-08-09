package org.variantsync.evaluation

class CountingMap<K>(keys: Collection<K>) {
    private val changeMap: HashMap<K, Int>

    init {
        changeMap = HashMap(keys.size)
        for (k in keys) {
            val currentCount = changeMap.getOrDefault(k, 0)
            changeMap[k] = currentCount + 1
        }
    }

    fun removeOne(k: K): Boolean {
        val currentCount = changeMap.getOrDefault(k, 0)
        return if (currentCount > 0) {
            changeMap[k] = currentCount - 1
            true
        } else {
            false
        }
    }

    fun contains(k: K): Boolean {
        return changeMap.getOrDefault(k, 0) > 0
    }

    fun keys(): MutableSet<K> {
        return changeMap.keys
    }
}