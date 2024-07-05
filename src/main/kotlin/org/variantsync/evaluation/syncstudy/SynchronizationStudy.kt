package org.variantsync.evaluation.syncstudy

import org.apache.commons.io.FileUtils
import org.tinylog.kotlin.Logger
import org.variantsync.diffdetective.datasets.DatasetDescription
import org.variantsync.diffdetective.load.GitLoader
import org.variantsync.evaluation.EvalConfig
import org.variantsync.evaluation.IDProvider
import org.variantsync.evaluation.baseline.shell.CpCommand
import org.variantsync.evaluation.baseline.shell.RmCommand
import org.variantsync.evaluation.determineSampleSize
import org.variantsync.evaluation.waitForShutdown
import org.variantsync.vevos.simulation.VEVOS
import org.variantsync.vevos.simulation.io.Resources
import org.variantsync.vevos.simulation.io.data.VariabilityDatasetLoader
import org.variantsync.vevos.simulation.repository.SPLRepository
import org.variantsync.vevos.simulation.variability.SPLCommit
import org.variantsync.vevos.simulation.variability.VariabilityDataset
import java.io.File
import java.io.IOException
import java.io.UncheckedIOException
import java.net.URI
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import java.util.*
import java.util.concurrent.BlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.stream.Collectors
import kotlin.system.exitProcess

/**
 * This class contains the core workflow of our study as described in our paper.
 */
class SynchronizationStudy(
    private val config: EvalConfig,
    private val datasetName: String,
    private val repositoryPath: Path,
    private val groundTruthPath: Path
) {
    // Path to the ground truth dataset
    private val idProvider: IDProvider
    private val parentRepos: Map<SyncStudyOperations, SPLRepository>
    private val childRepos: Map<SyncStudyOperations, SPLRepository>
    private val availableOperations: BlockingQueue<SyncStudyOperations>

    // The study tasks that are to be executed in parallel
    private val evalTasks: MutableList<SyncStudyTask>
    private val numThreads: Int

    /**
     * Initialize the study from the given configuration
     */
    init {
        if (!Files.exists(config.EXPERIMENT_DIR_RESULTS())) {
            Files.createDirectories(config.EXPERIMENT_DIR_RESULTS())
        }
        this.numThreads = config.EXPERIMENT_THREAD_COUNT()
        var history = init()

        if (config.EXPERIMENT_ENABLE_SAMPLING()) {
            val sampleSize = determineSampleSize(config, history.size)
            Logger.info("The dataset comprises a total of " + history.size + " commits.")
            if (history.size < sampleSize) {
                Logger.info("Skipping $datasetName because it contains fewer commits than the sample size of $sampleSize.")
            } else {
                Logger.info("Considering a representative sample of $sampleSize commits.")
                val seed: ByteArray = ByteBuffer.allocate(java.lang.Long.BYTES).putLong(config.SEED()).array()
                history = history.shuffled(SecureRandom(seed)).subList(0, sampleSize)
            }
        }

        this.evalTasks = ArrayList()
        this.availableOperations = LinkedBlockingQueue(numThreads)
        this.parentRepos = HashMap()
        this.childRepos = HashMap()

        for (i in 1..numThreads) {
            // Add one operations instance for each thread; each instance defines its own working directory
            val operations = SyncStudyOperations(config.EXPERIMENT_DIR_MAIN())
            val parentRepo = SPLRepository(operations.splCopyA)
            val childRepo = SPLRepository(operations.splCopyB)
            availableOperations.add(operations)
            parentRepos[operations] = parentRepo
            childRepos[operations] = childRepo
            // Clean old SPL repo files and create new ones
            initializeSPLCopies(operations)
        }

        idProvider = IDProvider(config.EXPERIMENT_START_ID())

        for (commit in history) {
            val runID = idProvider.next()
            if (runID < idProvider.start) {
                Logger.info("Skipped commit $runID")
                continue
            }
            evalTasks.add(
                SyncStudyTask(
                    config,
                    datasetName,
                    commit,
                    availableOperations,
                    parentRepos,
                    childRepos,
                    runID
                )
            )
        }
    }

    private fun initializeSPLCopies(operations: SyncStudyOperations) {
        // Clean old SPL repo files
        Logger.debug("Cleaning old repo files.")
        if (Files.exists(operations.splCopyA)) {
            operations.shell.execute(RmCommand(operations.splCopyA).recursive())
                .expect("Was not able to remove SPL-V0.")
        }
        if (Files.exists(operations.splCopyB)) {
            operations.shell.execute(RmCommand(operations.splCopyB).recursive())
                .expect("Was not able to remove SPL-V1.")
        }
        // Copy the SPL repo
        Logger.debug("Creating new SPL repo copies.")
        operations.shell.execute(CpCommand(this.repositoryPath, operations.splCopyA).recursive())
            .expect("Was not able to copy SPL-V0.")
        operations.shell.execute(CpCommand(this.repositoryPath, operations.splCopyB).recursive())
            .expect("Was not able to copy SPL-V1.")
    }

    /**
     * Execute the study.
     */
    fun run() {
        val threadPool = Executors.newFixedThreadPool(numThreads)
        Logger.info("Starting diffing and patching for SPL commits...")

        val futures = evalTasks.stream()
            .map { runnable: SyncStudyTask -> threadPool.submit(runnable) }
            .collect(Collectors.toList())

        waitForShutdown(threadPool, futures)

        // Finally, close all repos
        for (parentRepo in this.parentRepos.values) {
            parentRepo.close()
        }

        // Finally, close all repos
        for (childRepo in this.childRepos.values) {
            childRepo.close()
        }

        // And delete all workdirs
        for (operations in this.availableOperations) {
            FileUtils.deleteDirectory(operations.workDir.toFile())
        }
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
        }
    }
    exitProcess(0)
}
