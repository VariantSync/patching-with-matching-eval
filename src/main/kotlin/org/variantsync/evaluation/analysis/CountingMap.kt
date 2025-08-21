package org.variantsync.evaluation.analysis

class CountingMap<K>() {
    private var map: HashMap<K, Int> = HashMap()
    private var elementCount: UInt = 0u

    constructor(keys: Collection<K>) : this() {
        for (k in keys) {
            val currentCount = map.getOrDefault(k, 0)
            map[k] = currentCount + 1
            elementCount++
        }
    }

    constructor(other: CountingMap<K>) : this() {
        map = HashMap(other.map)
        elementCount = other.elementCount
    }

    fun removeOne(k: K): Boolean {
        val currentCount = map.getOrDefault(k, 0)
        return if (currentCount > 0) {
            map[k] = currentCount - 1
            elementCount--
            true
        } else {
            false
        }
    }

    fun contains(k: K): Boolean {
        return map.getOrDefault(k, 0) > 0
    }

    operator fun iterator(): Iterator<K> {
        val iterable: ArrayList<K> = ArrayList()
        for (entry in map.entries) {
            var count = entry.value
            while (count > 0) {
                iterable.add(entry.key)
                count--
            }
        }
        return iterable.iterator()
    }

    fun addOne(k: K) {
        val currentCount = map.getOrDefault(k, 0)
        map[k] = currentCount + 1
        elementCount++
    }

    fun elementCount(): UInt {
        return elementCount
    }
}
