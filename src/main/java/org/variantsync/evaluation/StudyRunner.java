package org.variantsync.evaluation;

import org.variantsync.evaluation.experiment.StudyConfiguration;
import org.variantsync.evaluation.experiment.SynchronizationStudy;

import java.io.File;

/**
 * Entry point for running our study. Loads the configuration and starts the study.
 */
public class StudyRunner {
    public static void main(final String... args) {
        if (args.length < 1) {
            System.err.println("The first argument should provide the path to the configuration file that is to be used");
        }
        final StudyConfiguration config = new StudyConfiguration(new File(args[0]));

        final SynchronizationStudy synchronizationStudy = new SynchronizationStudy(config);

        try {
            synchronizationStudy.run();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        } finally {
            System.exit(0);
        }
    }
}