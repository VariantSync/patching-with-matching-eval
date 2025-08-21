package org.variantsync.evaluation.analysis

import java.util.*
import org.variantsync.evaluation.execution.EvaluationRun

data class TaskOutcome(
        val runID: ULong,
        val result: Optional<List<TaskResult>>,
        val evalRun: EvaluationRun
)
