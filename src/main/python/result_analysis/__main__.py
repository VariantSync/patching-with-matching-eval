from result_analysis.tables import (
    find_example,
    metrics_table_generation,
    patch_sizes,
    venn_diagram,
)
from result_analysis.analyze_results import find_outliers

results_dir = "../../../evaluation-workdir/results/"
repo_sample = "../../../evaluation-workdir/data/repo-sample.yaml"
# metrics_file = "../../../evaluation-workdir/tables/metrics.tex"
metrics_file = (
    "/home/alex/papers/self/finished/patching-with-matching/paper/tables/metrics.tex"
)


def main():
    metrics_table_generation(
        results_dir,
        repo_sample,
        only_non_trivial=False,
        file_metrics=metrics_file,
        file_power="",
    )


def example():
    find_example(results_dir + "rep-1/", repo_sample, False)


def outliers():
    find_outliers(results_dir + "rep-1/", repo_sample, False)


def compare():
    venn_diagram(results_dir + "rep-1/", repo_sample, False)


def sizes():
    patch_sizes(results_dir + "rep-1/", repo_sample)


if __name__ == "__main__":
    main()
