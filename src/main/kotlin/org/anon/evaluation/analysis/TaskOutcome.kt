package org.anon.evaluation.analysis

import org.anon.evaluation.execution.EvaluationRun
import java.util.*

data class TaskOutcome(val runID: ULong, val result: Optional<List<ExperimentResult>>, val evalRun: EvaluationRun)
