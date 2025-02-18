package org.variantsync.evaluation.analysis

import org.variantsync.evaluation.execution.EvaluationRun
import java.util.*

data class TaskOutcome(val runID: ULong, val result: Optional<List<TaskResult>>, val evalRun: EvaluationRun)
