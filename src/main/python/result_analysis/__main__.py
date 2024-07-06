from result_analysis.misc_analyses import print_result_data
from result_analysis.misc_analyses import load_repos
from result_analysis.misc_analyses import print_results_per_repo
from result_analysis.misc_analyses import print_results_per_language
from result_analysis.plots import boxplot_results_per_patcher
from result_analysis.plots import boxplot_results_per_language
from sys import argv


def main():
    minimum = int(argv[1]) if len(argv) > 1 else 25
    print("Number of minimum results per repo to consider: " + str(minimum))
    # print_results_per_language(min)
    results_dir = '/home/alex/data/cherry-picks/results/'
    repo_sample = '/home/alex/programming/patching-with-matching-eval/simulation-files/data/repo-sample.yaml'
    boxplot_results_per_language(results_dir, repo_sample, minimum)
    # boxplot_results_per_patcher(results_dir, repo_sample, min)


if __name__ == "__main__":
    main()
