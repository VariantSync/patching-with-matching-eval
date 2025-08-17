package org.variantsync.evaluation.execution

import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import org.tinylog.kotlin.Logger
import org.variantsync.evaluation.analysis.TaskOutcome

class FutureAndEvalRun(val future: Future<TaskOutcome>, val evaluationRun: EvaluationRun)

fun waitForShutdown(
        threadPool: ExecutorService,
        futuresAndRuns: MutableList<FutureAndEvalRun>,
        config: EvalConfig,
) {
    var processed = 0uL
    for (fAndE in futuresAndRuns) {
        val future = fAndE.future
        processed++
        val runID: ULong
        val taskOutCome: TaskOutcome
        try {
            taskOutCome = future.get()
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
                }
            }
            markEvalRun(taskOutCome.evalRun, config.EXPERIMENT_PROCESSED_FILE())
        } catch (e: Throwable) {
            Logger.error("Failed to finish task!")
            Logger.error(e)
            e.printStackTrace()
            markEvalRun(fAndE.evaluationRun, config.EXPERIMENT_PROCESSED_FILE())
        }
    }
    Logger.info("Waiting for thread pool shutdown")
    threadPool.shutdownNow()

    Logger.info(String.format("Finished %s tasks.", futuresAndRuns.size.toString()))
}
