package org.variantsync.evaluation

import org.tinylog.kotlin.Logger
import org.variantsync.functjonal.iteration.ClusteredIterator
import org.variantsync.vevos.simulation.io.Resources
import org.variantsync.vevos.simulation.io.data.VariabilityDatasetLoader
import org.variantsync.vevos.simulation.variability.SPLCommit
import org.variantsync.vevos.simulation.variability.VariabilityDataset
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
    datasetName: String, mainDir: Path, resultsDir: Path,
    repositoryPath: Path, groundTruthPath: Path, numRepetitions: Int, numVariants: Int,
    idProvider: IDProvider, inDebug: Boolean, numThreads: Int
) {
    // Path to the ground truth dataset
    private val groundTruthPath: Path

    // The study tasks that are to be executed in parallel
    private val tasks: MutableList<Task>
    private val numThreads: Int

    /**
     * Initialize the study from the given configuration
     */
    init {
        val resultFile = resultsDir.resolve("$datasetName.results")
        this.groundTruthPath = groundTruthPath
        this.numThreads = numThreads
        val history = init()
        tasks = ArrayList()
        val clusterSize = ceil(history.size.toDouble() / numThreads).toInt()
        val commitClusterIterator = ClusteredIterator(history.iterator(), clusterSize)
        while (commitClusterIterator.hasNext()) {
            val commits = commitClusterIterator.next()
            tasks.add(
                Task(
                    datasetName, mainDir, repositoryPath, resultFile,
                    commits, numRepetitions, numVariants, inDebug, idProvider
                )
            )
        }
    }

    /**
     * Execute the study.
     */
    fun run() {
        try {
            val threadPool = Executors.newFixedThreadPool(numThreads)
            val futures = tasks.stream()
                .map { runnable: Task -> threadPool.submit(runnable) }
                .collect(Collectors.toList())
            threadPool.shutdown()
            for (future in futures) {
                future.get()
            }
            if (!threadPool.awaitTermination(7, TimeUnit.DAYS)) {
                Logger.error("Thread pool timeout.")
            }
        } catch (e: Exception) {
            throw RuntimeException(e)
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
