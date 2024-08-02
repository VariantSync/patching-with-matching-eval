package org.anon.evaluation.execution

import org.tinylog.kotlin.Logger
import org.anon.evaluation.loadPRDatasets
import java.io.File
import java.io.IOException
import java.io.UncheckedIOException

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        System.err.println(
            "The first argument should provide the path to the configuration file that is to be used"
        )
    }
    val config = EvalConfig(File(args[0]))
    val datasets: Map<String, MutableList<CherryDataset>> = try {
        org.anon.evaluation.loadPRDatasets(config)
    } catch (e: IOException) {
        Logger.error(
            "Was not able to load the yaml datasets from '"
                    + config.EXPERIMENT_DATASETS() + "'"
        )
        throw UncheckedIOException(e)
    }

    var totalNumberOfCherryPicks = 0
    for (datasetList in datasets.values) {
        for (dataset in datasetList) {
            totalNumberOfCherryPicks += dataset.cherryPicks.size
        }
    }

    Logger.info("There are $totalNumberOfCherryPicks cherry picks to consider for all languages.")
}