from typing import List
from result_analysis.eval_setup import Patcher
from result_analysis.eval_setup import OutcomeClassification
from result_analysis.io import load_repositories
from result_analysis.io import load_all_results
from result_analysis.result_handling import non_trivial_results
from result_analysis.result_handling import project_results_per_language
from result_analysis.result_handling import all_results_per_language
from result_analysis.result_handling import results_per_repo
from result_analysis.metrics import calculate_precision_recall


import matplotlib.pyplot as plt

languages = [("Python", "py"), ("JavaScript", "js"), ("Go", "go"),
             ("C++", "c++"), ("Java", "java"), ("TypeScript", "ts"),
             ("C", "c"), ("C#", "c#"), ("PHP", "php"), ("Rust", "rust")]


def boxplot_results_lang_overall(path_to_results, path_to_repo_list, only_non_trivial):
    global languages
    repos = load_repositories(path_to_repo_list)
    precisions_per_patcher = []
    recalls_per_patcher = []
    patchers = []
    for patcher in Patcher:  # Patcher is an enum
        print("Loading results for " + str(patcher))
        results = load_all_results(path_to_results, patcher)
        # Filter trivial results
        if only_non_trivial:
            results = non_trivial_results(results)
        # Group results by repo
        results = results_per_repo(results, repos)
        # Accumulate repo results per language
        lang_results = all_results_per_language(
            results)
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

    language_names = [lang[1] for lang in languages]
    create_boxplot_per_patcher_per_language(
        patchers, language_names, precisions_per_patcher, "PrecisionAll", showfliers=False)
    create_boxplot_per_patcher_per_language(
        patchers, language_names, recalls_per_patcher, "RecallAll", showfliers=False)


def boxplot_results_lang_per_proj(path_to_results, path_to_repo_list, min_results_per_repo, only_non_trivial):
    global languages
    repos = load_repositories(path_to_repo_list)
    precisions_per_patcher = []
    recalls_per_patcher = []
    patchers = []
    for patcher in Patcher:  # Patcher is an enum
        print("Loading results for " + str(patcher))
        results = load_all_results(path_to_results, patcher)
        # Filter trivial results
        if only_non_trivial:
            results = non_trivial_results(results)
        # Group results by repo
        results = results_per_repo(results, repos)
        # Accumulate repo results per language
        lang_results = project_results_per_language(
            results, min_results_per_repo)
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

    language_names = [lang[1] for lang in languages]
    create_boxplot_per_patcher_per_language(
        patchers, language_names, precisions_per_patcher, "Precision")
    create_boxplot_per_patcher_per_language(
        patchers, language_names, recalls_per_patcher, "Recall")


def create_boxplot_per_patcher_per_language(
        patchers: List[Patcher],
        language_names: List[str],
        values_per_patcher,
        value_name: str,
        showfliers=True):
    # Initialize figure for boxplots
    plt.figure(figsize=(10, 6))
    width = 0.25  # the width of the bars
    multiplier = -1

    fig, ax = plt.subplots(layout='constrained')

    # Create a boxplot for each language for each patcher
    # There are ten languages that we considered, and for each language we have a different number of repos
    for i, patcher in enumerate(patchers):
        print(patcher)
        for j, lang in enumerate(language_names):
            print(lang + ": " + str(values_per_patcher[i][j]))

        offset = width * multiplier
        # We evaluated the precision of three patchers for each language and repo
        # precisions_per_patcher is a list of three lists (one for each patcher); each patcher list contains ten lists
        # (one for each language), and each of those ten lists contains x lists (one for each repository with that
        # language)
        language_precisions = values_per_patcher[i]

        # This command should plot 10 boxplots for each considered patcher
        # The x-axis lists the ten languages, and the results of the different patchers are placed next to each other
        # for each language
        ax.boxplot(language_precisions, positions=[
            (x + offset) for x in range(0, 10)], widths=0.1, patch_artist=True,
            boxprops=dict(facecolor='C{}'.format(i)), showfliers=showfliers)
        multiplier += 1

    plt.xticks(range(len(languages)), language_names)
    plt.xlabel('Project Language on GitHub')
    plt.ylabel("Patching " + value_name)
    # plt.title(value_name + ' per Language for each Patcher')
    plt.legend([plt.Line2D([0], [0], color='C{}'.format(i), lw=4)
                for i in range(len(patchers))], patchers)
    plt.show()
    plt.savefig(
        "/home/alex/papers/self/patching-with-matching/paper/figures/" + value_name + ".pdf")


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
        results = project_results_per_language(results, min_results_per_repo)
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
