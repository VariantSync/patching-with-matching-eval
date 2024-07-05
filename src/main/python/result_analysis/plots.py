from typing import Dict
from typing import List
from result_analysis.eval_setup import PatchResult
from result_analysis.eval_setup import Patcher
from result_analysis.eval_setup import OutcomeClassification
from result_analysis.io import load_repositories
from result_analysis.io import load_all_results
from result_analysis.result_handling import non_trivial_results
from result_analysis.result_handling import results_per_language
from result_analysis.result_handling import results_per_repo
from result_analysis.metrics import calculate_precision_recall
from collections import defaultdict


import matplotlib.pyplot as plt


def boxplot_results_per_patcher(path_to_results, path_to_repo_list, min_results_per_repo):
    repos = load_repositories(path_to_repo_list)
    precisions_per_patcher = []
    recalls_per_patcher = []
    patchers = []
    for patcher in Patcher:  # Patcher is an enum
        results = load_all_results(path_to_results, patcher)
        # Filter trivial results
        results = non_trivial_results(results)
        # Group results by repo
        results = results_per_repo(results, repos)
        # Accumulate repo results per language
        results = results_per_language(results, min_results_per_repo)
        precision_per_language = []
        recall_per_language = []
        languages = []
        for language, results in results.items():
            # Sum up the results per language
            classification = OutcomeClassification()
            for result in results:
                classification.add_result(result)

            languages.append(language)
            precision, recall = calculate_precision_recall(
                classification.tp(), classification.fp(), classification.fn())
            precision_per_language.append(precision)
            recall_per_language.append(recall)

        patchers.append(patcher)
        precisions_per_patcher.append(precision_per_language)
        recalls_per_patcher.append(recall_per_language)

    # Initialize figure for boxplots
    plt.figure(figsize=(10, 6))
    # Add a boxplot for the precision on repos of that language to the figure
    plt.boxplot(precisions_per_patcher, labels=patchers)
    plt.xlabel('Patcher')
    plt.ylabel('Precision')
    plt.title('Precision per Patcher')

    # Show the final figure
    plt.show()

    # Initialize figure for boxplots
    plt.figure(figsize=(10, 6))
    # Add a boxplot for the precision on repos of that language to the figure
    plt.boxplot(recalls_per_patcher, labels=patchers)
    plt.xlabel('Patcher')
    plt.ylabel('Recall')
    plt.title('Recall per Patcher')

    # Show the final figure
    plt.show()


def boxplot_results_per_language(
        results_per_language: Dict[str, List[PatchResult]]):
    # Initialize figure for boxplots
    plt.figure(figsize=(10, 6))

    # Calculate the precisions for each repository associated with a language
    # and create a boxplot for that language
    language_names = []
    all_precisions = []
    for language, results in results_per_language.items():
        precision_per_repo = []
        for result in results:
            tp = result.tp()
            fp = result.fp()
            if tp + fp > 0:
                precision = tp / (tp + fp)
            else:
                precision = 0
            precision_per_repo.append(precision)
        all_precisions.append(precision_per_repo)
        language_names.append(language)

    # Add a boxplot for the precision on repos of that language to the figure
    plt.boxplot(all_precisions, labels=language_names)
    plt.xlabel('Programming Language')
    plt.ylabel('Precision')
    plt.title('Precision per Language for Patch Outcomes')

    # Show the final figure
    plt.show()
