package org.variantsync.evaluation

import org.variantsync.diffdetective.util.Assert

class EvaluationScenario(val required: ChangeMap, val undesired: ChangeMap, val unPatchable: ChangeMap) {
    fun evaluate(scenario: EvaluationScenario, patch: ChangeMap, rejects: ChangeMap, observedDifference: ChangeMap): EvalResult {
        var correct = 0u
        var invalid = 0u
        var wrongLocation = 0u
        var missing = 0u
        var filteredCorrectly = 0u
        var filteredIncorrectly = 0u
        var mitigatedInvalid = 0u
        var mitigatedMissing = 0u

        // Second, clean the observed differences from all un-patchable changes
        for (unpatchable in scenario.unPatchable.keys()) {
            observedDifference.removeOne(unpatchable)
        }

        // Third, classify the undesired changes into invalid, filtered, and mitigated
        for (undesired in scenario.undesired.keys()) {
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
            if (observedDifference.contains(inverse)) {
                invalid++
                // TODO: This does not work as expected because a Change in the difference might have a different context
                Assert.assertTrue(observedDifference.removeOne(inverse))
                continue
            }

            // The change has been applied, but did not cause an observable undesired effect
            mitigatedInvalid++
        }

        // Lastly, classify the required changes into valid, missing, and wrongLocation
        for (required in scenario.required.keys()) {
            // Is the change part of the applied patch?
            if (!patch.removeOne(required)) {
                // If not, it has been filtered incorrectly
                filteredIncorrectly++
                Assert.assertTrue(observedDifference.removeOne(required))
                continue
            }

            // Has it failed?
            if (rejects.removeOne(required)) {
                if (observedDifference.removeOne(required)) {
                    missing++
                } else {
                    mitigatedMissing++
                }
                continue
            }

            // Was it applied to the wrong location?
            if (observedDifference.removeOne(required)) {
                wrongLocation++
                continue
            }

            // It was applied correctly
            correct++
        }

        return EvalResult(Correct(correct), Invalid(invalid), WrongLocation(wrongLocation), Missing(missing), FilteredCorrectly(filteredCorrectly), FilteredIncorrectly(filteredIncorrectly), MitigatedInvalid(mitigatedInvalid), MitigatedMissing(mitigatedMissing))
    }
}

class EvalResult(val correct: Correct, val invalid: Invalid, val wrongLocation: WrongLocation, val missing: Missing, val filteredCorrectly: FilteredCorrectly, val filteredIncorrectly: FilteredIncorrectly, val mitigatedInvalid: MitigatedInvalid, val mitigatedMissing: MitigatedMissing)

@JvmInline
value class Correct(val v: UInt)

@JvmInline
value class Invalid(val v: UInt)

@JvmInline
value class Missing(val v: UInt)

@JvmInline
value class FilteredCorrectly(val v: UInt)

@JvmInline
value class FilteredIncorrectly(val v: UInt)

@JvmInline
value class WrongLocation(val v: UInt)

@JvmInline
value class MitigatedInvalid(val v: UInt)

@JvmInline
value class MitigatedMissing(val v: UInt)