package org.variantsync.evaluation

import org.tinylog.kotlin.Logger
import org.variantsync.diffdetective.datasets.DatasetDescription
import org.variantsync.diffdetective.load.GitLoader
import org.variantsync.vevos.simulation.VEVOS
import java.io.File
import java.io.IOException
import java.io.UncheckedIOException
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
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
        val config = StudyConfiguration(File(args[0]))
        Logger.info("Starting experiment initialization.")
        val mainDir = Path.of(config.EXPERIMENT_DIR_MAIN())
        val resultsDir = Path.of(config.EXPERIMENT_DIR_RESULTS())
        val reposDir = Path.of(config.EXPERIMENT_DIR_REPOS())
        val inDebug = config.EXPERIMENT_DEBUG()
        val groundTruthPath = Path.of(config.EXPERIMENT_DIR_GROUND_TRUTH())
        val numRepetitions = config.EXPERIMENT_REPEATS()
        val numVariants = config.EXPERIMENT_VARIANT_COUNT()
        val startID = config.EXPERIMENT_START_ID()
        val datasets: List<DatasetDescription> = try {
            DatasetDescription.fromMarkdown(Path.of(config.EXPERIMENT_DATASETS()))
        } catch (e: IOException) {
            Logger.error(
                "Was not able to load markdown file with the datasets from '"
                        + config.EXPERIMENT_DATASETS() + "'"
            )
            throw UncheckedIOException(e)
        }
        val datasetMaxSize = config.EXPERIMENT_DATASET_MAX_SIZE()
        for (dataset in datasets) {
            val datasetSize = dataset.commits().replace(",".toRegex(), "").toInt()
            if (datasetSize > datasetMaxSize) {
                Logger.info(
                    "Skipping %s with %s commits because it exceeds the maximum number of commits (%d) set in the configuration.".format(
                        dataset.name(),
                        dataset.commits(),
                        datasetMaxSize
                    )
                )
                continue
            }
            val repoDir = reposDir.resolve(dataset.name())
            val repoGroundTruth = groundTruthPath.resolve(dataset.name())
            if (!Files.exists(repoGroundTruth)) {
                Logger.info(
                    "Found no ground truth for %s. Skipping the study for %s"
                        .format(dataset.name(), dataset.name())
                )
                continue
            }
            GitLoader.fromRemote(repoDir, URI.create(dataset.repoURL()))
                .use { Logger.info("Cloned %s into %s".format(dataset.name(), repoDir)) }
            val numThreads = config.EXPERIMENT_THREAD_COUNT()
            val idProvider = IDProvider(startID)
            val synchronizationStudy = SynchronizationStudy(
                dataset.name(), mainDir, resultsDir, repoDir, repoGroundTruth,
                numRepetitions, numVariants, idProvider, inDebug, numThreads
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
