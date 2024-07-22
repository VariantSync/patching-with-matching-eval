from result_analysis.eval_setup import OutcomeClassification
from result_analysis.eval_setup import Patcher
from result_analysis.io import find_results_for_patcher
from result_analysis.io import read_results_from_file
from result_analysis.io import load_repositories
from result_analysis.result_handling import results_per_repo
from result_analysis.io import load_all_results
from result_analysis.latex import generate_metrics_result_table
from result_analysis.result_handling import (
    non_trivial_results,
    all_results_per_language,
)
from result_analysis.result_handling import overall_automation
from result_analysis.result_handling import edit_distance
from result_analysis.result_handling import runtime
from result_analysis.metrics import calculate_precision_recall
from collections import defaultdict

languages = [
    ("Python", "py"),
    ("JavaScript", "js"),
    ("Go", "go"),
    ("C++", "c++"),
    ("Java", "java"),
    ("TypeScript", "ts"),
    ("C", "c"),
    ("C#", "c#"),
    ("PHP", "php"),
    ("Rust", "rust"),
]


def print_result_data():
    directory_path = "/home/alex/data/cherry-picks/results/"
    total_results = 0
    summed_result = OutcomeClassification()
    for file_path in find_results_for_patcher(directory_path, Patcher.MPatch):
        result_objects = read_results_from_file(file_path)
        print("Read " + str(len(result_objects)) + " results.")
        total_results += len(result_objects)
        summed_result.add_result(result_objects[0].outcome_classification)
    print("Total: " + str(total_results))
    print("summed result: \n" + str(summed_result))


def load_repos():
    repo_sample = "../../../simulation-files/data/repo-sample.yaml"
    repos = load_repositories(repo_sample)
    print("num repos: " + str(len(repos)))
    return repos


def print_results_per_repo():
    repos = load_repos()

    directory_path = "/home/alex/data/cherry-picks/results/"
    results = []
    for file_path in find_results_for_patcher(directory_path, Patcher.MPatch):
        results.extend(read_results_from_file(file_path))

    # type: Dict[Repository, PatchResult]
    repo_results = results_per_repo(results, repos)
    for repo, results in repo_results.items():
        print(repo.name + ": " + str(len(results)))
