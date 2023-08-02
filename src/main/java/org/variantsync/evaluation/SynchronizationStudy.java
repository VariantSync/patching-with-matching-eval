package org.variantsync.evaluation;

import org.variantsync.functjonal.iteration.ClusteredIterator;
import org.variantsync.vevos.simulation.io.Resources;
import org.variantsync.vevos.simulation.io.data.VariabilityDatasetLoader;
import org.tinylog.Logger;
import org.variantsync.vevos.simulation.variability.SPLCommit;
import org.variantsync.vevos.simulation.variability.VariabilityDataset;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * This class contains the core workflow of our study as described in our paper.
 */
public class SynchronizationStudy {
    // Path to the ground truth dataset
    protected final Path groundTruthPath;
    // The study tasks that are to be executed in parallel
    final List<Task> tasks;
    final int numThreads;

    /**
     * Initialize the study from the given configuration
     */
    public SynchronizationStudy(String datasetName, Path mainDir, Path resultsDir,
                    Path repositoryPath, Path groundTruthPath, int numRepetitions, int numVariants,
                    int startID, boolean inDebug, int numThreads) {
        final Path resultFile = resultsDir.resolve("%s.results".formatted(datasetName));
        this.groundTruthPath = groundTruthPath;
        this.numThreads = numThreads;

        final List<SPLCommit> history = init();
        this.tasks = new ArrayList<>();
        int clusterSize = (int) Math.ceil((double) history.size() / numThreads);
        final ClusteredIterator<SPLCommit> commitClusterIterator =
                        new ClusteredIterator<>(history.iterator(), clusterSize);
        while (commitClusterIterator.hasNext()) {
            var commits = commitClusterIterator.next();
            tasks.add(new Task(datasetName, mainDir, resultsDir, repositoryPath, resultFile,
                            commits, numRepetitions, numVariants, inDebug, startID));
        }
    }

    /**
     * Execute the study.
     */
    public void run() {
        try {
            final ExecutorService threadPool = Executors.newFixedThreadPool(this.numThreads);
            List<Future<?>> futures = this.tasks.stream()
                    .map(threadPool::submit)
                    .collect(Collectors.toList());
            threadPool.shutdown();
            for (Future<?> future : futures) {
                future.get();
            }
            if (!threadPool.awaitTermination(7, TimeUnit.DAYS)) {
                Logger.error("Thread pool timeout.");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        Logger.info("All done.");
    }


    // Initialize the study by loading the required data
    private List<SPLCommit> init() {
        // Load VariabilityDataset
        Logger.info("Loading variability dataset.");
        VariabilityDataset dataset = null;
        try {
            final Resources instance = Resources.Instance();
            final VariabilityDatasetLoader datasetLoader = new VariabilityDatasetLoader();
            instance.registerLoader(VariabilityDataset.class, datasetLoader);
            dataset = instance.load(VariabilityDataset.class, groundTruthPath);
            Logger.info("Dataset loaded.");
        } catch (final Resources.ResourceIOException e) {
            throw new RuntimeException("Was not able to load dataset.", e);
        }

        // Retrieve pairs/sequences of usable commits
        Logger.info("Retrieving commits");
        return Objects.requireNonNull(dataset).getSuccessCommits();
    }

}
