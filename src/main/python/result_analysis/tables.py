import os
import numpy as np

from result_analysis.eval_setup import Metric, Patcher
from result_analysis.io import load_repositories
from result_analysis.io import load_all_results
from result_analysis.latex import generate_metrics_result_table
from result_analysis.result_handling import (
    non_trivial_results,
    results_per_repo,
)
from result_analysis.result_handling import accumulate_data_per_patcher
from collections import defaultdict
from statsmodels.stats.multitest import multipletests
from scipy.stats import wilcoxon

languages = [
    ("Python", "Python"),
    ("JavaScript", "\\multicolumn{1}{c}{JavaS.}"),
    ("Go", "Go"),
    ("C++", "\\multicolumn{1}{c}{C++}"),
    ("Java", "Java"),
    ("TypeScript", "\\multicolumn{1}{c}{TypeS.}"),
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


def metrics_table_generation(
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
    effects = []
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
            b = np.nanmean(base_values)
            o = np.nanmean(other_values)

            if other_patcher == base:
                differences_per_patcher[other_patcher][metric] = (
                    b,
                    0.0,
                    np.nan,
                    np.nan,
                )
                continue

            min_length = min(len(base_values), len(other_values))
            base_values = base_values[:min_length]
            other_values = other_values[:min_length]

            # _, p = wilcoxon(base_values, other_values)
            p, d = wilcoxon_effect_size(base_values, other_values)
            average_difference = np.nanmean(other_values - base_values)
            average_difference /= np.nanmean(base_values)
            print(f"{metric}-{other_patcher}-base: {b}")
            print(f"{metric}-{other_patcher}-other: {o}")
            print(f"average diff: {average_difference}")
            print()
            differences.append(average_difference)
            p_values.append(p)
            averages.append(o)
            effects.append(d)

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
                effects[i],
            )
            i += 1

    return differences_per_patcher


def wilcoxon_effect_size(data1, data2):
    filtered_data = [
        (x, y) for x, y in zip(data1, data2) if not (np.isnan(x) or np.isnan(y))
    ]
    data1, data2 = zip(*filtered_data)

    stat, p_value = wilcoxon(data1, data2)

    N = np.sum(np.array(data1) != np.array(data2))
    expected_rank_sum = N * (N + 1) / 4
    rank_biserial_r = (stat - expected_rank_sum) / expected_rank_sum

    return p_value, rank_biserial_r


def find_example(path_to_results, path_to_repo_list, only_non_trivial):
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
            res_mpatch = repo_results_mpatch.get(i, None)
            if res_mpatch is None:
                continue

            res_upatch = repo_results_upatch.get(i, None)
            res_cherry = repo_results_cherry.get(i, None)

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


def venn_diagram(path_to_results, path_to_repo_list, only_non_trivial):
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

    rm_best = 0
    rm_equal = 0
    rm_worse = 0
    num_total = 0
    rc_better = 0
    ru_better = 0
    ra_better = 0
    rc_worse = 0
    ru_worse = 0
    ra_worse = 0

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
            res_mpatch = repo_results_mpatch.get(i, None)
            if res_mpatch is None:
                continue

            res_upatch = repo_results_upatch.get(i, None)
            res_cherry = repo_results_cherry.get(i, None)
            res_apply = repo_results_apply.get(i, None)

            rm = res_mpatch.outcome_classification.num_incorrect()
            rc = (
                res_cherry.outcome_classification.num_incorrect()
                if res_cherry is not None
                else float("inf")
            )
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
            lang, user = res_mpatch.dataset.split("_")[:2]
            repo = "_".join(res_mpatch.dataset.split("_")[2:])

            if rm < rc:
                rc_worse += 1
            if rm < ru:
                ru_worse += 1
            if rm < ra:
                ra_worse += 1

            if rm < rc and rm < ru and rm < ra:
                rm_best += 1
            elif rm <= rc and rm <= ru and rm <= ra:
                rm_equal += 1
            else:
                rm_worse += 1
                if rm > rc:
                    rc_better += 1
                if rm > ru:
                    ru_better += 1
                if rm > ra:
                    ra_better += 1
            num_total += 1

    print("mpatch is best: " + str(rm_best / num_total))
    print("mpatch at least as good: " + str(rm_equal / num_total))
    print("mpatch worse: " + str(rm_worse / num_total))
    print("cp better: " + str(rc_better / num_total))
    print("patch better: " + str(ru_better / num_total))
    print("apply better: " + str(ra_better / num_total))

    print("cp worse: " + str(rc_worse / num_total))
    print("patch worse: " + str(ru_worse / num_total))
    print("apply worse: " + str(ra_worse / num_total))
    print()
