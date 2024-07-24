package org.variantsync.evaluation.analysis

import org.variantsync.evaluation.cherries.EvaluationRun
import java.util.*

data class TaskOutcome(val runID: ULong, val result: Optional<List<ExperimentResult>>, val evalRun: EvaluationRun)
