package org.variantsync.evaluation.execution

import org.tinylog.kotlin.Logger
import org.variantsync.evaluation.analysis.TaskOutcome
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class FutureAndEvalRun (val future: Future<TaskOutcome>, val evaluationRun: EvaluationRun)

fun waitForShutdown(
    threadPool: ExecutorService,
    futuresAndRuns: MutableList<FutureAndEvalRun>,
    config: EvalConfig,
): Boolean {
    val timeoutLength = 10L
    val timeoutUnit = TimeUnit.MINUTES
    val allowedTimeouts = 3
    var timouts = 0
    var processed = 0uL
    var hasTimeout = false
    threadPool.shutdown()
    for (fAndE in futuresAndRuns) {
        val future = fAndE.future
        processed++
        val runID: ULong
        val taskOutCome: TaskOutcome
        try {
            // TODO: Make timeout configurable
            taskOutCome = future.get(timeoutLength, timeoutUnit)
            runID = taskOutCome.runID
            if (processed == 1uL || processed % 25uL == 0uL) {
                Logger.info(
                    String.format(
                        "Running task %s of %s with ID %s.",
                        processed.toString(),
                        futuresAndRuns.size.toString(),
                        runID,
                    )
                )
            }

            if (taskOutCome.result.isPresent) {
                for (result in taskOutCome.result.get()) {
                    saveResult(result, runID)
                    timouts=0
                }
            }
            markEvalRun(taskOutCome.evalRun, config.EXPERIMENT_PROCESSED_FILE())
        } catch (e: TimeoutException) {
            Logger.warn("Timed out while running task. Skipping this task")
            future.cancel(true)
            markEvalRun(fAndE.evaluationRun, config.EXPERIMENT_PROCESSED_FILE())
            timouts++
            if (timouts > allowedTimeouts) {
                // if there are too many timeouts for a repository in a row, we cancel the evaluation for this repository
                Logger.warn("Stopping all tasks for subject repository - too many timeouts!")
                hasTimeout = true
                break
            }
        } catch (e: Throwable) {
            Logger.error("Failed to finish task!")
            Logger.error(e)
            e.printStackTrace()
            markEvalRun(fAndE.evaluationRun, config.EXPERIMENT_PROCESSED_FILE())
        }
    }
    Logger.info("Waiting for thread pool shutdown")
    threadPool.shutdownNow()
    if (!threadPool.awaitTermination(timeoutLength, timeoutUnit)) {
        Logger.error("Thread pool timeout.")
        hasTimeout = true
    }

    Logger.info(
        String.format(
            "Finished %s tasks.",
            futuresAndRuns.size.toString()
        )
    )
    return hasTimeout
}