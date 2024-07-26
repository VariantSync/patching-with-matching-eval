from result_analysis.latex import generate_metrics_result_table
from result_analysis.plots import (
    automation_boxplot_results_lang_per_proj,
    boxplot_results_lang_per_proj,
    ed_runtime_boxplot_results_lang_overall,
)
from result_analysis.plots import boxplot_results_lang_overall
from sys import argv

from result_analysis.tables import better_or_worse, rq3_table_generation


def main():
    minimum = int(argv[1]) if len(argv) > 1 else 10
    print("Number of minimum results per repo to consider: " + str(minimum))
    # print_results_per_language(min)
    results_dir = "/home/alex/data/cherry-picks/results/"
    repo_sample = "/home/alex/programming/patching-with-matching-eval/simulation-files/data/repo-sample.yaml"
    metrics_file = (
        "/home/alex/papers/self/patching-with-matching/paper/tables/metrics.tex"
    )
    power_file = "/home/alex/papers/self/patching-with-matching/paper/tables/power.tex"
    rq3_table_generation(
        results_dir,
        repo_sample,
        only_non_trivial=False,
        file_metrics=metrics_file,
        file_power=power_file,
    )
    better_or_worse(results_dir + "rep-1/", repo_sample, False)


if __name__ == "__main__":
    main()
