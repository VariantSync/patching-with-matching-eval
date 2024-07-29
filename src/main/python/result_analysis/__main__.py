from result_analysis.latex import generate_metrics_result_table
from result_analysis.plots import (
    automation_boxplot_results_lang_per_proj,
    boxplot_results_lang_per_proj,
    ed_runtime_boxplot_results_lang_overall,
)
from result_analysis.plots import boxplot_results_lang_overall
from sys import argv

from result_analysis.tables import better_or_worse, find_example, rq3_table_generation

results_dir = "/home/alex/data/cherry-picks/results/"
repo_sample = "/home/alex/programming/patching-with-matching-eval/simulation-files/data/repo-sample.yaml"
metrics_file = "/home/alex/papers/self/patching-with-matching/paper/tables/metrics.tex"


def main():
    rq3_table_generation(
        results_dir,
        repo_sample,
        only_non_trivial=False,
        file_metrics=metrics_file,
        file_power="",
    )
    better_or_worse(results_dir + "rep-1/", repo_sample, False)


def example():
    find_example(results_dir + "rep-1/", repo_sample, False)


if __name__ == "__main__":
    main()
