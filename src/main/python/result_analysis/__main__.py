from result_analysis.plots import (
    automation_boxplot_results_lang_per_proj,
    ed_runtime_boxplot_results_lang_overall,
    boxplot_results_lang_per_proj,
)
from result_analysis.plots import boxplot_results_lang_overall
from sys import argv

from result_analysis.tables import better_or_worse, rq3_table, rq3_table_alt


def main():
    minimum = int(argv[1]) if len(argv) > 1 else 25
    print("Number of minimum results per repo to consider: " + str(minimum))
    # print_results_per_language(min)
    results_dir = "/home/alex/data/cherry-picks/results/"
    repo_sample = "/home/alex/programming/patching-with-matching-eval/simulation-files/data/repo-sample.yaml"
    # boxplot_results_lang_per_proj(
    #     results_dir, repo_sample, minimum, only_non_trivial=True)
    # boxplot_results_lang_overall(
    #     results_dir, repo_sample, only_non_trivial=True)
    # boxplot_results_per_patcher(results_dir, repo_sample, min)
    # rq3_table(results_dir, only_non_trivial=True)
    # ed_runtime_boxplot_results_lang_overall(
    #    results_dir, repo_sample, only_non_trivial=True
    # )
    # automation_boxplot_results_lang_per_proj(
    #    results_dir, repo_sample, minimum, only_non_trivial=True
    # )

    better_or_worse(results_dir, repo_sample, only_non_trivial=True)


#    rq3_table_alt(results_dir, repo_sample, only_non_trivial=True)


if __name__ == "__main__":
    main()
