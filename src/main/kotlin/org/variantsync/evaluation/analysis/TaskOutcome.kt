package org.XXXX-1.evaluation.analysis

import org.XXXX-1.evaluation.execution.EvaluationRun
import java.util.*

data class TaskOutcome(val runID: ULong, val result: Optional<List<ExperimentResult>>, val evalRun: EvaluationRun)
