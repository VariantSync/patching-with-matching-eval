package org.variantsync.evaluation.analysis

import java.nio.file.Path

data class ExperimentResult(
    val resultObj: Any,
    val pathToResultFile: Path,
)
