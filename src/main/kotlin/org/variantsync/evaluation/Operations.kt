package org.variantsync.evaluation

import org.variantsync.evaluation.baseline.shell.AppliedPatchTracker
import org.variantsync.evaluation.baseline.shell.ShellExecutor
import java.nio.file.Path

abstract class Operations {
    abstract fun rejectsFile(): Path
    abstract fun rejectsFileFiltered(): Path
    abstract fun splitAndFilteredPatchFile(): Path
    abstract fun splitPatchFile(): Path
    abstract fun workDir(): Path
    abstract fun shell(): ShellExecutor
    abstract fun patchDir(): Path
    abstract fun appliedPatchTracker(): AppliedPatchTracker
    abstract fun filteredPatchFile(): Path
    abstract fun patchFile(): Path
    abstract fun sourceV0Path(name: String): Path

    // where:
    //    (n) is the sample size,
    //    (N) is the population size,
    //    (z) is the Z-score corresponding to your desired confidence level (for a 95% confidence level, (z = 1.96)),
    //    (p) is the sample proportion (in percent, such as 50% = 0.5),
    //    (e) is the margin of error (in percent, such as 5% = 0.05).
    fun determineSampleSize(config: EvalConfig, populationSize: Int): Int {
        val z = config.sampleZ()
        val p = config.sampleP()
        val e = config.sampleE()

        // \frac{ z^2 p(1-p) } {e^2}
        val upperFrac = ((z * z) * p * (1 - p)) / (e * e)
        // \frac{ z^2 p(1-p) } {e^2} + N - 1 }
        val lowerFrac = 1 + (((z * z) * p * (1 - p)) / ((e * e) * populationSize))

        return (upperFrac / lowerFrac).toInt() + 1;
    }

}