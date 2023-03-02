package org.variantsync.evaluation.experiment;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.convert.DefaultListDelimiterHandler;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.variantsync.vevos.simulation.util.LogLevel;

import java.io.File;
import java.nio.file.Path;

/**
 * Determines the configuration of our study.
 */
public class StudyConfiguration {
    // The name of the experimental subject
    private static final String EXPERIMENT_SUBJECT = "experiment.subject";
    // The number of repetitions for each commit and source target combination
    private static final String EXPERIMENT_REPEATS = "experiment.repeats";
    // The number of generated variants
    private static final String EXPERIMENT_VARIANT_COUNT = "experiment.variant.count";
    // The working directory
    private static final String EXPERIMENT_DIR_MAIN = "experiment.dir.main";
    // The directory containing the ground truth
    private static final String EXPERIMENT_DIR_DATASET = "experiment.dir.dataset";
    // The directory containing the SPL (i.e., BusyBox)
    private static final String EXPERIMENT_DIR_SPL = "experiment.dir.spl";
    // Enable saving of certain files (e.g., feature list, presence conditions, configurations) for additional debugging
    private static final String EXPERIMENT_DEBUG = "experiment.debug";
    // Log level
    private static final String EXPERIMENT_LOGGER_LEVEL = "experiment.logger.level";
    // Each commit pair that is considered has its own id. All ids < startid are skipped when running the study. This
    // property is required for the short installation validation.
    private static final String EXPERIMENT_STARTID = "experiment.startid";
    // The directory for saving the results
    private static final String EXPERIMENT_DIR_RESULTS = "experiment.dir.results";
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
            final var builder =
                    new FileBasedConfigurationBuilder<>(PropertiesConfiguration.class)
                            .configure(params.properties()
                                    .setFile(propertiesFile)
                                    .setListDelimiterHandler(new DefaultListDelimiterHandler(',')));
            this.config = builder.getConfiguration();
        } catch (ConfigurationException e) {
            System.err.println("Was not able to load properties file " + propertiesFile);
            throw new RuntimeException(e);
        }
    }

    /**
     * @return The name of the experimental subject
     */
    public String EXPERIMENT_SUBJECT() {
        return config.getString(EXPERIMENT_SUBJECT);
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
     * @return The root directory of the ground truth dataset
     */
    public String EXPERIMENT_DIR_DATASET() {
        return config.getString(EXPERIMENT_DIR_DATASET);
    }

    /**
     * @return The root directory of the considered SPL
     */
    public String EXPERIMENT_DIR_SPL() {
        return config.getString(EXPERIMENT_DIR_SPL);
    }

    /**
     * @return Whether additional debugging is enabled
     */
    public Boolean EXPERIMENT_DEBUG() {
        return config.getBoolean(EXPERIMENT_DEBUG);
    }

    /**
     * @return The log level
     */
    public LogLevel EXPERIMENT_LOGGER_LEVEL() {
        return LogLevel.valueOf(config.getString(EXPERIMENT_LOGGER_LEVEL));
    }

    /**
     * @return Each commit pair that is considered has its own id. All ids smaller than startid are skipped when running the study.
     * This property is required for the short installation validation.
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
}
