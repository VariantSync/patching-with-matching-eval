package org.variantsync.evaluation

import org.tinylog.kotlin.Logger
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

fun waitForShutdown(
    threadPool: ExecutorService,
    futures: MutableList<Future<ULong>>
) {
    threadPool.shutdown()
    for (future in futures) {
        try {
            // TODO: Handle premature return
            // TODO: Handle timeouts
            val runID: ULong = future.get()
            if (runID % 25uL == 0uL) {
                Logger.info(
                    String.format(
                        "Finished cherry-pick %s of %s.%n",
                        runID.toString(),
                        futures.size.toString()
                    )
                )
            }
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