package org.variantsync.evaluation.execution

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.function.Consumer
import org.variantsync.evaluation.analysis.TaskResult

fun saveResult(
        result: TaskResult,
        runID: ULong,
) {
    try {
        writeAsJSON(result.resultObj, result.pathToResultFile, true)
    } catch (e: IOException) {
        panic("Was not able to write filtered patch result file for run " + runID, e)
    }
}

fun markEvalRun(
        evalRun: EvaluationRun,
        path: Path,
) {
    try {
        writeAsJSON(evalRun, path, true)
    } catch (e: IOException) {
        panic("Was not able to mark eval run in file $path ", e)
    }
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
            Files.createDirectories(pathToFile.parent)
            Files.createFile(pathToFile)
        } catch (_: java.nio.file.FileAlreadyExistsException) {
            // Ignore if the file already exists
        }

        if (append) {
            Files.writeString(pathToFile, jsonBuilder.toString(), StandardOpenOption.APPEND)
        } else {
            Files.writeString(
                    pathToFile,
                    jsonBuilder.toString(),
                    StandardOpenOption.TRUNCATE_EXISTING
            )
        }
    }
}

fun saveSample(path: Path, sample: ArrayList<ArrayList<CherryDataset>>) {
    if (path.nameCount > 1) {
        Files.createDirectories(path.parent)
    }
    ObjectOutputStream(FileOutputStream(path.toFile())).use { it.writeObject(sample) }
}

fun loadSample(path: Path): ArrayList<ArrayList<CherryDataset>> {
    ObjectInputStream(FileInputStream(path.toFile())).use { it ->
        val obj = it.readObject()
        @Suppress("UNCHECKED_CAST") return obj as ArrayList<ArrayList<CherryDataset>>
    }
}

@Throws(IOException::class)
fun loadProcessedRuns(config: EvalConfig): MutableList<EvaluationRun> {
    val runs = ArrayList<EvaluationRun>()
    if (!Files.exists(config.EXPERIMENT_PROCESSED_FILE())) {
        return runs
    }
    Files.newBufferedReader(config.EXPERIMENT_PROCESSED_FILE()).use { reader ->
        val evalRunLines: MutableList<String> = ArrayList()
        var line = reader.readLine()
        while (line != null) {
            if (line.isEmpty()) {
                val evalRun = parseEvalRun(evalRunLines)
                runs.add(evalRun)
                evalRunLines.clear()
            } else {
                evalRunLines.add(line)
            }
            line = reader.readLine()
        }
    }
    return runs
}

private fun parseEvalRun(lines: List<String>): EvaluationRun {
    val sb = StringBuilder()
    lines.forEach(Consumer { l: String? -> sb.append(l).append("\n") })
    val mapper = jacksonObjectMapper()
    mapper.registerModule(JavaTimeModule())
    return mapper.readValue(sb.toString(), EvaluationRun::class.java)
}