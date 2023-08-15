package org.variantsync.evaluation

class IDProvider(var id: ULong) {
    val start: ULong = id
    fun next() : ULong {
        synchronized(this) {
            return id++
        }
    }

    fun start() : ULong {
        return start
    }
}