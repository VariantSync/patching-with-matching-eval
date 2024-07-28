import os
from typing import List
from typing import Dict
from typing import Optional

import numpy as np

from result_analysis.eval_setup import Metric, Patcher
from result_analysis.eval_setup import RQ3PatcherData
from result_analysis.eval_setup import PatchResult
from result_analysis.eval_setup import Repository
from result_analysis.io import load_repositories
from result_analysis.io import load_all_results
from result_analysis.latex import generate_metrics_result_table
from result_analysis.latex import generate_power_estimate_table
from result_analysis.result_handling import (
    edit_distance_percentiles,
    non_trivial_results,
    results_per_repo,
    all_results_per_language,
)
from result_analysis.result_handling import overall_automation
from result_analysis.result_handling import edit_distance
from result_analysis.result_handling import runtime
from result_analysis.result_handling import accumulate_data_per_patcher
from collections import defaultdict
from statsmodels.stats.multitest import multipletests
from scipy.stats import wilcoxon

languages = [
    ("Python", "Python"),
    ("JavaScript", "JavaS."),
    ("Go", "Go"),
    ("C++", "C++"),
    ("Java", "Java"),
    ("TypeScript", "TypeS."),
    ("C", "C"),
    ("C#", "C#"),
    ("PHP", "PHP"),
    ("Rust", "Rust"),
]


def list_all_dirs(path):
    return [
        os.path.join(path, d)
        for d in os.listdir(path)
        if os.path.isdir(os.path.join(path, d))
    ]


def rq3_table_generation(
    path_to_results, path_to_repo_list, only_non_trivial, file_metrics, file_power
):
    global languages
    repos = load_repositories(path_to_repo_list)

    result_dirs = list_all_dirs(path_to_results)

    print("Result dirs: " + str(result_dirs))

    results_per_patcher = accumulate_data_per_patcher(
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
    patcher_names = [patcher.nice_name() for patcher in Patcher]
    # corrected_significance, corrected_alpha = significance(results_per_patcher)
    differences = relative_difference(Patcher.MPatch, results_per_patcher)
    generate_metrics_result_table(
        patcher_names, languages, results_per_patcher, differences, file_metrics
    )
    # generate_power_estimate_table(
    #     patcher_names, language_names, results_per_patcher, corrected_alpha, file_power
    # )


def relative_difference(base_patcher: Patcher, results):
    base = base_patcher.nice_name()

    differences_per_patcher = defaultdict(dict)
    averages = []
    differences = []
    p_values = []
    for other_patcher in Patcher:
        other_patcher = other_patcher.nice_name()
        for metric in Metric:
            base_values = []
            other_values = []
            for dataset in results[base]:
                base_values.extend(
                    np.array(results[base][dataset].per_patch.get(metric))
                )
                other_values.extend(
                    np.array(results[other_patcher][dataset].per_patch.get(metric))
                )
            base_values = np.array(base_values)
            other_values = np.array(other_values)
            b = np.mean(base_values)
            o = np.mean(other_values)

            if other_patcher == base:
                differences_per_patcher[other_patcher][metric] = (
                    b,
                    0.0,
                    np.inf,
                )
                continue

            min_length = min(len(base_values), len(other_values))
            base_values = base_values[:min_length]
            other_values = other_values[:min_length]

            # _, p = wilcoxon(base_values, other_values)
            p = sign_test(base_values, other_values)
            average_difference = np.mean(other_values - base_values)
            average_difference /= np.mean(base_values)
            print(f"{metric}-{other_patcher}-base: {b}")
            print(f"{metric}-{other_patcher}-other: {o}")
            print(f"average diff: {average_difference}")
            print()
            differences.append(average_difference)
            p_values.append(p)
            averages.append(o)

    _, corrected_p_values, _, _ = multipletests(
        p_values, alpha=0.05, method="bonferroni"
    )

    i = 0
    for patcher in Patcher:
        patcher = patcher.nice_name()
        for metric in Metric:
            if patcher == base:
                continue
            differences_per_patcher[patcher][metric] = (
                averages[i],
                differences[i],
                corrected_p_values[i],
            )
            i += 1

    return differences_per_patcher


def sign_test(data1, data2):
    # The sign test can be approximated by a binomial test
    from scipy.stats import binomtest

    differences = [y - x for x, y in zip(data1, data2)]
    num_positive = sum(diff > 0 for diff in differences)
    num_negative = sum(diff < 0 for diff in differences)

    n = num_positive + num_negative
    k = min(num_positive, num_negative)

    p_value = binomtest(k, n, p=0.5, alternative="two-sided").pvalue
    return p_value


def significance(results):
    pwm = Patcher.MPatch.nice_name()

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
                            np.average(pwm_values),
                            other_patcher,
                            np.average(other_values),
                        )
                    )
                    p_values.append(p)

    # Correct for multiple tests
    _, corrected_p_values, _, corrected_alpha = multipletests(
        p_values, alpha=0.05, method="bonferroni"
    )

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

    return (results, corrected_alpha)


def better_or_worse(path_to_results, path_to_repo_list, only_non_trivial):
    global languages
    repos = load_repositories(path_to_repo_list)

    results_per_patcher = {}  # type: Dict[Patcher, Dict[Repository, List[PatchResult]]]
    all_equal = 0
    mpatch_best = 0
    patch_best = 0
    apply_best = 0
    cp_best = 0
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
        repo_results_cherry = results_per_patcher[Patcher.GitCherry][repo]

        sorted(repo_results_mpatch, key=lambda x: x.pick_id)
        sorted(repo_results_upatch, key=lambda x: x.pick_id)
        sorted(repo_results_apply, key=lambda x: x.pick_id)
        sorted(repo_results_cherry, key=lambda x: x.pick_id)

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

            scenario_size = res_mpatch.num_changes_total

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

            rm_min = min(ru, ra, rc)
            ru_min = min(rm, ra, rc)
            ra_min = min(rm, ru, rc)
            rc_min = min(rm, ru, ra)

            lang, user = res_mpatch.dataset.split("_")[:2]
            repo = "_".join(res_mpatch.dataset.split("_")[2:])
            if rm < rm_min:
                scenario_fits = scenario_size < 50 and lang != "C" and lang != "PHP"
                wrong_location = (
                    res_upatch.outcome_classification.applied_wrong_location
                    if res_upatch is not None
                    else 0
                )
                missing = (
                    res_upatch.outcome_classification.missing
                    if res_upatch is not None
                    else 0
                )

                patch_fits = wrong_location > 0 and missing > 0
                cp_fits = rc > 0
                if scenario_fits and patch_fits and cp_fits:
                    print("Found possible example:")
                    url = f"https://www.github.com/{user}/{repo}/commit/"
                    print(res_mpatch.dataset)
                    print(f"Cherry: {url}{res_mpatch.cherry_id}")
                    print(f"Target: {url}{res_mpatch.pick_id}")
                    print()
                mpatch_best += 1
            if ru < ru_min:
                patch_best += 1
            if ra < ra_min:
                apply_best += 1
            if rc < rc_min:
                cp_best += 1
            if rm == ru == ra == rc:
                all_equal += 1
            total += 1

    print("all are equal: " + f"{(100 * all_equal / total):.2f}%")
    print("mpatch is sole best: " + f"{(100 * mpatch_best / total):.2f}%")
    print("patch is sole best: " + f"{(100 * patch_best / total):.2f}%")
    print("apply is sole best: " + f"{(100 * apply_best / total):.2f}%")
    print("cherry-pick is sole best: " + f"{(100 * cp_best / total):.2f}%")
    print()


def find_example(path_to_results, path_to_repo_list, only_non_trivial):
    global languages
    repos = load_repositories(path_to_repo_list)

    results_per_patcher = {}  # type: Dict[Patcher, Dict[Repository, List[PatchResult]]]
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
        repo_results_cherry = results_per_patcher[Patcher.GitCherry][repo]

        sorted(repo_results_mpatch, key=lambda x: x.pick_id)
        sorted(repo_results_upatch, key=lambda x: x.pick_id)
        sorted(repo_results_apply, key=lambda x: x.pick_id)
        sorted(repo_results_cherry, key=lambda x: x.pick_id)

        repo_results_mpatch = {r.run_id: r for r in repo_results_mpatch}
        repo_results_upatch = {r.run_id: r for r in repo_results_upatch}
        repo_results_apply = {r.run_id: r for r in repo_results_apply}
        repo_results_cherry = {r.run_id: r for r in repo_results_cherry}

        for i in repo_results_mpatch.keys():
            res_mpatch = repo_results_mpatch.get(i, None)  # type: Optional[PatchResult]
            if res_mpatch is None:
                continue

            res_upatch = repo_results_upatch.get(i, None)  # type: Optional[PatchResult]
            res_cherry = repo_results_cherry.get(i, None)  # type: Optional[PatchResult]

            scenario_size = res_mpatch.num_changes_total

            rm = res_mpatch.outcome_classification.edit_distance
            rc = (
                res_cherry.outcome_classification.num_incorrect()
                if res_cherry is not None
                else float("inf")
            )

            lang, user = res_mpatch.dataset.split("_")[:2]
            repo = "_".join(res_mpatch.dataset.split("_")[2:])
            if rm == 0:
                scenario_fits = scenario_size < 50 and lang != "C" and lang != "PHP"
                wrong_location = (
                    res_upatch.outcome_classification.applied_wrong_location
                    if res_upatch is not None
                    else 0
                )
                missing = (
                    res_upatch.outcome_classification.missing
                    if res_upatch is not None
                    else 0
                )

                patch_fits = wrong_location > 0 and missing > 0
                cp_fits = rc > 0
                if scenario_fits and patch_fits and cp_fits:
                    print("Found possible example:")
                    url = f"https://www.github.com/{user}/{repo}/commit/"
                    print(res_mpatch.dataset)
                    print(f"Cherry: {url}{res_mpatch.cherry_id}")
                    print(f"Target: {url}{res_mpatch.pick_id}")
                    print()
    print()
