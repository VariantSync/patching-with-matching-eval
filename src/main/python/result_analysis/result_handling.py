from typing import Callable
from typing import List
from typing import Dict
from result_analysis.eval_setup import Repository
from result_analysis.eval_setup import PatchResult
from result_analysis.eval_setup import OutcomeClassification
from collections import defaultdict


def results_per_repo(
    results: List[PatchResult], repos_by_name: Dict[str, Repository]
) -> Dict[Repository, List[PatchResult]]:
    repo_results = defaultdict(list)
    for result in results:
        repo = repos_by_name[result.dataset]
        repo_results[repo].append(result)
    return repo_results


def all_results_per_language(
    repo_results: Dict[Repository, List[PatchResult]],
) -> Dict[str, List[PatchResult]]:
    language_cp_dict = defaultdict(list)

    for repo in repo_results.keys():
        results = repo_results[repo]

        # Sum up the results per repo
        for result in results:
            language_cp_dict[repo.language].append(result)

    return language_cp_dict


def project_results_per_language(
    repo_results: Dict[Repository, List[PatchResult]], min_num_results_per_repo: int
) -> Dict[str, List[OutcomeClassification]]:
    language_cp_dict = defaultdict(list)

    for repo in repo_results.keys():
        results = repo_results[repo]
        if len(results) < min_num_results_per_repo:
            # We only consider repos with a minimum number of cherry picks
            continue

        # Sum up the results per repo
        classification = OutcomeClassification()
        for result in results:
            classification.add_result(result.outcome_classification)

        language_cp_dict[repo.language].append(classification)

    return language_cp_dict


def filter_results(
    results: List[PatchResult], filter_func: Callable[[PatchResult], bool]
) -> List[PatchResult]:
    return [result for result in results if filter_func(result)]


def non_trivial_results(results: List[PatchResult]) -> List[PatchResult]:
    return filter_results(results, lambda r: not r.patch_is_trivial)


def overall_automation(results: List[PatchResult]) -> float:
    """
    Calculate the overall automation percentage
    """
    num_perfect = 0
    for result in results:
        oc = result.outcome_classification

        if oc.fp() == 0 and oc.fn() == 0:
            num_perfect += 1

    return num_perfect / len(results)


def edit_distance(results: List[PatchResult]) -> tuple[float, int]:
    """
    Calculate the average edit distance and median edit distance from a list of results.
    """

    edit_distances = []
    for result in results:
        edit_distances.append(result.outcome_classification.edit_distance)

    return (
        sum(edit_distances) / len(edit_distances),
        sorted(edit_distances)[len(edit_distances) // 2],
    )


def runtime(results: List[PatchResult]) -> tuple[float, int]:
    """
    Calculate the average runtime and median runtime from a list of results.
    """
    runtimes = []
    for result in results:
        runtimes.append(result.patch_duration)

    return (sum(runtimes) / len(runtimes), sorted(runtimes)[len(runtimes) // 2])
