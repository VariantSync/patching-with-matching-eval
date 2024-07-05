import glob
import sys
import os

import parse
import serialization
import table

if __name__ == "__main__":
    if len(sys.argv) < 2:
        result_dir = "src/main/resources/plot_testdata"
        output_base = "src/main/resources/plot_testdata/plots"
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
        output_dir = output_base + "/" + file_name
        os.makedirs(output_dir, exist_ok=True)

        cached_file = result_dir + "/" + file_name + ".cache"
        subjects.append(file_name)

        if os.path.exists(cached_file):
            print("Loading chache", cached_file)
            experiment = serialization.deserialize(cached_file)
        else:
            print("No chache found at", cached_file)
            print("Opening", result_file)
            experiment = parse.parse_file_at(result_file)
            serialization.serialize(experiment, cached_file)

        experiments.append(experiment)

        print()
        print("Parsed Values:")
        print("commitPatches =", experiment.normal.commit_patches)
        print("normal =", vars(experiment.normal))
        print("filtered =", vars(experiment.filtered))
        print()

        # plot.rq1(experiment.normal, outputDirectory)
        # plot.rq2(experiment.normal, colourscheme, outputDirectory)
        # plot.rq3(experiment, colourscheme, outputDirectory)
        # plot.rq4(experiment, colourscheme, outputDirectory)
    table.rq1_table(subjects, experiments, None)

    print("Done")
