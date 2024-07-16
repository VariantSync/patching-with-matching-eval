import numpy as np


def generate_latex_table(patcher_names, languages, results_per_patcher):
    with open("experiment_table.tex", "w") as file:
        # Begin the tabular environment
        file.write("\\begin{tabular}{|l|l|" + "r|" * len(languages) + "}\n")
        file.write("\\hline\n")

        # Write the multi-column header for languages
        language_header = (
            "Metric & Patcher & \\multicolumn{"
            + str(len(languages))
            + "}{c|}{Project Languages} \\\\\n"
        )
        file.write(language_header)
        file.write("\\cline{3-" + str(len(languages) + 2) + "}\n")

        # Write the language names
        language_names = [language for language in languages]
        for i in range(0, len(language_names)):
            if language_names[i] == "C#":
                language_names[i] = "C\\#"
        file.write(" & & " + " & ".join(language_names) + " \\\\\n")
        file.write("\\hline\n")

        # Write the multi-rows and their corresponding rows
        metrics = ["Precision", "Recall", "Automation", "Edit Distance", "Runtime"]
        for metric in metrics:
            file.write(
                "\\multirow{" + str(len(results_per_patcher)) + "}{*}{" + metric + "}\n"
            )
            for patcher in patcher_names:
                line = " & " + patcher
                for language in languages:
                    value = 0
                    best_type = ""
                    results = results_per_patcher[patcher][language]
                    if metric == "Precision":
                        value = np.mean(results.precision)
                        best_type = "max"
                    elif metric == "Recall":
                        value = np.mean(results.recall)
                        best_type = "max"
                    elif metric == "Automation":
                        value = 100 * np.mean(results.patch_automation)
                        best_type = "max"
                    elif metric == "Edit Distance":
                        value = np.mean(results.avg_edit_distance)
                        best_type = "min"
                    elif metric == "Runtime":
                        value = np.mean(results.avg_runtime)
                        best_type = "min"
                    else:
                        value = -1

                    max_value = determine_best(
                        results_per_patcher, patcher_names, metric, best_type, language
                    )
                    if value == max_value:
                        line += " & \\textbf{" + f"{value:.2f}" + "}"
                    else:
                        line += " & " + f"{value:.2f}"
                file.write(line + " \\\\\n")
            file.write("\\hline\n")

        # End the tabular environment
        file.write("\\end{tabular}")


def determine_best(results_per_patcher, patcher_names, metric, best_type, language):
    values = []
    for patcher in patcher_names:
        results = results_per_patcher[patcher][language]
        if metric == "Precision":
            values.append(np.mean(results.precision))
        elif metric == "Recall":
            values.append(np.mean(results.recall))
        elif metric == "Automation":
            values.append(100 * np.mean(results.patch_automation))
        elif metric == "Edit Distance":
            values.append(np.mean(results.avg_edit_distance))
        elif metric == "Runtime":
            values.append(np.mean(results.avg_runtime))

    if best_type == "max":
        return max(values)
    elif best_type == "min":
        return min(values)
