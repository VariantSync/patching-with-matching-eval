import glob
import sys
import os

import parse
import plot
import serialization
import colours
import table

if __name__ == "__main__":
    if len(sys.argv) < 2:
        result_dir = "testdata"
        output_base = "testdata/plots"
    else:
        result_dir = sys.argv[1]
        output_base = sys.argv[2]

    # Get all .results files in the result directory
    results_files = glob.glob(result_dir + '/*.results')

    # Print each file name without the .results extension
    subjects = []
    experiments = []
    for result_file in results_files:
        # Get the final component of the path
        base_name = os.path.basename(result_file)
        # Split the base name into name and extension
        file_name = os.path.splitext(base_name)[0]
        outputDirectory = output_base + "/" + file_name
        os.makedirs(outputDirectory, exist_ok=True)

        cachedFile = result_dir + "/" + file_name + ".cache"
        subjects.append(file_name)

        colourscheme = colours.CSCHEME1

        if os.path.exists(cachedFile):
            print("Loading chache", cachedFile)
            experiment = serialization.deserialize(cachedFile)
        else:
            print("No chache found at", cachedFile)
            print("Opening", result_file)
            experiment = parse.parseFileAt(result_file)
            serialization.serialize(experiment, cachedFile)

        experiments.append(experiment)

        print()
        print("Parsed Values:")
        print("commitPatches =", experiment.normal.commitPatches)
        print("normal =", vars(experiment.normal))
        print("filtered =", vars(experiment.filtered))
        print()

        # plot.rq1(experiment.normal, outputDirectory)
        # plot.rq2(experiment.normal, colourscheme, outputDirectory)
        # plot.rq3(experiment, colourscheme, outputDirectory)
        # plot.rq4(experiment, colourscheme, outputDirectory)
    table.rq1_table(subjects, experiments, None)

    print("Done")
