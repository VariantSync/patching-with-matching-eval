package org.variantsync.evaluation.analysis

import org.variantsync.evaluation.execution.*
import java.io.File
import java.nio.file.Path
import java.text.NumberFormat
import java.util.*
import kotlin.collections.HashMap
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        System.err.println(
            "The first argument should provide the path to the configuration file that is to be used"
        )
    }
    val config = EvalConfig(File(args[0]))

    val resultFiles = findResults(config.EXPERIMENT_DIR_RESULTS())
    val compositionResults = loadCompositionResults(resultFiles)

    // What are the top-10 most-patched file types?
    val mergedResults = mergeMaps(compositionResults.map { r -> r.fileMap })

    // How often are the patches of the top-10 most-patched pure?
    val numberFormatter = NumberFormat.getNumberInstance(Locale.US)

    val top10 = topNValues(mergedResults, 10)
    println("++++++++++++++++++++++++++++++++")
    println(" There are " + numberFormatter.format(mergedResults.values.sum()) + " patched files")
    println("+++++ TOP 10 PATCHED FILES +++++")
    val maxKeyLength = top10.maxOf { it.first.length }
    val maxValLength = top10.maxOf { numberFormatter.format(it.second).length }
    for (t in top10) {
        println("${t.first.padEnd(maxKeyLength)} : ${numberFormatter.format(t.second).padStart(maxValLength)}")
    }
    println("-------------------------------")
    println()

    val top10pure = HashMap<String, Int>()
    for (t in top10) {
        top10pure[t.first] = 0
    }

    for (r in compositionResults) {
        if (r.fileMap.size == 1) {
            val key = r.fileMap.keys.first()
            if (top10pure.containsKey(key)) {
                top10pure[key] = top10pure[key]!! + 1
            }
        }
    }

    println(" There are " + numberFormatter.format(compositionResults.size) + " patches")
    println("+++++ TOP 10 PURE PATCHES +++++")
    for (pure in top10pure.entries
        .sortedByDescending { it.value }) {
        println("${pure.key.padEnd(maxKeyLength)} : ${numberFormatter.format(pure.value).padStart(maxValLength)}")
    }
    println("-------------------------------")
    println()

    exitProcess(0)
}

fun findFilesByPostfix(directoryPath: Path, postfix: String): List<Path> {
    return directoryPath.toFile().listFiles { file -> file.isFile && file.name.endsWith(postfix) }
        ?.map { it.toPath() }
        ?: emptyList()
}

fun findResults(directoryPath: Path): List<Path> {
    val postfix = ".composition"
    return findFilesByPostfix(directoryPath, postfix)
}

fun mergeMaps(maps: List<Map<String, Int>>): Map<String, Int> {
    return maps.fold(mutableMapOf()) { acc, map ->
        map.forEach { (key, value) ->
            acc.merge(key, value, Int::plus)
        }
        acc
    }
}

fun topNValues(map: Map<String, Int>, n: Int): List<Pair<String, Int>> {
    return map.entries
        .sortedByDescending { it.value }
        .take(n)
        .map { it.key to it.value }
}