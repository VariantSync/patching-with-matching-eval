package org.variantsync.evaluation

import java.io.File
import java.io.IOException
import java.io.UncheckedIOException
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
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
import org.apache.commons.io.FileUtils
import org.tinylog.kotlin.Logger
import org.variantsync.evaluation.execution.*
import org.variantsync.evaluation.util.shell.RmCommand
import org.variantsync.evaluation.util.shell.ShellExecutor

// What this analysis has to do
// clone repos
// checkout source version before and after changes and setup locations for it
// diff the source to get a patch
// analyze the composition of the patch
// For each patch, extract the file endings of the patched files
// Save the result
// Print some statistics
// What are the ten most commonly patched file types? How many percent are they of all file types?
// How many file patches affect C source code?
// How many file patches affect PHP source code?
// How many patches affect only C code?
// How many patches affect only PHP code?
class PatchCompositionAnalysis(
        val config: EvalConfig,
        dataset: CherryDataset,
        idProvider: IDProvider,
        completedRuns: Set<EvaluationRun>,
) {
    // The study tasks that are to be executed in parallel
    private val evalTasks: MutableList<PatchCompositionTask>
    private val numThreads: Int
    private val availableOperations: BlockingQueue<CompositionAnalysisOperations>

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
            // Add one operations instance for each thread; each instance defines its own working
            // directory
            val operations = CompositionAnalysisOperations(config, repoPath)
            // Clean old variant files
            cleanVariantDirectories(operations)
            // Copy the source and target variant to the respective variant directories
            prepareVariantDirectories(operations, repoPath)
            availableOperations.add(operations)
        }

        this.evalTasks = ArrayList()

        for (cherryPick in dataset.cherryPicks) {
            val runID = idProvider.next()
            val run =
                    EvaluationRun(
                            0,
                            dataset.datasetName,
                            cherryPick.cherryCommit,
                            cherryPick.expectedResultCommit
                    )
            if (completedRuns.contains(run)) {
                Logger.info("Skipped cherry pick of run $runID (already processed)")
                continue
            }
            evalTasks.add(
                    PatchCompositionTask(
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

    /** Execute the study. */
    fun run() {
        val threadPool = Executors.newFixedThreadPool(numThreads)
        Logger.info("Scheduling ${evalTasks.size} tasks...")

        val futures =
                evalTasks
                        .stream()
                        .map { runnable: PatchCompositionTask ->
                            FutureAndEvalRun(threadPool.submit(runnable), runnable.evalRun)
                        }
                        .collect(Collectors.toList())

        Logger.info("Scheduled all tasks.")

        waitForShutdown(threadPool, futures, config)

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
                                    .execute(RmCommand(operations.workDir).recursive().force())
                                    .isSuccess
                    ) {
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
    val datasetsPerLanguage: Map<String, MutableList<CherryDataset>> =
            try {
                loadPRDatasets(config)
            } catch (e: IOException) {
                Logger.error(
                        "Was not able to load the yaml datasets from '" +
                                config.EXPERIMENT_DATASETS() +
                                "'"
                )
                throw UncheckedIOException(e)
            }

    val seed: ByteArray =
            ByteBuffer.allocate(java.lang.Long.BYTES)
                    .putLong(config.EXPERIMENT_REPEATS_START() + config.SEED())
                    .array()
    val idProvider = IDProvider(config.EXPERIMENT_START_ID())
    var id = 0uL
    val rand = SecureRandom(seed)
    val allSamples = createOrLoadSamples(config, datasetsPerLanguage, rand)

    cloneDatasets(allSamples, config)

    val n = 5
    Logger.info("Processing $n repos in parallel")
    val threadPool = Executors.newFixedThreadPool(n)

    val completedRunsAll = loadCompletedRuns(config)

    val numCherryPicks = countCherryPicks(allSamples[0])

    val completedRuns = completedRunsAll.getOrDefault(0, HashMap())
    var completed = 0
    Logger.info("Already considered ${completedRuns.size} repos.")
    completedRuns.forEach { s -> completed += s.value.size }
    Logger.info("Processed a total of $completed evaluation runs.\n")
    Thread.sleep(5000)

    Logger.info("Considering a total of $numCherryPicks cherry picks")
    for (dataset in allSamples[0]) {
        while (idProvider.next() < id) {}
        id += dataset.cherryPicks.size.toUInt()

        if (completedRuns.contains(dataset.datasetName) &&
                        completedRuns[dataset.datasetName]!!.size == dataset.cherryPicks.size
        ) {
            // Skip this dataset, it was already processed
            Logger.info("Skipping evaluation of cherry picks from ${dataset.datasetName}")
            printProgress(completed, numCherryPicks, 0, 0uL)
            continue
        }
        threadPool.submit {
            val i = id
            Logger.info("Preparing evaluation of cherry picks from ${dataset.datasetName}")
            val study =
                    PatchCompositionAnalysis(
                            config,
                            dataset,
                            idProvider,
                            completedRuns.getOrDefault(dataset.datasetName, HashSet())
                    )
            try {
                study.run()
            } catch (e: Exception) {
                e.printStackTrace()
                Logger.error(e)
            }
            completed += dataset.cherryPicks.size
            printProgress(completed, numCherryPicks, 0, i)
        }
    }
    threadPool.awaitTermination(10, TimeUnit.DAYS)
    threadPool.shutdown()

    exitProcess(0)
}
