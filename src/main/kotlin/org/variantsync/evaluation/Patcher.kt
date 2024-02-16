package org.variantsync.evaluation

import org.tinylog.kotlin.Logger
import org.variantsync.evaluation.common.Rejects
import org.variantsync.vevos.simulation.feature.Variant
import java.nio.file.Files

interface Patcher {

    fun applyPatch(
        operations: Operations,
        sourceVariant: Variant,
        targetVariant: Variant,
        withFiler: Boolean,
    ): Rejects

    fun name(): String

    fun clean(operations: Operations) {
        if (Files.exists(operations.rejectsFile)) {
            Logger.debug("Cleaning old rejects file ${operations.rejectsFile} for unix patch")
            Files.delete(operations.rejectsFile)
        }
        if (Files.exists(operations.rejectsFileFiltered)) {
            Logger.debug("Cleaning old rejects file ${operations.rejectsFileFiltered} for unix patch")
            Files.delete(operations.rejectsFileFiltered)
        }
    }
}