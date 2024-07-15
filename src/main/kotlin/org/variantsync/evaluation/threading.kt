package org.variantsync.evaluation

import org.tinylog.kotlin.Logger
import org.variantsync.evaluation.analysis.TaskOutcome
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

fun waitForShutdown(
    threadPool: ExecutorService,
    futures: MutableList<Future<TaskOutcome>>
) {
    val timeoutLength = 5L
    val timeoutUnit = TimeUnit.MINUTES
    val allowedTimeouts = 3
    var timouts = 0
    threadPool.shutdown()
    for (future in futures) {
        val runID: ULong
        val taskOutCome: TaskOutcome
        try {
            // TODO: Make timeout configurable
            taskOutCome = future.get(timeoutLength, timeoutUnit)
            runID = taskOutCome.runID
            if (runID % 25uL == 0uL) {
                Logger.info(
                    String.format(
                        "Running task %s of %s.",
                        runID.toString(),
                        futures.size.toString()
                    )
                )
            }

            if (taskOutCome.result.isPresent) {
                for (result in taskOutCome.result.get()) {
                    saveResult(result, runID)
                    timouts=0
                }
            }

        } catch (e: TimeoutException) {
            Logger.warn("Timed out while running task. Skipping this task")
            future.cancel(true)
            timouts++
            if (timouts > allowedTimeouts) {
                // if there are too many timeouts for a repository in a row, we cancel the evaluation for this repository
                Logger.warn("Stopping all tasks for subject repository - too many timeouts!")
                break
            }
        } catch (e: Throwable) {
            Logger.error("Failed to finish task!")
            Logger.error(e)
            e.printStackTrace()
        }
    }
    Logger.info("Waiting for thread pool shutdown")
    threadPool.shutdownNow()
    if (!threadPool.awaitTermination(timeoutLength, timeoutUnit)) {
        Logger.error("Thread pool timeout.")
    }

    Logger.info(
        String.format(
            "Finished %s tasks.",
            futures.size.toString()
        )
    )
}