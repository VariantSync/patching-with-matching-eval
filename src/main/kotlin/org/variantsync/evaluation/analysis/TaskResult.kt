package org.variantsync.evaluation.analysis

import java.nio.file.Path

data class TaskResult(
        val resultObj: Any,
        val pathToResultFile: Path,
)
