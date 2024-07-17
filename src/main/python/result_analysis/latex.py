import numpy as np

from result_analysis.eval_setup import Metric


def generate_latex_table(
    patcher_names, languages, results_per_patcher, corrected_significance, file
):
    with open(file, "w") as file:
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
        for metric in Metric:
            file.write(
                "\\multirow{"
                + str(len(results_per_patcher))
                + "}{*}{"
                + metric.nice_name()
                + "}\n"
            )
            for patcher in patcher_names:
                line = " & " + patcher
                for language in languages:
                    value = 0
                    best_type = ""
                    results = results_per_patcher[patcher][language]
                    value = np.mean(results.get(metric))
                    if metric == Metric.Precision:
                        best_type = "max"
                    elif metric == Metric.Recall:
                        best_type = "max"
                    elif metric == Metric.Automation:
                        value = 100 * value
                        best_type = "max"
                    elif metric == Metric.EditDistance:
                        best_type = "min"
                    elif metric == Metric.Runtime:
                        best_type = "min"
                    else:
                        value = -1

                    max_value = determine_best(
                        results_per_patcher, patcher_names, metric, best_type, language
                    )

                    p_value = 1.0
                    if patcher in corrected_significance:
                        if language in corrected_significance[patcher]:
                            if metric in corrected_significance[patcher][language]:
                                p_value = corrected_significance[patcher][language][
                                    metric
                                ]
                    if p_value < 0.01:
                        color = "blue!90"
                    elif p_value < 0.02:
                        color = "blue!70"
                    elif p_value < 0.03:
                        color = "blue!50"
                    elif p_value < 0.04:
                        color = "blue!30"
                    elif p_value < 0.05:
                        color = "blue!10"
                    else:
                        color = "white"

                    if value == max_value:
                        line += f" & \\cellcolor{{{color}}}\\textbf{{{value:.2f}}}"
                    else:
                        line += f" & \\cellcolor{{{color}}}{value:.2f}"

                file.write(line + " \\\\\n")
            file.write("\\hline\n")

        # End the tabular environment
        file.write("\\end{tabular}")


def determine_best(results_per_patcher, patcher_names, metric, best_type, language):
    values = []
    for patcher in patcher_names:
        results = results_per_patcher[patcher][language]
        if metric == Metric.Automation:
            values.append(100 * np.mean(results.get(metric)))
        else:
            values.append(np.mean(results.get(metric)))

    if best_type == "max":
        return max(values)
    elif best_type == "min":
        return min(values)
