from typing import Callable
from typing import List
from typing import Dict
from result_analysis.eval_setup import AccumulatedPatcherData, Repository
from result_analysis.eval_setup import PatchResult
from result_analysis.eval_setup import OutcomeClassification
from result_analysis.eval_setup import Patcher
from result_analysis.eval_setup import RQ3PatcherData
from result_analysis.io import load_all_results
from result_analysis.metrics import calculate_f1_score, calculate_precision_recall
from collections import defaultdict
import numpy as np


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
    if not results:
        return -1.0
    num_perfect = 0
    for result in results:
        oc = result.outcome_classification

        if oc.fp() == 0 and oc.fn() == 0:
            num_perfect += 1

    return num_perfect / len(results)


def edit_distance_percentiles(
    results: List[PatchResult],
) -> tuple[int, int, int, int, int]:
    """
    Determine the 25, 50, 75, 99, and 100 percentiles of edit distances
    """
    if not results:
        return (-1, -1, -1, -1, -1)

    edit_distances = []
    for result in results:
        edit_distances.append(result.outcome_classification.num_incorrect())

    edit_distances = sorted(edit_distances)

    # Determine percentiles
    percentiles = np.percentile(edit_distances, [25, 50, 75, 99, 100])

    return tuple(percentiles)


def outlier_free_results(
    results: List[PatchResult],
) -> list[PatchResult]:
    if not results:
        return results

    edit_distances = [
        result.outcome_classification.num_incorrect() for result in results
    ]
    b99 = np.percentile(edit_distances, 99)

    filtered_results = [
        result
        for result in results
        if result.outcome_classification.num_incorrect() <= b99
    ]
    return filtered_results


def edit_distance(results: List[PatchResult]) -> tuple[float, int]:
    """
    Calculate the average edit distance and median edit distance from a list of results.
    """
    if not results:
        return (-1.0, -1)
    edit_distances = []
    for result in results:
        edit_distances.append(result.outcome_classification.num_incorrect())

    return (
        sum(edit_distances) / len(edit_distances),
        sorted(edit_distances)[len(edit_distances) // 2],
    )


def runtime(results: List[PatchResult]) -> tuple[float, int]:
    """
    Calculate the average runtime and median runtime from a list of results.
    """
    if not results:
        return (-1.0, -1)
    runtimes = []
    for result in results:
        runtimes.append(result.patch_duration)

    return (sum(runtimes) / len(runtimes), sorted(runtimes)[len(runtimes) // 2])


def accumulate_data_per_patcher(
    repos, result_dirs, languages, only_non_trivial
) -> Dict:
    results_per_patcher = defaultdict(dict)
    for result_dir in sorted(result_dirs):
        for language in languages:
            language = language[0]
            for patcher in Patcher:  # Patcher is an enum
                results = load_all_results(result_dir, patcher)
                if len(results) == 0:
                    continue
                # Filter trivial results
                if only_non_trivial:
                    results = non_trivial_results(results)
                # results = outlier_free_results(results)
                # Group results by repo
                results = results_per_repo(results, repos)
                # Accumulate repo results per language
                lang_results = all_results_per_language(results)
                results = lang_results[language]

                tp = 0.0
                fp = 0.0
                fn = 0.0
                data_per_patch = None
                for res in results:
                    oc = res.outcome_classification
                    if oc.num_positive() == 0:
                        continue
                    tp += oc.tp()
                    fp += oc.fp()
                    fn += oc.fn()
                    p, r = calculate_precision_recall(
                        oc.tp(),
                        oc.fp(),
                        oc.fn(),
                    )
                    f1 = calculate_f1_score(p, r)
                    # if np.isnan(p) or np.isnan(r):
                    #    continue
                    num_incorrect = oc.num_incorrect()
                    if num_incorrect == 0:
                        a = 1
                    else:
                        a = 0
                    if data_per_patch is None:
                        data_per_patch = RQ3PatcherData(
                            patcher=patcher,
                            precision=p,
                            recall=r,
                            f1_score=f1,
                            patch_automation=a,
                            avg_edit_distance=num_incorrect,
                            avg_runtime=res.patch_duration,
                        )
                    else:
                        data_per_patch.add_data(
                            precision=p,
                            recall=r,
                            f1_score=f1,
                            patch_automation=a,
                            avg_edit_distance=num_incorrect,
                            avg_runtime=res.patch_duration,
                        )

                precision, recall = calculate_precision_recall(
                    tp=tp,
                    fp=fp,
                    fn=fn,
                )

                f1_score = calculate_f1_score(precision, recall)

                oa = overall_automation(results)
                (average_ed, _) = edit_distance(results)
                (average_run, _) = runtime(results)

                if language in results_per_patcher[patcher.nice_name()]:
                    accumulated_data = results_per_patcher[patcher.nice_name()][
                        language
                    ]
                    accumulated_data.accumulated_data.add_data(
                        precision=precision,
                        recall=recall,
                        f1_score=f1_score,
                        patch_automation=oa,
                        avg_edit_distance=average_ed,
                        avg_runtime=average_run,
                    )
                    accumulated_data.data_per_patch.extend(data_per_patch)
                else:
                    patcher_data = RQ3PatcherData(
                        patcher=patcher,
                        precision=precision,
                        recall=recall,
                        f1_score=f1_score,
                        patch_automation=oa,
                        avg_edit_distance=average_ed,
                        avg_runtime=average_run,
                    )
                    patcher_data = AccumulatedPatcherData(patcher_data, data_per_patch)
                    results_per_patcher[patcher.nice_name()][language] = patcher_data
    return results_per_patcher
