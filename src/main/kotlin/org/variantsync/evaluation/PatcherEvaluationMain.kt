package org.variantsync.evaluation

import java.io.File
import java.io.IOException
import java.io.UncheckedIOException
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.system.exitProcess
import org.apache.commons.io.FileUtils
import org.tinylog.kotlin.Logger
import org.variantsync.evaluation.execution.*
import org.variantsync.evaluation.util.shell.RmCommand
import org.variantsync.evaluation.util.shell.ShellExecutor

class CherryPickStudy(
        val config: EvalConfig,
        val dataset: CherryDataset,
        val repetition: Int,
        val idProvider: IDProvider,
        val completedRuns: Set<EvaluationRun>,
) : Callable<CherryDataset> {

    fun initializeEvalTasks(evalSetup: EvalOperations): MutableList<CherryPickEvalTask> {
        val evalTasks: MutableList<CherryPickEvalTask> = ArrayList()

        for (cherryPick in dataset.cherryPicks) {
            val runID = idProvider.next()
            val run =
                    EvaluationRun(
                            repetition,
                            dataset.datasetName,
                            cherryPick.cherryCommit,
                            cherryPick.expectedResultCommit
                    )
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
                            evalSetup,
                            runID,
                            run,
                    )
            )
        }
        return evalTasks
    }

    /** Execute the study. */
    override fun call(): CherryDataset {
        var evalSetup: EvalOperations? = null
        try {
            if (!Files.exists(config.EXPERIMENT_DIR_RESULTS())) {
                Files.createDirectories(config.EXPERIMENT_DIR_RESULTS())
            }

            val repoPath: Path = cloneGitHubRepo(config, dataset.repositoryId)
            evalSetup = EvalOperations(config, repoPath)

            // Clean old variant files
            cleanVariantDirectories(evalSetup)
            // Copy the source and target variant to the respective variant directories
            prepareVariantDirectories(evalSetup, repoPath)

            val evalTasks = initializeEvalTasks(evalSetup)

            Logger.info(
                    "Beginning execution of ${evalTasks.size} evaluation tasks for " +
                            this.dataset.datasetName
            )

            var processed = 0uL
            for (task in evalTasks) {
                processed++
                if (processed % 25uL == 0uL) {
                    Logger.info(
                            String.format(
                                    "Running task %s of %s.",
                                    processed.toString(),
                                    evalTasks.size.toString(),
                            )
                    )
                }
                executeTask(config, task)
            }

            Logger.info(String.format("Finished %s tasks.", evalTasks.size.toString()))
        } catch (e: Exception) {
            Logger.debug { "Was not able to evaluate patchers on " + dataset.datasetName }
            Logger.debug(e)
        } finally {
            if (evalSetup != null) {
                clean(evalSetup)
            }
        }
        return dataset
    }
}

fun executeTask(config: EvalConfig, task: CherryPickEvalTask) {
    try {
        val taskOutcome = task.execute()
        val runID = taskOutcome.runID

        if (taskOutcome.result.isPresent) {
            for (result in taskOutcome.result.get()) {
                saveResult(result, runID)
            }
        }
    } catch (e: Throwable) {
        Logger.error("Failed to finish task!")
        Logger.error(e)
        e.printStackTrace()
    } finally {
        markEvalRun(task.evalRun, config.EXPERIMENT_PROCESSED_FILE())
    }
}

fun clean(evalSetup: EvalOperations) {
    Logger.debug("Running clean up.")
    try {
        FileUtils.deleteDirectory(evalSetup.workDir.toFile())
    } catch (e: Exception) {
        Logger.debug(e)
        if (Files.exists(evalSetup.workDir)) {
            Logger.debug("Trying to remove directory with 'rm -rf'")
            if (ShellExecutor(Logger::warn, Logger::warn, evalSetup.workDir)
                            .execute(RmCommand(evalSetup.workDir).recursive().force())
                            .isSuccess
            ) {
                Logger.debug("Success!")
            }
        }
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

    val n = config.EXPERIMENT_THREAD_COUNT()
    Logger.info("Processing $n repos in parallel")
    val threadPool = Executors.newFixedThreadPool(n)

    val completedRunsAll = loadCompletedRuns(config)

    for (repetition in config.EXPERIMENT_REPEATS_START()..config.EXPERIMENT_REPEATS_END()) {
        val repetitionIndex = repetition - config.EXPERIMENT_REPEATS_START()
        val numCherryPicks = countCherryPicks(allSamples[repetitionIndex])

        val completedRuns = completedRunsAll.getOrDefault(repetition, HashMap())
        var completed = 0
        Logger.info("Already considered ${completedRuns.size} repos.")
        completedRuns.forEach { s -> completed += s.value.size }
        Logger.info("Already processed a total of $completed evaluation runs.\n")
        Thread.sleep(5000)

        Logger.info(
                "Considering a total of $numCherryPicks cherry-picks for repetition $repetition"
        )
        val futures: MutableList<Future<CherryDataset>> = ArrayList()
        for (dataset in allSamples[repetitionIndex]) {
            id += dataset.cherryPicks.size.toUInt()
            if (completedRuns.contains(dataset.datasetName) &&
                            completedRuns[dataset.datasetName]!!.size == dataset.cherryPicks.size
            ) {
                // Skip this dataset, it was already processed
                Logger.info(
                        "Skipping evaluation of cherry picks from ${dataset.datasetName} (rep.: $repetition): Already processed."
                )
                printProgress(completed, numCherryPicks, repetition)
                continue
            }
            val study =
                    CherryPickStudy(
                            config,
                            dataset,
                            repetition,
                            idProvider,
                            completedRuns.getOrDefault(dataset.datasetName, HashSet())
                    )
            val future: Future<CherryDataset> = threadPool.submit(study)
            futures.add(future)
        }
        threadPool.shutdown()
        for (future in futures) {
            val dataset = future.get()
            completed += dataset.cherryPicks.size
            if (config.CLEAN_REPOSITORIES()) {
                val cloneDir =
                        config.EXPERIMENT_DIR_REPOS()
                                .resolve(dataset.repositoryId.replace("/", "_"))
                cloneDir.toFile().deleteRecursively()
            }
            printProgress(completed, numCherryPicks, repetition)
        }
        threadPool.awaitTermination(10, TimeUnit.DAYS)
    }

    exitProcess(0)
}
