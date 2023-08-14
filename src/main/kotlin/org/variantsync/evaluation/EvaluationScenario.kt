package org.variantsync.evaluation

import org.variantsync.diffdetective.util.Assert
import org.variantsync.evaluation.baseline.diff.lines.ChangedLine
import org.variantsync.evaluation.common.Change

class EvaluationScenario(private val required: CountingMap<Change>, private val undesired: CountingMap<Change>, private val unPatchable: CountingMap<ChangedLine>) {
    fun evaluate(patch: CountingMap<Change>, rejects: CountingMap<Change>, observedDifference: CountingMap<ChangedLine>): EvaluationResult {
        var correct = 0L
        var invalid = 0L
        var wrongLocation = 0L
        var missing = 0L
        var filteredCorrectly = 0L
        var filteredIncorrectly = 0L
        var mitigatedInvalid = 0L
        var mitigatedMissing = 0L

        // Second, clean the observed differences from all un-patchable changes
        for (unpatchable in this.unPatchable) {
            observedDifference.removeOne(unpatchable)
        }

        // Third, classify the required changes into valid, missing, and wrongLocation
        for (required in this.required) {
            // Is the change part of the applied patch?
            // TODO: Bug! The context of the required change does not necessarily fit the context of the change in the patch
            // TODO: We have to track ids or something
            if (!patch.removeOne(required)) {
                if(observedDifference.removeOne(required.asChangedLine())) {
                    // If not, it has been filtered incorrectly
                    filteredIncorrectly++
                } else {
                    mitigatedMissing++
                }
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

        // Lastly, classify the undesired changes into invalid, filtered, and mitigated
        for (undesired in this.undesired) {
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


        return EvaluationResult(Applied(correct), Invalid(invalid), WrongLocation(wrongLocation), Missing(missing), FilteredCorrectly(filteredCorrectly), FilteredIncorrectly(filteredIncorrectly), MitigatedInvalid(mitigatedInvalid), MitigatedMissing(mitigatedMissing))
    }
}