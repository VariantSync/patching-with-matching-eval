package org.variantsync.evaluation.execution

class EvaluationRun(
        val repetition: Int,
        val datasetName: String,
        val cherry: String,
        val pick: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EvaluationRun

        if (repetition != other.repetition) return false
        if (datasetName != other.datasetName) return false
        if (cherry != other.cherry) return false
        if (pick != other.pick) return false

        return true
    }

    override fun hashCode(): Int {
        var result = repetition
        result = 31 * result + datasetName.hashCode()
        result = 31 * result + cherry.hashCode()
        result = 31 * result + pick.hashCode()
        return result
    }
}

