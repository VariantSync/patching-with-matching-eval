package org.variantsync.evaluation

import org.apache.commons.configuration2.Configuration
import org.apache.commons.configuration2.PropertiesConfiguration
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder
import org.apache.commons.configuration2.builder.fluent.Parameters
import org.apache.commons.configuration2.convert.DefaultListDelimiterHandler
import org.apache.commons.configuration2.ex.ConfigurationException
import java.io.File

/**
 * Determines the configuration of our study.
 */
class StudyConfiguration(propertiesFile: File) {
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
     * @return The number of repetitions for each commit pair and source-target combination
     */
    fun EXPERIMENT_REPEATS(): Int {
        return config!!.getInt(EXPERIMENT_REPEATS)
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
    fun EXPERIMENT_DIR_MAIN(): String {
        return config!!.getString(EXPERIMENT_DIR_MAIN)
    }

    /**
     * @return The root directory of the ground truth
     */
    fun EXPERIMENT_DIR_GROUND_TRUTH(): String {
        return config!!.getString(EXPERIMENT_DIR_GROUND_TRUTH)
    }

    /**
     * @return The file with the list of datasets in Markdown format
     */
    fun EXPERIMENT_DATASETS(): String {
        return config!!.getString(EXPERIMENT_DATASETS)
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
    fun EXPERIMENT_START_ID(): Int {
        return config!!.getInt(EXPERIMENT_STARTID, 0)
    }

    /**
     * @return The path to the results directory
     */
    fun EXPERIMENT_DIR_RESULTS(): String {
        return config!!.getString(EXPERIMENT_DIR_RESULTS)
    }

    /**
     *
     * @return Maximum number of commits in a repository for a dataset to be considered for the
     * study. If a repository has more commits, it is simply ignored. Values of 0 or less
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

    companion object {
        // The number of repetitions for each commit and source target combination
        private const val EXPERIMENT_REPEATS = "experiment.repeats"

        // The number of generated variants
        private const val EXPERIMENT_VARIANT_COUNT = "experiment.variant.count"

        // The working directory
        private const val EXPERIMENT_DIR_MAIN = "experiment.dir.main"

        // The directory containing the ground truth
        private const val EXPERIMENT_DIR_GROUND_TRUTH = "experiment.dir.ground-truths"

        // The file containing the list of datasets
        private const val EXPERIMENT_DATASETS = "experiment.datasets"

        // Enable saving of certain files (e.g., feature list, presence conditions, configurations) for
        // additional debugging
        private const val EXPERIMENT_DEBUG = "experiment.debug"

        // Each commit pair that is considered has its own id. All ids < startid are skipped when
        // running the study. This
        // property is required for the short installation validation.
        private const val EXPERIMENT_STARTID = "experiment.startid"

        // The directory for saving the results
        private const val EXPERIMENT_DIR_RESULTS = "experiment.dir.results"

        // The maximum number of commits in a dataset for it to be considered
        private const val EXPERIMENT_DATASET_MAX_SIZE = "experiment.dataset.max-size"

        // The number of threads for parallel execution
        private const val EXPERIMENT_THREAD_COUNT = "experiment.thread-count"
    }
}
