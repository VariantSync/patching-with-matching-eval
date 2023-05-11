package org.variantsync.evaluation;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.convert.DefaultListDelimiterHandler;
import org.apache.commons.configuration2.ex.ConfigurationException;

import java.io.File;

/**
 * Determines the configuration of our study.
 */
public class StudyConfiguration {
    // The number of repetitions for each commit and source target combination
    private static final String EXPERIMENT_REPEATS = "experiment.repeats";
    // The number of generated variants
    private static final String EXPERIMENT_VARIANT_COUNT = "experiment.variant.count";
    // The working directory
    private static final String EXPERIMENT_DIR_MAIN = "experiment.dir.main";
    // The directory containing the ground truth
    private static final String EXPERIMENT_DIR_GROUND_TRUTH = "experiment.dir.ground-truths";
    // The file containing the list of datasets
    private static final String EXPERIMENT_DATASETS = "experiment.datasets";
    // Enable saving of certain files (e.g., feature list, presence conditions, configurations) for
    // additional debugging
    private static final String EXPERIMENT_DEBUG = "experiment.debug";
    // Each commit pair that is considered has its own id. All ids < startid are skipped when
    // running the study. This
    // property is required for the short installation validation.
    private static final String EXPERIMENT_STARTID = "experiment.startid";
    // The directory for saving the results
    private static final String EXPERIMENT_DIR_RESULTS = "experiment.dir.results";
    // The maximum number of commits in a dataset for it to be considered
    private static final String EXPERIMENT_DATASET_MAX_SIZE = "experiment.dataset.max-size";
    // The number of threads for parallel execution
    private static final String EXPERIMENT_THREAD_COUNT = "experiment.thread-count";
    // Configuration object holding key-value properties.
    private final Configuration config;

    /**
     * Load a configuration from the given properties file.
     *
     * @param propertiesFile The properties file
     */
    public StudyConfiguration(final File propertiesFile) {
        final Parameters params = new Parameters();
        try {
            final var builder = new FileBasedConfigurationBuilder<>(PropertiesConfiguration.class)
                            .configure(params.properties().setFile(propertiesFile)
                                            .setListDelimiterHandler(
                                                            new DefaultListDelimiterHandler(',')));
            this.config = builder.getConfiguration();
        } catch (ConfigurationException e) {
            System.err.println("Was not able to load properties file " + propertiesFile);
            throw new RuntimeException(e);
        }
    }

    /**
     * @return The number of repetitions for each commit pair and source-target combination
     */
    public int EXPERIMENT_REPEATS() {
        return config.getInt(EXPERIMENT_REPEATS);
    }

    /**
     * @return The number of variants that are to be generated
     */
    public int EXPERIMENT_VARIANT_COUNT() {
        return config.getInt(EXPERIMENT_VARIANT_COUNT);
    }

    /**
     * @return The working directory
     */
    public String EXPERIMENT_DIR_MAIN() {
        return config.getString(EXPERIMENT_DIR_MAIN);
    }

    /**
     * @return The root directory of the ground truth
     */
    public String EXPERIMENT_DIR_GROUND_TRUTH() {
        return config.getString(EXPERIMENT_DIR_GROUND_TRUTH);
    }

    /**
     * @return The file with the list of datasets in Markdown format
     */
    public String EXPERIMENT_DATASETS() {
        return config.getString(EXPERIMENT_DATASETS);
    }

    /**
     * @return Whether additional debugging is enabled
     */
    public Boolean EXPERIMENT_DEBUG() {
        return config.getBoolean(EXPERIMENT_DEBUG);
    }

    /**
     * @return Each commit pair that is considered has its own id. All ids smaller than startid are
     *         skipped when running the study. This property is required for the short installation
     *         validation.
     */
    public int EXPERIMENT_START_ID() {
        return config.getInt(EXPERIMENT_STARTID, 0);
    }

    /**
     * @return The path to the results directory
     */
    public String EXPERIMENT_DIR_RESULTS() {
        return config.getString(EXPERIMENT_DIR_RESULTS);
    }

    /**
     *
     * @return Maximum number of commits in a repository for a dataset to be considered for the
     *         study. If a repository has more commits, it is simply ignored. Values of 0 or less
     *         are automatically converted to Integer.MAX_VALUE.
     */
    public int EXPERIMENT_DATASET_MAX_SIZE() {
        var value = config.getInt(EXPERIMENT_DATASET_MAX_SIZE);
        if (value <= 0) {
            value = Integer.MAX_VALUE;
        }
        return value;
    }


    /**
     * @return Number of threads to use for the parallel execution of the study. If the number is
     *         unset or negative, the number of available processors is taken.
     */
    public int EXPERIMENT_THREAD_COUNT() {
        int count = config.getInt(EXPERIMENT_THREAD_COUNT, 0);
        if (count < 1) {
            count = Runtime.getRuntime().availableProcessors();
        }
        return count;
    }
}
