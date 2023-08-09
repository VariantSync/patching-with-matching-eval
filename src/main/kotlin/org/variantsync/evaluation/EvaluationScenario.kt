package org.variantsync.evaluation

import org.variantsync.diffdetective.util.Assert
import org.variantsync.evaluation.baseline.diff.lines.ChangedLine
import org.variantsync.evaluation.common.Change

class EvaluationScenario(private val required: CountingMap<Change>, private val undesired: CountingMap<Change>, private val unPatchable: CountingMap<Change>) {
    fun evaluate(patch: CountingMap<Change>, rejects: CountingMap<Change>, observedDifference: CountingMap<ChangedLine>): EvalResult {
        var correct = 0
        var invalid = 0
        var wrongLocation = 0
        var missing = 0
        var filteredCorrectly = 0
        var filteredIncorrectly = 0
        var mitigatedInvalid = 0
        var mitigatedMissing = 0

        // Second, clean the observed differences from all un-patchable changes
        for (unpatchable in this.unPatchable.keys()) {
            observedDifference.removeOne(unpatchable.asChangedLine())
        }

        // Third, classify the undesired changes into invalid, filtered, and mitigated
        for (undesired in this.undesired.keys()) {
            // Is the change part of the applied patch?
            if (!patch.removeOne(undesired)) {
                // If not, it has been filtered
                filteredCorrectly++
                continue
            }

            // Did the undesired change fail to apply?
            if (rejects.contains(undesired)) {
                // If it did, it has effectively been filtered
                filteredCorrectly++
                Assert.assertTrue(rejects.removeOne(undesired))
                continue
            }

            // The change has been applied and caused an observable undesired effect
            val inverse = undesired.inverse()
            if (observedDifference.contains(inverse.asChangedLine())) {
                invalid++
                Assert.assertTrue(observedDifference.removeOne(inverse.asChangedLine()))
                continue
            }

            // The change has been applied, but did not cause an observable undesired effect
            mitigatedInvalid++
        }

        // Lastly, classify the required changes into valid, missing, and wrongLocation
        for (required in this.required.keys()) {
            // Is the change part of the applied patch?
            if (!patch.removeOne(required)) {
                // If not, it has been filtered incorrectly
                filteredIncorrectly++
                Assert.assertTrue(observedDifference.removeOne(required.asChangedLine()))
                continue
            }

            // Has it failed?
            if (rejects.removeOne(required)) {
                if (observedDifference.removeOne(required.asChangedLine())) {
                    missing++
                } else {
                    mitigatedMissing++
                }
                continue
            }

            // Was it applied to the wrong location?
            if (observedDifference.removeOne(required.asChangedLine())) {
                wrongLocation++
                continue
            }

            // It was applied correctly
            correct++
        }

        return EvalResult(Applied(correct), Invalid(invalid), WrongLocation(wrongLocation), Missing(missing), FilteredCorrectly(filteredCorrectly), FilteredIncorrectly(filteredIncorrectly), MitigatedInvalid(mitigatedInvalid), MitigatedMissing(mitigatedMissing))
    }
}

class EvalResult(val applied: Applied, val invalid: Invalid, val wrongLocation: WrongLocation, val missing: Missing,
                 val filteredCorrectly: FilteredCorrectly, val filteredIncorrectly: FilteredIncorrectly,
                 val mitigatedInvalid: MitigatedInvalid, val mitigatedMissing: MitigatedMissing)  {
    fun resultCount(): Int {
        return applied.v + invalid.v + wrongLocation.v + missing.v + filteredCorrectly.v + filteredIncorrectly.v + mitigatedInvalid.v + mitigatedMissing.v;
    }

}

class AccumulatedResult(var applied: Applied, var invalid: Invalid,
                        var wrongLocation: WrongLocation, var missing: Missing,
                        var filteredCorrectly: FilteredCorrectly, var filteredIncorrectly: FilteredIncorrectly,
                        var mitigatedInvalid: MitigatedInvalid, var mitigatedMissing: MitigatedMissing)  {

    constructor() : this(Applied(0),Invalid(0),WrongLocation(0),Missing(0),FilteredCorrectly(0),FilteredIncorrectly(0),MitigatedInvalid(0),MitigatedMissing(0))
    fun resultCount(): Int {
        return applied.v + invalid.v + wrongLocation.v + missing.v + filteredCorrectly.v + filteredIncorrectly.v + mitigatedInvalid.v + mitigatedMissing.v
    }

    fun correctCount(): Int {
        return applied.v + filteredCorrectly.v + mitigatedInvalid.v + mitigatedMissing.v
    }

    fun incorrectCount(): Int {
        return invalid.v + wrongLocation.v + missing.v + filteredIncorrectly.v
    }

    fun add(other: EvalResult) {
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
value class Applied(val v: Int)

@JvmInline
value class Invalid(val v: Int)

@JvmInline
value class Missing(val v: Int)

@JvmInline
value class FilteredCorrectly(val v: Int)

@JvmInline
value class FilteredIncorrectly(val v: Int)

@JvmInline
value class WrongLocation(val v: Int)

@JvmInline
value class MitigatedInvalid(val v: Int)

@JvmInline
value class MitigatedMissing(val v: Int)