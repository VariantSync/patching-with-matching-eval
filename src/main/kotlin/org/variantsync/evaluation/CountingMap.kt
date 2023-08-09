package org.variantsync.evaluation

class CountingMap<K>() {
    private var changeMap: HashMap<K, Int> = HashMap()
    private var elementCount: UInt = 0u

    constructor(keys: Collection<K>) :this() {
        for (k in keys) {
            val currentCount = changeMap.getOrDefault(k, 0)
            changeMap[k] = currentCount + 1
            elementCount++
        }
    }

    constructor(other: CountingMap<K>) : this() {
        changeMap = HashMap(other.changeMap)
        elementCount = other.elementCount
    }

    fun removeOne(k: K): Boolean {
        val currentCount = changeMap.getOrDefault(k, 0)
        return if (currentCount > 0) {
            changeMap[k] = currentCount - 1
            elementCount--
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

    operator fun iterator(): Iterator<K> {
        val iterable: ArrayList<K> = ArrayList()
        for (entry in changeMap.entries) {
            var count = entry.value
            while(count > 0) {
                iterable.add(entry.key)
                count--
            }
        }
        return iterable.iterator()
    }

    fun addOne(k: K) {
        val currentCount = changeMap.getOrDefault(k, 0)
        changeMap[k] = currentCount + 1
        elementCount++
    }

    fun elementCount(): UInt {
        return elementCount
    }
}