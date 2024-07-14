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
    threadPool.shutdown()
    var timouts = 0
    for (future in futures) {
        val runID: ULong
        val taskOutCome: TaskOutcome
        try {
            // TODO: Make timeout configurable
            taskOutCome = future.get(10, TimeUnit.MINUTES)
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
            if (timouts > 10) {
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
    // TODO: Configure timeout
    if (!threadPool.awaitTermination(10, TimeUnit.MINUTES)) {
        Logger.error("Thread pool timeout.")
    }

    Logger.info(
        String.format(
            "Finished all %s tasks.%n",
            futures.size.toString()
        )
    )

    Logger.info("All done.")
}