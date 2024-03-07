package org.variantsync.evaluation

import org.tinylog.kotlin.Logger
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

fun waitForShutdown(
    threadPool: ExecutorService,
    futures: MutableList<Future<*>>
) {
    threadPool.shutdown()
    for (future in futures) {
        try {
            future.get()
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