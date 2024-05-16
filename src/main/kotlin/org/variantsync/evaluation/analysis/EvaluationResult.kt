package org.variantsync.evaluation.analysis

class EvaluationResult(
    val applied: Applied, val invalid: Invalid, val wrongLocation: WrongLocation, val missing: Missing,
    val filteredCorrectly: FilteredCorrectly, val filteredIncorrectly: FilteredIncorrectly,
    val mitigatedInvalid: MitigatedInvalid, val mitigatedMissing: MitigatedMissing,
    val editDistance: EditDistance,
) {
    fun resultCount(): Long {
        return applied.v + invalid.v + wrongLocation.v + missing.v + filteredCorrectly.v + filteredIncorrectly.v + mitigatedInvalid.v + mitigatedMissing.v
    }

    fun incorrectCount(): Long {
        return invalid.v + wrongLocation.v + missing.v + filteredIncorrectly.v
    }

}

class AccumulatedResult(
    var applied: Applied, var invalid: Invalid,
    var wrongLocation: WrongLocation, var missing: Missing,
    var filteredCorrectly: FilteredCorrectly, var filteredIncorrectly: FilteredIncorrectly,
    var mitigatedInvalid: MitigatedInvalid, var mitigatedMissing: MitigatedMissing,
    var editDistance: EditDistance,
    private var fullyCorrectCommits: FullyCorrectCommits,
    private var numResultsTotal: NumResultsTotal,
) {

    constructor() : this(
        Applied(0),
        Invalid(0),
        WrongLocation(0),
        Missing(0),
        FilteredCorrectly(0),
        FilteredIncorrectly(0),
        MitigatedInvalid(0),
        MitigatedMissing(0),
        EditDistance(0u),
        FullyCorrectCommits(0u),
        NumResultsTotal(0u),
    )

    fun resultCount(): Long {
        return applied.v + invalid.v + wrongLocation.v + missing.v + filteredCorrectly.v + filteredIncorrectly.v + mitigatedInvalid.v + mitigatedMissing.v
    }

    fun correctCount(): Long {
        return applied.v + filteredCorrectly.v + mitigatedInvalid.v + mitigatedMissing.v
    }

    fun incorrectCount(): Long {
        return invalid.v + wrongLocation.v + missing.v + filteredIncorrectly.v
    }

    fun averageEditDistance(): Double {
        return this.editDistance.v.toDouble() / this.numResultsTotal.v.toDouble()
    }

    fun fullyCorrectPercentage(): Double {
        return 100.0 * (this.fullyCorrectCommits.v.toDouble() / this.numResultsTotal.v.toDouble())
    }

    fun add(other: EvaluationResult) {
        this.applied = Applied(this.applied.v + other.applied.v)
        this.invalid = Invalid(this.invalid.v + other.invalid.v)
        this.wrongLocation = WrongLocation(this.wrongLocation.v + other.wrongLocation.v)
        this.missing = Missing(this.missing.v + other.missing.v)
        this.filteredCorrectly = FilteredCorrectly(this.filteredCorrectly.v + other.filteredCorrectly.v)
        this.filteredIncorrectly = FilteredIncorrectly(this.filteredIncorrectly.v + other.filteredIncorrectly.v)
        this.mitigatedInvalid = MitigatedInvalid(this.mitigatedInvalid.v + other.mitigatedInvalid.v)
        this.mitigatedMissing = MitigatedMissing(this.mitigatedMissing.v + other.mitigatedMissing.v)
        this.editDistance = EditDistance(this.editDistance.v + other.editDistance.v)
        this.numResultsTotal = NumResultsTotal(this.numResultsTotal.v + 1u)

        if (other.incorrectCount() == 0L) {
            this.fullyCorrectCommits = FullyCorrectCommits(this.fullyCorrectCommits.v + 1u)
        }
    }
}

@JvmInline
value class Applied(val v: Long)

@JvmInline
value class Invalid(val v: Long)

@JvmInline
value class Missing(val v: Long)

@JvmInline
value class FilteredCorrectly(val v: Long)

@JvmInline
value class FilteredIncorrectly(val v: Long)

@JvmInline
value class WrongLocation(val v: Long)

@JvmInline
value class MitigatedInvalid(val v: Long)

@JvmInline
value class MitigatedMissing(val v: Long)

@JvmInline
value class EditDistance(val v: UInt)

@JvmInline
value class FullyCorrectCommits(val v: UInt)

@JvmInline
value class NumResultsTotal(val v: UInt)