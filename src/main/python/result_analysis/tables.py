import os
from typing import List
from typing import Dict
from typing import Optional

import numpy

from result_analysis.eval_setup import Metric, Patcher
from result_analysis.eval_setup import RQ3PatcherData
from result_analysis.eval_setup import PatchResult
from result_analysis.eval_setup import Repository
from result_analysis.io import load_repositories
from result_analysis.io import load_all_results
from result_analysis.latex import generate_latex_table
from result_analysis.result_handling import (
    edit_distance_percentiles,
    non_trivial_results,
    results_per_repo,
    all_results_per_language,
)
from result_analysis.result_handling import overall_automation
from result_analysis.result_handling import edit_distance
from result_analysis.result_handling import runtime
from result_analysis.result_handling import cluster_results_per_patcher
from collections import defaultdict
from statsmodels.stats.multitest import multipletests

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
            patcher=patcher,
            precision=0,
            recall=0,
            patch_automation=oa,
            avg_edit_distance=average_ed,
            avg_runtime=average_run,
        )
        print(patcher_data)


def list_all_dirs(path):
    return [
        os.path.join(path, d)
        for d in os.listdir(path)
        if os.path.isdir(os.path.join(path, d))
    ]


def rq3_table_alt(path_to_results, path_to_repo_list, only_non_trivial):
    global languages
    repos = load_repositories(path_to_repo_list)

    result_dirs = list_all_dirs(path_to_results)

    print("Result dirs: " + str(result_dirs))

    results_per_patcher = cluster_results_per_patcher(
        repos=repos,
        result_dirs=result_dirs,
        languages=languages,
        only_non_trivial=only_non_trivial,
    )

    for language in languages:
        language = language[0]
        print(language)
        for patcher in Patcher:  # Patcher is an enum
            patcher_data = results_per_patcher[patcher.nice_name()][language]
            print(patcher_data)
    language_names = [lang[0] for lang in languages]
    patcher_names = [patcher.nice_name() for patcher in Patcher]
    corrected_significance = significance(results_per_patcher)
    generate_latex_table(
        patcher_names, language_names, results_per_patcher, corrected_significance
    )


def significance(results):
    import numpy as np
    from scipy.stats import wilcoxon

    pwm = Patcher.MPatch2.nice_name()

    comparisons = []
    p_values = []
    for other_patcher in Patcher:
        other_patcher = other_patcher.nice_name()
        if other_patcher == pwm:
            continue
        for dataset in results[pwm]:
            for metric in Metric:
                pwm_values = np.array(results[pwm][dataset].get(metric))
                other_values = np.array(results[other_patcher][dataset].get(metric))
                # Perform the Wilcoxon signed-rank test
                if not (sum(pwm_values) <= 0 or sum(other_values) <= 0):
                    _, p = wilcoxon(pwm_values, other_values)
                    comparisons.append(
                        (
                            dataset,
                            metric,
                            numpy.average(pwm_values),
                            other_patcher,
                            numpy.average(other_values),
                        )
                    )
                    p_values.append(p)
                else:
                    _, p = wilcoxon(
                        [x for x in range(0, 15)], [x for x in range(30, 45)]
                    )
                    comparisons.append(
                        (
                            dataset,
                            metric,
                            numpy.average(pwm_values),
                            other_patcher,
                            numpy.average(other_values),
                        )
                    )
                    p_values.append(p)

    # Correct for multiple tests
    corrected_p_values = multipletests(p_values, alpha=0.05, method="bonferroni")[1]

    # Print the results
    results = defaultdict(dict)
    for (dataset, metric, value1, classifier2, value2), p, corrected_p in zip(
        comparisons, p_values, corrected_p_values
    ):
        print(
            f"{dataset}: Comparison of {metric} for {classifier2}: p-value = {p}, corrected p-value = {corrected_p}"
        )
        print(f"{dataset}: {value1} vs. {value2}")
        print()
        if dataset not in results[classifier2]:
            results[classifier2][dataset] = {}

        results[classifier2][dataset][metric] = corrected_p

    return results


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

    results = results_per_patcher[Patcher.MPatch2]
    for repo in results.keys():
        repo_results_mpatch = results[repo]
        repo_results_upatch = results_per_patcher[Patcher.UnixPatch][repo]
        repo_results_apply = results_per_patcher[Patcher.GitApply][repo]
        repo_results_cherry = results_per_patcher[Patcher.GitCherry][repo]

        sorted(repo_results_mpatch, key=lambda x: x.run_id)
        sorted(repo_results_upatch, key=lambda x: x.run_id)
        sorted(repo_results_apply, key=lambda x: x.run_id)
        sorted(repo_results_cherry, key=lambda x: x.run_id)

        repo_results_mpatch = {r.run_id: r for r in repo_results_mpatch}
        repo_results_upatch = {r.run_id: r for r in repo_results_upatch}
        repo_results_apply = {r.run_id: r for r in repo_results_apply}
        repo_results_cherry = {r.run_id: r for r in repo_results_cherry}

        for i in repo_results_mpatch.keys():
            res_mpatch = repo_results_mpatch.get(i, None)  # type: Optional[PatchResult]
            if res_mpatch is None:
                continue

            res_upatch = repo_results_upatch.get(i, None)  # type: Optional[PatchResult]
            res_apply = repo_results_apply.get(i, None)  # type: Optional[PatchResult]
            res_cherry = repo_results_cherry.get(i, None)  # type: Optional[PatchResult]

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
            rc = (
                res_cherry.outcome_classification.fn()
                if res_cherry is not None
                else float("inf")
            )

            fn = res_mpatch.outcome_classification.fn()
            if fn > rc:
                if fn > 100:
                    print(
                        "Worse for patch "
                        + str(i)
                        + " in "
                        + str(repo)
                        + " with "
                        + str(rm)
                        + " vs. "
                        + str(rc)
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
