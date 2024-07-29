package org.variantsync.evaluation

import org.apache.commons.configuration2.Configuration
import org.apache.commons.configuration2.PropertiesConfiguration
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder
import org.apache.commons.configuration2.builder.fluent.Parameters
import org.apache.commons.configuration2.convert.DefaultListDelimiterHandler
import org.apache.commons.configuration2.ex.ConfigurationException
import org.variantsync.evaluation.cherries.CherryType
import java.io.File
import java.nio.file.Path

/**
 * Determines the configuration of our study.
 */
class EvalConfig(propertiesFile: File) {
    // Configuration object holding key-value properties.
    private var config: Configuration? = null

    /**
     * Load a configuration from the given properties file.
     *
     */
    init {
        val params = Parameters()
        try {
            val builder = FileBasedConfigurationBuilder(
                PropertiesConfiguration::class.java
            )
                .configure(
                    params.properties().setFile(propertiesFile)
                        .setListDelimiterHandler(
                            DefaultListDelimiterHandler(',')
                        )
                )
            config = builder.configuration
        } catch (e: ConfigurationException) {
            System.err.println("Was not able to load properties file $propertiesFile")
            throw RuntimeException(e)
        }
    }

    /**
     * @return The start of repetitions for each commit pair and source-target combination
     */
    fun EXPERIMENT_REPEATS_START(): Int {
        return config!!.getInt(EXPERIMENT_REPEATS_START, 1)
    }

    /**
     * @return The end of repetitions for each commit pair and source-target combination
     */
    fun EXPERIMENT_REPEATS_END(): Int {
        return config!!.getInt(EXPERIMENT_REPEATS_END)
    }

    /**
     * @return The number of repetitions for each commit pair and source-target combination
     */
    fun EXPERIMENT_REPEATS_COUNT(): Int {
        return EXPERIMENT_REPEATS_END() - EXPERIMENT_REPEATS_START() + 1
    }

    /**
     * @return The number of variants that are to be generated
     */
    fun EXPERIMENT_VARIANT_COUNT(): Int {
        return config!!.getInt(EXPERIMENT_VARIANT_COUNT)
    }

    /**
     * @return The working directory
     */
    fun EXPERIMENT_DIR_MAIN(): Path {
        return Path.of(config!!.getString(EXPERIMENT_DIR_MAIN))
    }

    /**
     * @return The root directory of the ground truth
     */
    fun EXPERIMENT_DIR_GROUND_TRUTH(): Path {
        return Path.of(config!!.getString(EXPERIMENT_DIR_GROUND_TRUTH))
    }

    /**
     * @return The directory to which the repositories specified in the dataset file are cloned to
     */
    fun EXPERIMENT_DIR_REPOS(): Path {
        return Path.of(config!!.getString(EXPERIMENT_DIR_REPOS))
    }

    /**
     * @return The file with the list of datasets in Markdown format
     */
    fun EXPERIMENT_DATASETS(): Path {
        return Path.of(config!!.getString(EXPERIMENT_DATASETS))
    }

    /**
     * @return Whether additional debugging is enabled
     */
    fun EXPERIMENT_DEBUG(): Boolean {
        return config!!.getBoolean(EXPERIMENT_DEBUG)
    }

    /**
     * @return Each commit pair that is considered has its own id. All ids smaller than startid are
     * skipped when running the study. This property is required for the short installation
     * validation.
     */
    fun EXPERIMENT_START_ID(): ULong {
        return config!!.getLong(EXPERIMENT_STARTID, 0).toULong()
    }

    /**
     * @return The path to the file remembering processed runs
     */
    fun EXPERIMENT_PROCESSED_FILE(): Path {
        return Path.of(config!!.getString(EXPERIMENT_PROCESSED_FILE))
    }

    /**
     * @return The path to the results directory
     */
    fun EXPERIMENT_DIR_RESULTS(): Path {
        return Path.of(config!!.getString(EXPERIMENT_DIR_RESULTS))
    }

    /**
     *
     * @return Minimum number of cherries in a repository for a dataset to be considered for the
     * study. If a repository has fewer cherries, it is simply ignored. Values of 0 or less
     * are automatically converted to 0.
     */
    fun EXPERIMENT_DATASET_MIN_SIZE(): Int {
        var value = config!!.getInt(EXPERIMENT_DATASET_MIN_SIZE)
        if (value < 0) {
            value = 0
        }
        return value
    }

    /**
     *
     * @return Maximum number of cherries in a repository for a dataset to be considered for the
     * study. If a repository has more cherries, it is simply ignored. Values of 0 or less
     * are automatically converted to Integer.MAX_VALUE.
     */
    fun EXPERIMENT_DATASET_MAX_SIZE(): Int {
        var value = config!!.getInt(EXPERIMENT_DATASET_MAX_SIZE)
        if (value <= 0) {
            value = Int.MAX_VALUE
        }
        return value
    }

    /**
     * @return Number of threads to use for the parallel execution of the study. If the number is
     * unset or negative, the number of available processors is taken.
     */
    fun EXPERIMENT_THREAD_COUNT(): Int {
        var count = config!!.getInt(EXPERIMENT_THREAD_COUNT, 0)
        if (count < 1) {
            count = Runtime.getRuntime().availableProcessors()
        }
        return count
    }

    /**
     * @return Whether sampling of commits that are processed is enabled
     */
    fun EXPERIMENT_ENABLE_SAMPLING(): Boolean {
        return config!!.getBoolean(EXPERIMENT_ENABLE_SAMPLING)
    }

    fun EXPERIMENT_SAMPLE_FILE(): Path {
        return Path.of(config!!.getString(EXPERIMENT_SAMPLE_FILE, "cherry-sample.ser"))
    }

    fun sampleZ(): Double {
        // 1.96 is the z score for 95% confidence
        return config!!.getDouble(SAMPLING_Z, 1.96)
    }

    fun sampleE(): Double {
        // 0.05 is the error margin for 95% confidence
        return config!!.getDouble(SAMPLING_E, 0.05)
    }

    fun sampleP(): Double {
        // 0.5 is the default proportion if there is no knowledge about the population
        return config!!.getDouble(SAMPLING_P, 0.5)
    }

    fun SEED(): Long {
        return config!!.getLong(SAMPLING_SEED, 42)
    }

    fun EXPERIMENT_CHERRY_TYPE(): CherryType {
        return config!!.getEnum(EXPERIMENT_CHERRY_TYPE, CherryType::class.java)
    }

    companion object {
        // The first id of repetitions for each commit and source target combination
        private const val EXPERIMENT_REPEATS_START = "experiment.repeats.start"

        // The last id of repetitions for each commit and source target combination
        private const val EXPERIMENT_REPEATS_END = "experiment.repeats.end"

        // The number of generated variants
        private const val EXPERIMENT_VARIANT_COUNT = "experiment.variant.count"

        // The working directory
        private const val EXPERIMENT_DIR_MAIN = "experiment.dir.main"

        // The directory containing the ground truth
        private const val EXPERIMENT_DIR_GROUND_TRUTH = "experiment.dir.ground-truths"

        // The directory to which the repositories are cloned to
        private const val EXPERIMENT_DIR_REPOS = "experiment.dir.repos"

        // The file containing the list of datasets
        private const val EXPERIMENT_DATASETS = "experiment.datasets"

        private const val EXPERIMENT_CHERRY_TYPE = "experiment.cherry-type"

        // Enable saving of certain files (e.g., feature list, presence conditions, configurations) for
        // additional debugging
        private const val EXPERIMENT_DEBUG = "experiment.debug"

        // Each commit pair that is considered has its own id. All ids < startid are skipped when
        // running the study. This
        // property is required for the short installation validation.
        private const val EXPERIMENT_STARTID = "experiment.startid"

        // The directory for saving the results
        private const val EXPERIMENT_DIR_RESULTS = "experiment.dir.results"

        // The directory for saving the results
        private const val EXPERIMENT_PROCESSED_FILE = "experiment.processed-file"

        // The minimum number of cherries in a dataset for it to be considered
        private const val EXPERIMENT_DATASET_MIN_SIZE = "experiment.dataset.min-size"

        // The maximum number of cherries in a dataset for it to be considered
        private const val EXPERIMENT_DATASET_MAX_SIZE = "experiment.dataset.max-size"

        // The number of threads for parallel execution
        private const val EXPERIMENT_THREAD_COUNT = "experiment.thread-count"

        // Should sampling be enabled to reduce the amount of data to process?
        private const val EXPERIMENT_ENABLE_SAMPLING = "experiment.enable-sampling"

        // Should sampling be enabled to reduce the amount of data to process?
        private const val EXPERIMENT_SAMPLE_FILE = "experiment.sample-file"

        // The z score for sample size computation
        private const val SAMPLING_Z = "sampling.z"

        // The error margin for sample size computation
        private const val SAMPLING_E = "sampling.e"

        // The sample proportion for sample size computation
        private const val SAMPLING_P = "sampling.p"

        // The seed for the SecureRandom that chooses a random subset
        private const val SAMPLING_SEED = "sampling.seed"
    }
}
