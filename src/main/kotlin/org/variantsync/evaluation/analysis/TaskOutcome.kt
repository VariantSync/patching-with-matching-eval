package org.variantsync.evaluation.analysis

import java.util.*

data class TaskOutcome(val runID: ULong, val result: Optional<List<ExperimentResult>>)
