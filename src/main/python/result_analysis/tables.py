from typing import List
from result_analysis.eval_setup import Patcher
from result_analysis.eval_setup import OutcomeClassification
from result_analysis.io import load_repositories
from result_analysis.io import load_all_results
from result_analysis.result_handling import non_trivial_results
from result_analysis.result_handling import results_per_language
from result_analysis.result_handling import results_per_repo
from result_analysis.metrics import calculate_precision_recall


def rq3_table(path_to_results, path_to_repo_list, min_results_per_repo, only_non_trivial):
    repos = load_repositories(path_to_repo_list)
    patchers = []
    languages = [("Python", "py"), ("JavaScript", "js"), ("Go", "go"),
                 ("C++", "c++"), ("Java", "java"), ("TypeScript", "ts"),
                 ("C", "c"), ("C#", "c#"), ("PHP", "php"), ("Rust", "rust")]
    for patcher in Patcher:  # Patcher is an enum
        print("Loading results for " + str(patcher))
        results = load_all_results(path_to_results, patcher)
        # Filter trivial results
        if only_non_trivial:
            results = non_trivial_results(results)
        # Group results by repo
        results = results_per_repo(results, repos)
        # Accumulate repo results per language
        lang_results = results_per_language(results, min_results_per_repo)
        precision_per_language = []
        recall_per_language = []
        for language in languages:
            language = language[0]
            print("processing " + language)
            results = lang_results[language]
            precisions = []
            recalls = []
            for res in results:
                precision, recall = calculate_precision_recall(
                    res.tp(), res.fp(), res.fn())
                precisions.append(precision)
                recalls.append(recall)
            precision_per_language.append(precisions)
            recall_per_language.append(recalls)

        patchers.append(patcher)
        precisions_per_patcher.append(precision_per_language)
        recalls_per_patcher.append(recall_per_language)

    languages = [lang[1] for lang in languages]
    create_boxplot_per_patcher_per_language(
        patchers, languages, precisions_per_patcher, "Precision")
    create_boxplot_per_patcher_per_language(
        patchers, languages, recalls_per_patcher, "Recall")
