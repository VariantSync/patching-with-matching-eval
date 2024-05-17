package org.variantsync.evaluation

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.tinylog.kotlin.Logger
import org.variantsync.evaluation.analysis.CherryPickPatchOutcome
import org.variantsync.evaluation.analysis.SyncStudyPatchOutcome
import org.variantsync.evaluation.cherries.CherryPick
import org.variantsync.evaluation.syncstudy.panic
import org.variantsync.vevos.simulation.feature.Variant
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

fun saveResult(
    patchOutcome: SyncStudyPatchOutcome,
    resultFile: Path,
    runID: ULong,
    source: Variant,
    target: Variant
) {
    try {
        writeAsJSON(patchOutcome, resultFile, true)
    } catch (e: IOException) {
        panic(
            "Was not able to write filtered patch result file for run "
                    + runID, e
        )
    }
    Logger.debug(
        "Finished patching for source " + source.name + " and target "
                + target.name
    )
}

fun saveResult(
    patchOutcome: CherryPickPatchOutcome,
    cherryPick: CherryPick,
    resultFile: Path,
    runID: ULong,
) {
    try {
        writeAsJSON(patchOutcome, resultFile, true)
    } catch (e: IOException) {
        panic(
            "Was not able to write filtered patch result file for run "
                    + runID, e
        )
    }
    Logger.debug(
        "Finished patching for cherry " + cherryPick.cherryCommit + " and target "
                + cherryPick.targetCommit
    )
}

@Throws(IOException::class)
fun writeAsJSON(obj: Any, pathToFile: Path, append: Boolean) {
    val jsonBuilder = StringBuilder()

    // Option 1: Create a new ObjectMapper and register the KotlinModule
    // val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
    val mapper = jacksonObjectMapper()
    mapper.registerModule(JavaTimeModule())

    jsonBuilder.append(mapper.writeValueAsString(obj)).append("\n\n")

    // Common lock object for synchronization
    val lock = Any()

    synchronized(lock) {
        try {
            Files.createFile(pathToFile)
        } catch (e: java.nio.file.FileAlreadyExistsException) {
            // Ignore if the file already exists
        }

        if (append) {
            Files.writeString(pathToFile, jsonBuilder.toString(), StandardOpenOption.APPEND)
        } else {
            Files.writeString(pathToFile, jsonBuilder.toString(), StandardOpenOption.TRUNCATE_EXISTING)
        }
    }
}