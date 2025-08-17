package org.variantsync.evaluation.analysis

class EvaluationResult(
        val applied: Applied,
        val invalid: Invalid,
        val wrongLocation: WrongLocation,
        val missing: Missing,
        val filteredCorrectly: FilteredCorrectly,
        val filteredIncorrectly: FilteredIncorrectly,
        val mitigatedInvalid: MitigatedInvalid,
        val mitigatedMissing: MitigatedMissing,
        val editDistance: EditDistance,
) {
    fun resultCount(): Long {
        return applied.v +
                invalid.v +
                wrongLocation.v +
                missing.v +
                filteredCorrectly.v +
                filteredIncorrectly.v +
                mitigatedInvalid.v +
                mitigatedMissing.v
    }
}

@JvmInline value class Applied(val v: Long)

@JvmInline value class Invalid(val v: Long)

@JvmInline value class Missing(val v: Long)

@JvmInline value class FilteredCorrectly(val v: Long)

@JvmInline value class FilteredIncorrectly(val v: Long)

@JvmInline value class WrongLocation(val v: Long)

@JvmInline value class MitigatedInvalid(val v: Long)

@JvmInline value class MitigatedMissing(val v: Long)

@JvmInline value class EditDistance(val v: UInt)

@JvmInline value class FullyCorrectCommits(val v: UInt)

@JvmInline value class NumResultsTotal(val v: UInt)
