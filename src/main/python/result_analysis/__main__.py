from result_analysis.plots import boxplot_results_lang_per_proj
from result_analysis.plots import boxplot_results_lang_overall
from sys import argv


def main():
    minimum = int(argv[1]) if len(argv) > 1 else 25
    print("Number of minimum results per repo to consider: " + str(minimum))
    # print_results_per_language(min)
    results_dir = '/home/alex/data/cherry-picks/results/'
    repo_sample = '/home/alex/programming/patching-with-matching-eval/simulation-files/data/repo-sample.yaml'
    boxplot_results_lang_per_proj(
        results_dir, repo_sample, minimum, only_non_trivial=True)
    boxplot_results_lang_overall(
        results_dir, repo_sample, only_non_trivial=True)
    # boxplot_results_per_patcher(results_dir, repo_sample, min)


if __name__ == "__main__":
    main()
