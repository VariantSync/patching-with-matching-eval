package org.variantsync.evaluation

import org.apache.commons.io.FileUtils
import org.eclipse.jgit.api.Git
import org.tinylog.kotlin.Logger
import org.variantsync.evaluation.execution.*
import org.variantsync.evaluation.util.shell.CpCommand
import org.variantsync.evaluation.util.shell.RmCommand
import org.variantsync.evaluation.util.shell.ShellExecutor
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.IOException
import java.io.UncheckedIOException
import java.math.RoundingMode
import java.nio.ByteBuffer
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.security.SecureRandom
import java.text.DecimalFormat
import java.util.*
import java.util.concurrent.BlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.math.max
import kotlin.math.min
import kotlin.system.exitProcess

class CherryPickStudy(
    val config: EvalConfig,
    dataset: CherryDataset,
    repetition: Int,
    idProvider: IDProvider,
    completedRuns: Set<EvaluationRun>,
) {
    // The study tasks that are to be executed in parallel
    private val evalTasks: MutableList<CherryPickEvalTask>
    private val numThreads: Int
    private val availableOperations: BlockingQueue<EvalOperations>

    /**
     * Initialize the study from the given configuration
     */
    init {
        if (!Files.exists(config.EXPERIMENT_DIR_RESULTS())) {
            Files.createDirectories(config.EXPERIMENT_DIR_RESULTS())
        }
        val repoPath: Path = cloneGitHubRepo(config, dataset.repositoryId)
        val t = min(config.EXPERIMENT_THREAD_COUNT(), dataset.cherryPicks.size / 100)
        this.numThreads = max(1, t)
        this.availableOperations = LinkedBlockingQueue(numThreads)

        Logger.info("Preparing working directories for $numThreads threads.")
        for (i in 1..numThreads) {
            // Add one operations instance for each thread; each instance defines its own working directory
            val operations = EvalOperations(config.EXPERIMENT_DIR_MAIN(), repoPath)
            // Clean old variant files
            cleanVariantDirectories(operations)
            // Copy the source and target variant to the respective variant directories
            prepareVariantDirectories(operations, repoPath)
            availableOperations.add(operations)
        }

        this.evalTasks = ArrayList()

        for (cherryPick in dataset.cherryPicks) {
            val runID = idProvider.next()
            val run = EvaluationRun(repetition, dataset.datasetName, cherryPick.cherryCommit, cherryPick.expectedResultCommit)
            if (completedRuns.contains(run)) {
                Logger.info("Skipped cherry pick of run $runID (already processed)")
                continue
            }
            evalTasks.add(
                CherryPickEvalTask(
                    repetition,
                    config,
                    dataset.datasetName,
                    cherryPick,
                    availableOperations,
                    runID,
                    run,
                )
            )
        }
    }

    /**
     * Execute the study.
     */
    fun run() {
        val threadPool = Executors.newFixedThreadPool(numThreads)
        Logger.info("Scheduling ${evalTasks.size} tasks...")

        val futures = evalTasks.stream()
            .map { runnable: CherryPickEvalTask -> FutureAndEvalRun(threadPool.submit(runnable), runnable.evalRun) }
            .collect(Collectors.toList())

        Logger.info("Scheduled all tasks.")

        val hadTimeout = waitForShutdown(threadPool, futures, config)

        if (hadTimeout) {
            Logger.info("Timeout detected. Marking task of ${evalTasks.first().evalRun.datasetName} as completed.")
            for (evalTask in evalTasks) {
                markEvalRun(evalTask.evalRun, config.EXPERIMENT_PROCESSED_FILE())
            }
        }

        Logger.info("Running clean up.")
        // Delete all workdirs
        for (operations in this.availableOperations) {
            try {
                FileUtils.deleteDirectory(operations.workDir.toFile())
            } catch (e: Exception) {
                Logger.debug(e)
                if (Files.exists(operations.workDir)) {
                    Logger.debug("Trying to remove directory with 'rm -rf'")
                    if (ShellExecutor(Logger::warn, Logger::warn, operations.workDir)
                        .execute(RmCommand(operations.workDir).recursive().force()).isSuccess) {
                        Logger.debug("Success!")
                    }
                }
            }
        }
        Logger.info("Cleaned all working directories.")
    }
}


fun main(args: Array<String>) {
    if (args.isEmpty()) {
        System.err.println(
            "The first argument should provide the path to the configuration file that is to be used"
        )
    }
    val config = EvalConfig(File(args[0]))
    Logger.info("Starting experiment initialization.")
    val datasetsPerLanguage: Map<String, MutableList<CherryDataset>> = try {
        loadPRDatasets(config)
    } catch (e: IOException) {
        Logger.error(
            "Was not able to load the yaml datasets from '"
                    + config.EXPERIMENT_DATASETS() + "'"
        )
        throw UncheckedIOException(e)
    }

    val seed: ByteArray = ByteBuffer.allocate(java.lang.Long.BYTES).putLong(config.EXPERIMENT_REPEATS_START()
            + config.SEED()).array()
    val idProvider = IDProvider(config.EXPERIMENT_START_ID())
    var id = 0uL
    val rand = SecureRandom(seed)
    val allSamples = createOrLoadSamples(config, datasetsPerLanguage, rand)

    cloneDatasets(allSamples, config)

    val n = 5
    Logger.info("Processing $n repos in parallel")
    val threadPool = Executors.newFixedThreadPool(n)

    val completedRunsAll = loadCompletedRuns(config)

    for (repetition in config.EXPERIMENT_REPEATS_START()..config.EXPERIMENT_REPEATS_END()) {
        val repetitionIndex = repetition - config.EXPERIMENT_REPEATS_START()
        val numCherryPicks = countCherryPicks(allSamples[repetitionIndex])

        val completedRuns = completedRunsAll.getOrDefault(repetition, HashMap())
        var completed = 0
        Logger.info("Already considered ${completedRuns.size} repos.")
        completedRuns.forEach { s -> completed += s.value.size}
        Logger.info("Processed a total of $completed evaluation runs.\n")
        Thread.sleep(5000)


        Logger.info("Considering a total of $numCherryPicks cherry-picks for repetition $repetition")
        for (dataset in allSamples[repetitionIndex]) {
            while (idProvider.next() < id) {}
            id += dataset.cherryPicks.size.toUInt()

            if (completedRuns.contains(dataset.datasetName) && completedRuns[dataset.datasetName]!!.size == dataset.cherryPicks.size) {
                // Skip this dataset, it was already processed
                Logger.info("Skipping evaluation of cherry picks from ${dataset.datasetName} (rep.: $repetition)")
                printProgress(completed, numCherryPicks, repetition, 0uL)
                continue
            }
            threadPool.submit {
                val i = id
                Logger.info("Preparing evaluation of cherry picks from ${dataset.datasetName}")
                val study = CherryPickStudy(config, dataset, repetition, idProvider, completedRuns.getOrDefault(dataset.datasetName, HashSet()))
                try {
                    study.run()
                } catch (e: Exception) {
                    e.printStackTrace()
                    Logger.error(e)
                }
                completed += dataset.cherryPicks.size
                printProgress(completed, numCherryPicks, repetition, i)
            }
        }
        threadPool.awaitTermination(10, TimeUnit.DAYS)
    }
    threadPool.shutdown()

    exitProcess(0)
}

