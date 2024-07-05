from typing import Dict
from typing import List
from result_analysis.eval_setup import PatchResult
from result_analysis.eval_setup import OutcomeClassification
from collections import defaultdict


import matplotlib.pyplot as plt


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
