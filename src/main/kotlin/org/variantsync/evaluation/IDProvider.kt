package org.variantsync.evaluation

class IDProvider(val start: ULong) {
    var id: ULong = 0uL
    fun next() : ULong {
        synchronized(this) {
            return id++
        }
    }

    fun start() : ULong {
        return start
    }
}