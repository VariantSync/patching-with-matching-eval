from typing import Callable
from typing import List
from eval_setup import Repository
from eval_setup import PatchResult


def results_per_language(results_per_repo: dict[Repository, PatchResult], min_num_results_per_repo: int) -> {}:
    language_cp_dict = {}

    for repo in results_per_repo.keys():
        results = results_per_repo[repo]
        if len(results) < min_num_results_per_repo:
            # We only consider repos with a minimum number of cherry picks
            continue

    # TODO:
    pass


def filter_results(
        results: List[PatchResult],
        filter_func: Callable[[PatchResult], bool]) -> List[PatchResult]:
    return [result for result in results if filter_func(result)]


def non_trivial_results(results: List[PatchResult]) -> List[PatchResult]:
    return filter_results(results, lambda r: not r.patch_is_trivial)
