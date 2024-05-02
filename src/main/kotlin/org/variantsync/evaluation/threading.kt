package org.variantsync.evaluation

import org.tinylog.kotlin.Logger
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

fun waitForShutdown(
    threadPool: ExecutorService,
    futures: MutableList<Future<ULong>>
) {
    threadPool.shutdown()
    for (future in futures) {
        val runID: ULong
        try {
            // TODO: Make timeout configurable
            runID = future.get(5, TimeUnit.MINUTES)
            if (runID % 25uL == 0uL) {
                Logger.info(
                    String.format(
                        "Finished cherry-pick %s of %s.%n",
                        runID.toString(),
                        futures.size.toString()
                    )
                )
            }
        } catch (e: TimeoutException) {
            Logger.warn("Timed out while simulating one cherry pick. Skipping this cherry pick in the evaluation")

            future.cancel(true)
        } catch (e: Throwable) {
            Logger.error("Failed to finish task!")
            Logger.error(e)
            e.printStackTrace()
        }
    }
    if (!threadPool.awaitTermination(7, TimeUnit.DAYS)) {
        Logger.error("Thread pool timeout.")
    }

    Logger.info(
        String.format(
            "Finished all %s cherry-picks.%n",
            futures.size.toString()
        )
    )

    Logger.info("All done.")
}