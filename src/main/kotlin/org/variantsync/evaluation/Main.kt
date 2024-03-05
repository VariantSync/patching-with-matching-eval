package org.variantsync.evaluation

import org.tinylog.kotlin.Logger
import org.variantsync.diffdetective.datasets.DatasetDescription
import org.variantsync.diffdetective.load.GitLoader
import org.variantsync.evaluation.vevos.SynchronizationStudy
import org.variantsync.vevos.simulation.VEVOS
import java.io.File
import java.io.IOException
import java.io.UncheckedIOException
import java.net.URI
import java.nio.file.Files
import kotlin.system.exitProcess

/**
 * Entry point for running our study. Loads the configuration and starts the study.
 */
object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        if (args.isEmpty()) {
            System.err.println(
                "The first argument should provide the path to the configuration file that is to be used"
            )
        }
        // Initialize the VEVOS Simulation library
        VEVOS.Initialize()
        val config = EvalConfig(File(args[0]))
        Logger.info("Starting experiment initialization.")
        val datasets: List<DatasetDescription> = try {
            DatasetDescription.fromMarkdown(config.EXPERIMENT_DATASETS())
        } catch (e: IOException) {
            Logger.error(
                "Was not able to load markdown file with the datasets from '"
                        + config.EXPERIMENT_DATASETS() + "'"
            )
            throw UncheckedIOException(e)
        }
        for (dataset in datasets) {
            val datasetSize = dataset.commits().replace(",".toRegex(), "").toInt()
            if (datasetSize > config.EXPERIMENT_DATASET_MAX_SIZE()) {
                Logger.info(
                    "Skipping %s with %s commits because it exceeds the maximum number of commits (%d) set in the configuration.".format(
                        dataset.name(),
                        dataset.commits(),
                        config.EXPERIMENT_DATASET_MAX_SIZE()
                    )
                )
                continue
            }
            val repoDir = config.EXPERIMENT_DIR_REPOS().resolve(dataset.name())
            val repoGroundTruth = config.EXPERIMENT_DIR_GROUND_TRUTH().resolve(dataset.name())
            if (!Files.exists(repoGroundTruth)) {
                Logger.info(
                    "Found no ground truth for %s. Skipping the study for %s"
                        .format(dataset.name(), dataset.name())
                )
                continue
            }
            GitLoader.fromRemote(repoDir, URI.create(dataset.repoURL()))
                .use { Logger.info("Cloned %s into %s".format(dataset.name(), repoDir)) }
            val synchronizationStudy = SynchronizationStudy(
                config, dataset.name(), repoDir, repoGroundTruth
            )
            try {
                synchronizationStudy.run()
            } catch (e: Exception) {
                e.printStackTrace()
                Logger.error(e)
                exitProcess(1)
            }
        }
        exitProcess(0)
    }
}
