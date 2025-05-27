package org.variantsync.evaluation.analysis

import org.variantsync.evaluation.execution.*
import java.io.File
import java.nio.file.Path
import java.text.NumberFormat
import java.util.*
import kotlin.collections.HashMap
import kotlin.system.exitProcess

val n = 100

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
    val mergedResults = HashMap<String, Int>()
    for (compositionResult in compositionResults) {
        val lang = compositionResult.evalRun.datasetName.split("_").first()
        for (entry in compositionResult.fileMap) {
            val key = entry.key
            mergedResults[key] = mergedResults.getOrDefault(key, 0) + entry.value
        }
    }
   // val mergedResults = mergeMaps(compositionResults.map { r -> r.fileMap })

    // How often are the patches of the top-10 most-patched pure?
    val numberFormatter = NumberFormat.getNumberInstance(Locale.US)
    val percentageFormatter = NumberFormat.getPercentInstance(Locale.US)

    val topN = topNValues(mergedResults, n)
    println("Number of file types: " + mergedResults.size)
    println("++++++++++++++++++++++++++++++++")
    println(" There are " + numberFormatter.format(mergedResults.values.sum()) + " patched files")
    println("+++++ TOP $n PATCHED FILES +++++")
    val maxKeyLength = topN.maxOf { it.first.length }
    val maxValLength = topN.maxOf { numberFormatter.format(it.second).length }
    for (t in topN) {
        val percentage =  (t.second.toDouble() / mergedResults.values.sum().toDouble())
    println("${t.first.padEnd(maxKeyLength)} : ${numberFormatter.format(t.second).padStart(maxValLength)}   ${percentageFormatter.format(percentage)}")
    }
    println("-------------------------------")
    println()

    val topNpure = HashMap<String, Int>()
    for (t in topN) {
        topNpure[t.first] = 0
    }

    var impurePatches = 0
    for (r in compositionResults) {
        if (r.fileMap.size == 1) {
            val key = r.fileMap.keys.first()
            if (topNpure.containsKey(key)) {
                topNpure[key] = topNpure[key]!! + 1
            }
        } else {
            impurePatches++
        }
    }

    println(" There are " + numberFormatter.format(compositionResults.size) + " patches")
    println(" There are $impurePatches impure patches")
    println("+++++ TOP $n PURE PATCHES +++++")
    for (pure in topNpure.entries
        .sortedByDescending { it.value }) {
        val percentage =  pure.value.toDouble() / compositionResults.size.toDouble()
        println("${pure.key.padEnd(maxKeyLength)} : ${numberFormatter.format(pure.value).padStart(maxValLength)}   ${percentageFormatter.format(percentage)}")
    }
    println("-------------------------------")
    println()

    val languageRelatedFiles = listOf("java", "py", "go", "js", "cpp", "hpp", "c", "h", "ts", "cs", "php", "rs")
    var numLangRelPatches = 0
    var numLangRelFilePatches = 0
    for (l in languageRelatedFiles) {
        numLangRelFilePatches += mergedResults[l]!!
        numLangRelPatches += topNpure[l]!!
    }
    val relPatchesPercentage = 100.0 * (numLangRelPatches.toDouble() / compositionResults.size)
    val relFilePatchesPercentage = 100.0 * (numLangRelFilePatches.toDouble() / mergedResults.values.sum())
    println("+++++ LANG RELATED PATCHES +++++")
    println("There are " + numberFormatter.format(numLangRelFilePatches) + " patched files related to the top langs. (" + relFilePatchesPercentage + "%)")
    println("There are " + numberFormatter.format(numLangRelPatches) + " pure patches related to the top langs. (" + relPatchesPercentage + "%)")
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