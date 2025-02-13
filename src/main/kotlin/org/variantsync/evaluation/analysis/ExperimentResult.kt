package org.XXXX-1.evaluation.analysis

import java.nio.file.Path

data class ExperimentResult(
    val resultObj: Any,
    val pathToResultFile: Path,
)
