package org.variantsync.evaluation

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.variantsync.evaluation.analysis.PatchOutcome
import org.variantsync.evaluation.analysis.ExperimentResult
import org.variantsync.evaluation.execution.CherryDataset
import org.variantsync.evaluation.execution.EvaluationRun
import org.variantsync.evaluation.execution.panic
import java.io.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.function.Consumer

fun saveResult(
    result: ExperimentResult,
    runID: ULong,
) {
    try {
        writeAsJSON(result.resultObj, result.pathToResultFile, true)
    } catch (e: IOException) {
        panic(
            "Was not able to write filtered patch result file for run "
                    + runID, e
        )
    }
}

fun markEvalRun(
    evalRun: EvaluationRun,
    path: Path,
) {
    try {
        writeAsJSON(evalRun, path, true)
    } catch (e: IOException) {
        panic(
            "Was not able to mark eval run in file $path ", e
        )
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

fun saveSample(path: Path, sample: ArrayList<ArrayList<CherryDataset>>) {
    if (path.nameCount > 1) {
        Files.createDirectories(path.parent)
    }
    ObjectOutputStream(FileOutputStream(path.toFile())).use { it.writeObject(sample) }
}

fun loadSample(path: Path): ArrayList<ArrayList<CherryDataset>> {
    ObjectInputStream(FileInputStream(path.toFile())).use { return it.readObject() as ArrayList<ArrayList<CherryDataset>> }
}


@Throws(IOException::class)
fun loadResultObjects(paths: HashMap<Int, ArrayList<Path>>): HashMap<Int, MutableList<PatchOutcome>> {
    val outcomes = HashMap<Int, MutableList<PatchOutcome>>()
    for (rep in paths.keys) {
        for (path in paths[rep]!!) {
            Files.newBufferedReader(path).use { reader ->
                val outcomeLines: MutableList<String> = ArrayList()
                var line = reader.readLine()
                while (line != null) {
                    if (line.isEmpty()) {
                        val outcome = parseResult(outcomeLines)
                        outcomes.getOrPut(rep) { ArrayList() }.add(outcome)
                        outcomeLines.clear()
                    } else {
                        outcomeLines.add(line)
                    }
                    line = reader.readLine()
                }
            }
        }
    }
    return outcomes
}

@Throws(IOException::class)
fun loadProcessedRuns(config: EvalConfig): MutableList<EvaluationRun> {
    val runs = ArrayList<EvaluationRun>();
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

private fun parseResult(lines: List<String>): PatchOutcome {
    val sb = StringBuilder()
    lines.forEach(Consumer { l: String? -> sb.append(l).append("\n") })
    val mapper = jacksonObjectMapper()
    mapper.registerModule(JavaTimeModule())
    return mapper.readValue(sb.toString(), PatchOutcome::class.java)
}

fun listResultFiles(resultsDir: Path) : HashMap<Int, ArrayList<Path>> {
    val runDirs = ArrayList<Path>()
    Files.list(resultsDir).use { files ->
        files.filter { f: Path ->
            val fileName = f.getName(f.nameCount-1).toString()
            fileName.startsWith("rep")
        }.forEach { f: Path ->
            runDirs.add(f)
        }
    }
    val resultFiles = HashMap<Int, ArrayList<Path>>()

    for (runDir in runDirs) {
        Files.list(runDir).use { files ->
            files.filter { f: Path ->
                val fileName = f.fileName.toString()
                fileName.endsWith(".results")
            }.forEach { f: Path ->
                    val runId = f.getName(f.nameCount-1).toString().split("-")[1].toInt()
                    resultFiles.getOrPut(runId) { ArrayList() }.add(f)
            }
        }
    }

    return resultFiles
}