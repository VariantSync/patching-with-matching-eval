from result_analysis.io import load_repositories
from result_analysis.io import load_all_results
from result_analysis.tables import list_all_dirs
from result_analysis.result_handling import accumulate_data_per_patcher
from result_analysis.eval_setup import Metric, Patcher
from result_analysis.result_handling import (
    non_trivial_results,
    results_per_repo,
)

languages = [
    ("Python", "Python"),
    ("JavaScript", "\\multicolumn{1}{c}{JS}"),
    ("Go", "Go"),
    ("C++", "\\multicolumn{1}{c}{C++}"),
    ("Java", "Java"),
    ("TypeScript", "\\multicolumn{1}{c}{TS}"),
    ("C", "C"),
    ("C#", "C#"),
    ("PHP", "PHP"),
    ("Rust", "Rust"),
]


def find_outliers(path_to_results, path_to_repo_list, only_non_trivial):
    global languages
    repos = load_repositories(path_to_repo_list)

    results_per_patcher = {}
    for patcher in Patcher:  # Patcher is an enum
        results = load_all_results(path_to_results, patcher)
        # Filter trivial results
        if only_non_trivial:
            results = non_trivial_results(results)
        # Group results by repo
        results_per_patcher[patcher] = results_per_repo(results, repos)

    results = results_per_patcher[Patcher.MPatch]
    print("found " + str(len(results)) + " repo results")

    worst_results = []
    print(str(len(results.keys())))
    for repo in results.keys():
        repo_results_mpatch = results[repo]

        repo_results_mpatch = sorted(
            repo_results_mpatch, key=lambda x: x.outcome_classification.num_incorrect()
        )

        repo_results_mpatch = reversed(repo_results_mpatch)

        i = 0
        for result in repo_results_mpatch:
            if i > 4:
                break
            i += 1
            worst_results.append(result)

    worst_results = sorted(
        worst_results, key=lambda x: x.outcome_classification.num_incorrect()
    )

    worst_results = reversed(worst_results)
    i = 0
    for result in worst_results:
        if i > 4:
            break
        i += 1
        print(result)
        print()
