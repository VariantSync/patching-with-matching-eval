package org.variantsync.evaluation;

import org.eclipse.jgit.api.Git;
import org.variantsync.diffdetective.datasets.DatasetDescription;
import org.variantsync.diffdetective.load.GitLoader;
import org.variantsync.vevos.simulation.util.Logger;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.variantsync.vevos.simulation.VEVOS.Initialize;

/**
 * Entry point for running our study. Loads the configuration and starts the study.
 */
public class StudyRunner {
    public static void main(final String... args) {
        if (args.length < 1) {
            System.err.println("The first argument should provide the path to the configuration file that is to be used");
        }
        // Initialize the VEVOS Simulation library
        Initialize();

        final StudyConfiguration config = new StudyConfiguration(new File(args[0]));

        Logger.status("Starting experiment initialization.");
        Logger.setLogLevel(config.EXPERIMENT_LOGGER_LEVEL());

        final Path mainDir = Path.of(config.EXPERIMENT_DIR_MAIN());
        var resultsDir = Path.of(config.EXPERIMENT_DIR_RESULTS());
        var inDebug = config.EXPERIMENT_DEBUG();
        var groundTruthPath = Path.of(config.EXPERIMENT_DIR_GROUND_TRUTH());
        var numRepetitions = config.EXPERIMENT_REPEATS();
        var numVariants = config.EXPERIMENT_VARIANT_COUNT();
        var startID = config.EXPERIMENT_START_ID();

        List<DatasetDescription> datasets;
        try {
            datasets = DatasetDescription.fromMarkdown(Path.of(config.EXPERIMENT_DATASETS()));
        } catch (IOException e) {
            Logger.error("Was not able to load markdown file with the datasets from '" + config.EXPERIMENT_DATASETS() + "'");
            throw new UncheckedIOException(e);
        }

        var datasetMaxSize = config.EXPERIMENT_DATASET_MAX_SIZE();
        for (DatasetDescription dataset : datasets) {
            var datasetSize = Integer.parseInt(dataset.commits().replaceAll(",", ""));
            if (datasetSize > datasetMaxSize) {
                Logger.status("Skipping %s with %s commits because it exceeds the maximum number of commits (%d) set in the configuration.".formatted(dataset.name(), dataset.commits(), datasetMaxSize));
                continue;
            }
            var repoDir = mainDir.resolve(dataset.name());
            var repoGroundTruth = groundTruthPath.resolve(dataset.name());

            if (!Files.exists(repoGroundTruth)) {
                Logger.warning("Found no ground truth for %s. Skipping the study for %s".formatted(dataset.name(), dataset.name()));
                continue;
            }

            // Clone the repository if required
            try (Git ignored = GitLoader.fromRemote(repoDir, URI.create(dataset.repoURL()))) {
                Logger.status("Cloned %s into %s".formatted(dataset.name(), repoDir));
            }

            final SynchronizationStudy synchronizationStudy = new SynchronizationStudy(dataset.name(), mainDir, resultsDir,
                    repoDir, repoGroundTruth, numRepetitions, numVariants, startID, inDebug);

            try {
                synchronizationStudy.run();
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(1);
            }
        }

        System.exit(0);
    }
}