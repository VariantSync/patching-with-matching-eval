from typing import List
from typing import Dict
from typing import Optional
from result_analysis.eval_setup import Patcher
from result_analysis.eval_setup import PatchResult
from result_analysis.eval_setup import Repository
from result_analysis.io import load_repositories
from result_analysis.io import load_all_results
from result_analysis.result_handling import (
    edit_distance_percentiles,
    non_trivial_results,
    results_per_repo,
    all_results_per_language,
)
from result_analysis.result_handling import overall_automation
from result_analysis.result_handling import edit_distance
from result_analysis.result_handling import runtime

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


class RQ3PatcherData:
    def __init__(
        self,
        patcher: Patcher,
        patch_automation: float,
        avg_edit_distance: float,
        median_edit_distance: int,
        avg_runtime: float,
        median_runtime: int,
    ):
        self.patcher = patcher
        self.patch_automation = patch_automation
        self.avg_edit_distance = avg_edit_distance
        self.median_edit_distance = median_edit_distance
        self.avg_runtime = avg_runtime
        self.median_runtime = median_runtime

    def __str__(self):
        return (
            f"Patcher: {self.patcher}, "
            f"Patch Automation: {self.patch_automation}, "
            f"Avg Edit Distance: {self.avg_edit_distance}, "
            f"Median Edit Distance: {self.median_edit_distance}, "
            f"Avg Runtime: {self.avg_runtime}, "
            f"Median Runtime: {self.median_runtime}"
        )


def rq3_table(path_to_results, only_non_trivial):
    global languages
    patchers = []
    for patcher in Patcher:  # Patcher is an enum
        print("Loading results for " + str(patcher))
        # type: List[PatchResult]
        results = load_all_results(path_to_results, patcher)
        # Filter trivial results
        if only_non_trivial:
            results = non_trivial_results(results)

        patchers.append(patcher)
        oa = overall_automation(results)
        (average_ed, median_ed) = edit_distance(results)
        (average_run, median_run) = runtime(results)
        patcher_data = RQ3PatcherData(
            patcher, oa, average_ed, median_ed, average_run, median_run
        )
        print(patcher_data)


def rq3_table_alt(path_to_results, path_to_repo_list, only_non_trivial):
    global languages
    repos = load_repositories(path_to_repo_list)

    for language in languages:
        language = language[0]
        for patcher in Patcher:  # Patcher is an enum
            print("Loading results for " + str(patcher))
            results = load_all_results(path_to_results, patcher)
            # Filter trivial results
            if only_non_trivial:
                results = non_trivial_results(results)
            # Group results by repo
            results = results_per_repo(results, repos)
            # Accumulate repo results per language
            lang_results = all_results_per_language(results)
            results = lang_results[language]
            print(language)
            oa = overall_automation(results)
            (average_ed, median_ed) = edit_distance(results)
            (average_run, median_run) = runtime(results)
            ed_percentiles = edit_distance_percentiles(results)
            patcher_data = RQ3PatcherData(
                patcher, oa, average_ed, median_ed, average_run, median_run
            )
            print(patcher_data)
            print(ed_percentiles)


def better_or_worse(path_to_results, path_to_repo_list, only_non_trivial):
    global languages
    repos = load_repositories(path_to_repo_list)

    results_per_patcher = {}  # type: Dict[Patcher, Dict[Repository, List[PatchResult]]]
    mpatch_better = 0
    better_diff = 0
    mpatch_equal = 0
    mpatch_worse = 0
    worse_diff = 0
    total = 0
    for patcher in Patcher:  # Patcher is an enum
        results = load_all_results(path_to_results, patcher)
        # Filter trivial results
        if only_non_trivial:
            results = non_trivial_results(results)
        # Group results by repo
        results_per_patcher[patcher] = results_per_repo(results, repos)

    results = results_per_patcher[Patcher.MPatch]
    for repo in results.keys():
        repo_results_mpatch = results[repo]
        repo_results_upatch = results_per_patcher[Patcher.UnixPatch][repo]
        repo_results_apply = results_per_patcher[Patcher.GitApply][repo]

        sorted(repo_results_mpatch, key=lambda x: x.run_id)
        sorted(repo_results_upatch, key=lambda x: x.run_id)
        sorted(repo_results_apply, key=lambda x: x.run_id)

        repo_results_mpatch = {r.run_id: r for r in repo_results_mpatch}
        repo_results_upatch = {r.run_id: r for r in repo_results_upatch}
        repo_results_apply = {r.run_id: r for r in repo_results_apply}

        for i in repo_results_mpatch.keys():
            res_mpatch = repo_results_mpatch.get(i, None)  # type: Optional[PatchResult]
            if res_mpatch is None:
                continue

            res_upatch = repo_results_upatch.get(i, None)  # type: Optional[PatchResult]
            res_apply = repo_results_apply.get(i, None)  # type: Optional[PatchResult]

            rm = res_mpatch.outcome_classification.num_incorrect()
            ru = (
                res_upatch.outcome_classification.num_incorrect()
                if res_upatch is not None
                else float("inf")
            )
            ra = (
                res_apply.outcome_classification.num_incorrect()
                if res_apply is not None
                else float("inf")
            )

            if rm < ru and rm < ra:
                mpatch_better += 1
                if ru < ra:
                    # We consider the difference to the one that has fewer errors
                    better_diff += ru - rm
                else:
                    better_diff += ra - rm
            elif rm > ru or rm > ra:
                mpatch_worse += 1
                if ru < ra:
                    # We consider the difference to the one that has fewer errors
                    worse_diff += rm - ru
                else:
                    worse_diff += rm - ra
            else:
                mpatch_equal += 1
            total += 1

    print("mpatch is better: " + str(100 * mpatch_better / total) + "%")
    print("mpatch is equal: " + str(100 * mpatch_equal / total) + "%")
    print("mpatch is worse: " + str(100 * mpatch_worse / total) + "%")
    print("better diff: " + str(better_diff / total))
    print("worse diff: " + str(worse_diff / total))
    print()
