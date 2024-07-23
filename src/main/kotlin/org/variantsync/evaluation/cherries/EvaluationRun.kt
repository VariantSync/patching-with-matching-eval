package org.variantsync.evaluation.cherries

import org.variantsync.evaluation.analysis.CherryPickPatchOutcome

class EvaluationRun(val repetition: Int, val datasetName: String, val cherry: String, val target: String) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EvaluationRun

        if (repetition != other.repetition) return false
        if (datasetName != other.datasetName) return false
        if (cherry != other.cherry) return false
        if (target != other.target) return false

        return true
    }

    override fun hashCode(): Int {
        var result = repetition
        result = 31 * result + datasetName.hashCode()
        result = 31 * result + cherry.hashCode()
        result = 31 * result + target.hashCode()
        return result
    }
}

fun outcomesToRuns(outcomes: Map<Int, List<CherryPickPatchOutcome>>): Set<EvaluationRun> {
    val evaluationRuns = mutableSetOf<EvaluationRun>()
    for (rep in outcomes.keys) {
        for (outcome in outcomes[rep]!!) {
            evaluationRuns.add(EvaluationRun(rep, outcome.dataset, outcome.cherry, outcome.target))
        }
    }
    return evaluationRuns
}