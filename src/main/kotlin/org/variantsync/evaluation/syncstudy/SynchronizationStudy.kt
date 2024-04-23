package org.variantsync.evaluation.syncstudy

import org.tinylog.kotlin.Logger
import org.variantsync.diffdetective.datasets.DatasetDescription
import org.variantsync.diffdetective.load.GitLoader
import org.variantsync.evaluation.EvalConfig
import org.variantsync.evaluation.determineSampleSize
import org.variantsync.evaluation.waitForShutdown
import org.variantsync.functjonal.iteration.ClusteredIterator
import org.variantsync.vevos.simulation.VEVOS
import org.variantsync.vevos.simulation.io.Resources
import org.variantsync.vevos.simulation.io.data.VariabilityDatasetLoader
import org.variantsync.vevos.simulation.variability.SPLCommit
import org.variantsync.vevos.simulation.variability.VariabilityDataset
import java.io.File
import java.io.IOException
import java.io.UncheckedIOException
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.concurrent.Executors
import java.util.stream.Collectors
import kotlin.collections.ArrayList
import kotlin.math.ceil
import kotlin.system.exitProcess

/**
 * This class contains the core workflow of our study as described in our paper.
 */
class SynchronizationStudy(
    config: EvalConfig,
    datasetName: String,
    repositoryPath: Path,
    groundTruthPath: Path
) {
    // Path to the ground truth dataset
    private val groundTruthPath: Path

    // The study tasks that are to be executed in parallel
    private val syncStudyTasks: MutableList<SyncStudyTask>
    private val numThreads: Int

    /**
     * Initialize the study from the given configuration
     */
    init {
        if (!Files.exists(config.EXPERIMENT_DIR_RESULTS())) {
            Files.createDirectories(config.EXPERIMENT_DIR_RESULTS())
        }
        this.groundTruthPath = groundTruthPath
        this.numThreads = config.EXPERIMENT_THREAD_COUNT()
        var history = init()

        if (config.EXPERIMENT_ENABLE_SAMPLING()) {
            val sampleSize = determineSampleSize(config, history.size)
            Logger.info("Considering a representative sample of $sampleSize commits.")
            history = history.shuffled().subList(0, sampleSize)
        }

        syncStudyTasks = ArrayList()
        val clusterSize = ceil(history.size.toDouble() / numThreads).toInt()
        val commitClusterIterator = ClusteredIterator(history.iterator(), clusterSize)
        while (commitClusterIterator.hasNext()) {
            val commits = commitClusterIterator.next()
            syncStudyTasks.add(
                SyncStudyTask(config, datasetName, repositoryPath, commits)
            )
        }
    }

    /**
     * Execute the study.
     */
    fun run() {
        val threadPool = Executors.newFixedThreadPool(numThreads)
        val futures = syncStudyTasks.stream()
            .map { runnable: SyncStudyTask -> threadPool.submit(runnable) }
            .collect(Collectors.toList())
        waitForShutdown(threadPool, futures)
    }

    // Initialize the study by loading the required data
    private fun init(): List<SPLCommit> {
        // Load VariabilityDataset
        Logger.info("Loading variability dataset.")
        val dataset: VariabilityDataset
        try {
            val instance = Resources.Instance()
            val datasetLoader = VariabilityDatasetLoader()
            instance.registerLoader(VariabilityDataset::class.java, datasetLoader)
            dataset = instance.load(VariabilityDataset::class.java, groundTruthPath)
            Logger.info("Dataset loaded.")
        } catch (e: Resources.ResourceIOException) {
            throw RuntimeException("Was not able to load dataset.", e)
        }

        // Retrieve pairs/sequences of usable commits
        Logger.info("Retrieving commits")
        return Objects.requireNonNull(dataset).successCommits
    }
}

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
