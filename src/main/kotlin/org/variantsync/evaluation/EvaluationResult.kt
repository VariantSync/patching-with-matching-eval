package org.variantsync.evaluation

class EvaluationResult(
    val applied: Applied, val invalid: Invalid, val wrongLocation: WrongLocation, val missing: Missing,
    val filteredCorrectly: FilteredCorrectly, val filteredIncorrectly: FilteredIncorrectly,
    val mitigatedInvalid: MitigatedInvalid, val mitigatedMissing: MitigatedMissing
) {
    fun resultCount(): Long {
        return applied.v + invalid.v + wrongLocation.v + missing.v + filteredCorrectly.v + filteredIncorrectly.v + mitigatedInvalid.v + mitigatedMissing.v
    }

}

class AccumulatedResult(
    var applied: Applied, var invalid: Invalid,
    var wrongLocation: WrongLocation, var missing: Missing,
    var filteredCorrectly: FilteredCorrectly, var filteredIncorrectly: FilteredIncorrectly,
    var mitigatedInvalid: MitigatedInvalid, var mitigatedMissing: MitigatedMissing
) {

    constructor() : this(
        Applied(0),
        Invalid(0),
        WrongLocation(0),
        Missing(0),
        FilteredCorrectly(0),
        FilteredIncorrectly(0),
        MitigatedInvalid(0),
        MitigatedMissing(0)
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

    fun add(other: EvaluationResult) {
        this.applied = Applied(this.applied.v + other.applied.v)
        this.invalid = Invalid(this.invalid.v + other.invalid.v)
        this.wrongLocation = WrongLocation(this.wrongLocation.v + other.wrongLocation.v)
        this.missing = Missing(this.missing.v + other.missing.v)
        this.filteredCorrectly = FilteredCorrectly(this.filteredCorrectly.v + other.filteredCorrectly.v)
        this.filteredIncorrectly = FilteredIncorrectly(this.filteredIncorrectly.v + other.filteredIncorrectly.v)
        this.mitigatedInvalid = MitigatedInvalid(this.mitigatedInvalid.v + other.mitigatedInvalid.v)
        this.mitigatedMissing = MitigatedMissing(this.mitigatedMissing.v + other.mitigatedMissing.v)
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