import numpy as np

from result_analysis.eval_setup import Metric


def generate_metrics_result_table(
    patcher_names, languages, results_per_patcher, differences, file
):
    language_names = [lang[1] for lang in languages]
    languages = [lang[0] for lang in languages]

    with open(file, "w") as file:
        fmt = "S[table-format=2.2]" * (4 + len(languages))
        # Begin the tabular environment
        file.write("\\begin{tabular}{lc" + fmt + "}\n")
        file.write("\\toprule\n")

        # Write the multi-column header for languages
        language_header = " & & \\multicolumn{10}{c}{Project Languages} & & &\\\\\n"
        file.write(language_header)
        file.write("\\cline{3-" + str(len(languages) + 2) + "}\n")

        # Write the language names
        language_names = [name for name in language_names]
        for i in range(0, len(language_names)):
            if language_names[i] == "C#":
                language_names[i] = "C\\#"
        file.write(
            "Metric & Patcher & "
            + " & ".join(language_names)
            + " & \\multicolumn{1}{c}{$\\overline{x}$} & \\multicolumn{1}{c}{$\\pm\\%$} & \\multicolumn{1}{c}{$|r_{RB}|$} \\\\\n"
        )

        # Write the multi-rows and their corresponding rows
        file.write("\\toprule\n")
        for metric in Metric:
            if metric != Metric.F1Score:
                file.write("\\midrule\n")
            file.write(
                "\\multirow{"
                + str(len(results_per_patcher))
                + "}{*}{"
                + metric.nice_name()
                + "}\n"
            )
            best_average = determine_best_average(differences, patcher_names, metric)
            for patcher in patcher_names:
                line = " & " + patcher
                for language in languages:
                    value = 0
                    best_type = ""
                    results = results_per_patcher[patcher][language].per_patch
                    value = np.nanmean(results.get(metric))
                    postfix = ""
                    if metric == Metric.F1Score:
                        best_type = "max"
                    elif metric == Metric.Automation:
                        value = 100 * value
                        best_type = "max"
                        # postfix = "\\%"
                    elif metric == Metric.EditDistance:
                        best_type = "min"
                    elif metric == Metric.Runtime:
                        best_type = "min"
                        # postfix = "s"
                    else:
                        value = -1

                    max_value = determine_best(
                        results_per_patcher, patcher_names, metric, best_type, language
                    )

                    if value == max_value:
                        line += f" & \\bfseries {value:.2f}{postfix}"
                    else:
                        line += f" & {value:.2f}{postfix}"

                (average, diff, p_value, effect) = differences[patcher][metric]
                effect = np.abs(effect)

                if metric == Metric.Automation:
                    average *= 100
                    best_average *= 100

                if average == best_average:
                    average_text = f"\\bfseries {average:.2f}"
                else:
                    average_text = f"{average:.2f}"

                color = "white"
                diff *= 100
                if np.isnan(p_value):
                    line += f" & {average_text} &  & "
                else:
                    line += f" & \\cellcolor{{{color}}}{average_text}"
                    line += f" & \\cellcolor{{{color}}}{diff:.2f}\\%"
                    line += f" & \\cellcolor{{{color}}}{effect:.2f}"
                file.write(line + " \\\\\n")

        # End the tabular environment
        file.write("\\bottomrule\n")
        file.write("\\end{tabular}")


def determine_best(results_per_patcher, patcher_names, metric, best_type, language):
    values = []
    for patcher in patcher_names:
        results = results_per_patcher[patcher][language].per_patch
        if metric == Metric.Automation:
            values.append(100 * np.nanmean(results.get(metric)))
        else:
            values.append(np.nanmean(results.get(metric)))

    if best_type == "max":
        return max(values)
    elif best_type == "min":
        return min(values)


def determine_best_average(differences, patcher_names, metric):
    values = []
    for patcher in patcher_names:
        results = differences[patcher][metric][0]
        values.append(results)

    if metric == Metric.F1Score:
        best_type = "max"
    elif metric == Metric.Automation:
        best_type = "max"
    elif metric == Metric.EditDistance:
        best_type = "min"
    elif metric == Metric.Runtime:
        best_type = "min"
    else:
        exit(-1)

    if best_type == "max":
        return np.nanmax(values)
    elif best_type == "min":
        return np.nanmin(values)
