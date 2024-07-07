from result_analysis.eval_setup import Patcher
from result_analysis.io import load_repositories
from result_analysis.io import load_all_results
from result_analysis.result_handling import non_trivial_results, results_per_repo, all_results_per_language
from result_analysis.result_handling import overall_automation
from result_analysis.result_handling import edit_distance
from result_analysis.result_handling import runtime

languages = [("Python", "py"), ("JavaScript", "js"), ("Go", "go"),
             ("C++", "c++"), ("Java", "java"), ("TypeScript", "ts"),
             ("C", "c"), ("C#", "c#"), ("PHP", "php"), ("Rust", "rust")]


class RQ3PatcherData:
    def __init__(self, patcher: Patcher, patch_automation: float,
                 avg_edit_distance: float, median_edit_distance: int,
                 avg_runtime: float, median_runtime: int):
        self.patcher = patcher
        self.patch_automation = patch_automation
        self.avg_edit_distance = avg_edit_distance
        self.median_edit_distance = median_edit_distance
        self.avg_runtime = avg_runtime
        self.median_runtime = median_runtime

    def __str__(self):
        return (f"Patcher: {self.patcher}, "
                f"Patch Automation: {self.patch_automation}, "
                f"Avg Edit Distance: {self.avg_edit_distance}, "
                f"Median Edit Distance: {self.median_edit_distance}, "
                f"Avg Runtime: {self.avg_runtime}, "
                f"Median Runtime: {self.median_runtime}")


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
            patcher, oa, average_ed, median_ed, average_run, median_run)
        print(patcher_data)

def rq3_table_alt(path_to_results, path_to_repo_list, only_non_trivial):
    global languages
    patchers = []
    global languages
    repos = load_repositories(path_to_repo_list)
    patchers = []

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
            patcher_data = RQ3PatcherData(
                patcher, oa, average_ed, median_ed, average_run, median_run)
            print(patcher_data)

