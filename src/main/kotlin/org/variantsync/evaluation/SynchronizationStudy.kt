package org.variantsync.evaluation

import org.tinylog.kotlin.Logger
import org.variantsync.functjonal.iteration.ClusteredIterator
import org.variantsync.vevos.simulation.io.Resources
import org.variantsync.vevos.simulation.io.data.VariabilityDatasetLoader
import org.variantsync.vevos.simulation.variability.SPLCommit
import org.variantsync.vevos.simulation.variability.VariabilityDataset
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors
import kotlin.math.ceil

/**
 * This class contains the core workflow of our study as described in our paper.
 */
class SynchronizationStudy(
    config: StudyConfiguration,
    datasetName: String,
    repositoryPath: Path,
    groundTruthPath: Path
) {
    // Path to the ground truth dataset
    private val groundTruthPath: Path

    // The study tasks that are to be executed in parallel
    private val VEVOSEvalTasks: MutableList<VEVOSEvalTask>
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
        val history = init()
        VEVOSEvalTasks = ArrayList()
        val clusterSize = ceil(history.size.toDouble() / numThreads).toInt()
        val commitClusterIterator = ClusteredIterator(history.iterator(), clusterSize)
        while (commitClusterIterator.hasNext()) {
            val commits = commitClusterIterator.next()
            VEVOSEvalTasks.add(
                VEVOSEvalTask(config, datasetName, repositoryPath, commits)
            )
        }
    }

    /**
     * Execute the study.
     */
    fun run() {
        val threadPool = Executors.newFixedThreadPool(numThreads)
        val futures = VEVOSEvalTasks.stream()
            .map { runnable: VEVOSEvalTask -> threadPool.submit(runnable) }
            .collect(Collectors.toList())
        threadPool.shutdown()
        for (future in futures) {
            try {
                future.get()
            } catch (e: Throwable) {
                Logger.error("Failed to finish task!")
                Logger.error(e)
                e.printStackTrace()
            }
        }
        if (!threadPool.awaitTermination(7, TimeUnit.DAYS)) {
            Logger.error("Thread pool timeout.")
        }

        Logger.info("All done.")
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
