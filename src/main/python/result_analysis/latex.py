import numpy as np

from result_analysis.eval_setup import Metric
from result_analysis.eval_setup import Patcher
from result_analysis.simulation import power_analysis_simulation


def generate_metrics_result_table(
    patcher_names, languages, results_per_patcher, differences, file
):
    language_names = [lang[1] for lang in languages]
    languages = [lang[0] for lang in languages]

    with open(file, "w") as file:
        fmt = "S[table-format=2.2]|" * (3 + len(languages))
        # Begin the tabular environment
        file.write("\\begin{tabular}{|l|c|" + fmt + "}\n")
        file.write("\\hline\n")

        # Write the multi-column header for languages
        language_header = "\\multirow{2}{*}{Metric} & \\multirow{2}{*}{Patcher} & \\multicolumn{10}{c|}{Project Languages} & \\multicolumn{1}{c|}{\\multirow{2}{*}{$\\overline{x}$}} & \\multicolumn{1}{c|}{\\multirow{2}{*}{$\\pm\\%$}} & \\multicolumn{1}{c|}{\\multirow{2}{*}{p}}\\\\\n"
        file.write(language_header)
        file.write("\\cline{3-" + str(len(languages) + 2) + "}\n")

        # Write the language names
        language_names = [name for name in language_names]
        for i in range(0, len(language_names)):
            if language_names[i] == "C#":
                language_names[i] = "C\\#"
        file.write(" & & " + " & ".join(language_names) + " & & & \\\\\n")
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
                    results = results_per_patcher[patcher][language].per_patch
                    value = np.mean(results.get(metric))
                    postfix = ""
                    if metric == Metric.Precision:
                        best_type = "max"
                    elif metric == Metric.Recall:
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

                    # p_value = 1.0
                    # if patcher in corrected_significance:
                    #    if language in corrected_significance[patcher]:
                    #        if metric in corrected_significance[patcher][language]:
                    #            p_value = corrected_significance[patcher][language][
                    #                metric
                    #            ]

                    if value == max_value:
                        line += f" & \\bfseries {value:.2f}{postfix}"
                    else:
                        line += f" & {value:.2f}{postfix}"

                (average, diff, p_value) = differences[patcher][metric]
                if p_value < 0.01:
                    color = "blue!25"
                elif p_value < 0.02:
                    color = "blue!20"
                elif p_value < 0.03:
                    color = "blue!15"
                elif p_value < 0.04:
                    color = "blue!10"
                elif p_value < 0.05:
                    color = "blue!05"
                else:
                    color = "white"
                diff *= 100
                if metric == Metric.Automation:
                    average *= 100
                if p_value == np.inf:
                    line += f" & {average:.2f} &  & "
                else:
                    line += f" & \\cellcolor{{{color}}}{average:.2f}"
                    line += f" & \\cellcolor{{{color}}}{diff:.2f}\\%"
                    line += f" & \\cellcolor{{{color}}}{p_value:.2f}"
                file.write(line + " \\\\\n")
            file.write("\\hline\n")

        # End the tabular environment
        file.write("\\end{tabular}")


def determine_best(results_per_patcher, patcher_names, metric, best_type, language):
    values = []
    for patcher in patcher_names:
        results = results_per_patcher[patcher][language].per_patch
        if metric == Metric.Automation:
            values.append(100 * np.mean(results.get(metric)))
        else:
            values.append(np.mean(results.get(metric)))

    if best_type == "max":
        return max(values)
    elif best_type == "min":
        return min(values)


def generate_power_estimate_table(
    patcher_names, languages, results_per_patcher, corrected_alpha, file
):
    with open(file, "w") as file:
        fmt = "S[table-format=2.2]|" * (3 + len(languages))
        # Begin the tabular environment
        file.write("\\begin{tabular}{|l|" + fmt + "}\n")
        file.write("\\hline\n")

        # Write the multi-column header for languages
        language_header = (
            "Metric & \\multicolumn{"
            + str(len(languages))
            + "}{c|}{Project Languages} & Average Power\\\\\n"
        )
        file.write(language_header)
        file.write("\\cline{2-" + str(len(languages) + 1) + "}\n")

        # Write the language names
        language_names = [language for language in languages]
        for i in range(0, len(language_names)):
            if language_names[i] == "C#":
                language_names[i] = "C\\#"
        file.write(" & " + " & ".join(language_names) + " & \\\\\n")
        file.write("\\hline\n")

        # Parameters for the power analysis
        num_simulations = 1000
        num_datasets = len(languages)
        num_patchers = len(patcher_names)
        our_patcher = Patcher.MPatch.nice_name()
        # Write the multi-rows and their corresponding rows
        for metric in Metric:
            line = metric.nice_name()
            n = 0
            effect_sizes = []
            deviations = []
            for language in results_per_patcher[our_patcher]:
                results_ours = results_per_patcher[our_patcher][language]
                values_ours = results_ours.get(metric)
                mean_ours = np.mean(values_ours)
                n = len(values_ours)

                e = []
                d = [np.std(values_ours)]
                for patcher in patcher_names:
                    if patcher == our_patcher:
                        continue

                    results_other = results_per_patcher[patcher][language]
                    values_other = results_other.get(metric)
                    mean_other = np.mean(values_other)
                    e.append(np.abs(mean_ours - mean_other))
                    d.append(np.std(values_other))
                effect_sizes.append(e)
                deviations.append(d)

            # Perform the power analysis using the corrected significance level
            deviations = np.array(deviations)
            effect_sizes = np.array(effect_sizes)
            print("\nStarting power estimation for " + str(metric))
            estimated_power = power_analysis_simulation(
                num_simulations,
                num_datasets,
                num_patchers,
                n,
                effect_sizes,
                deviations,
                corrected_alpha,
                distribution="normal",
            )
            for language, power in zip(languages, estimated_power):
                line += " & " + str(100 * np.min(power))
            average_power = 100 * np.mean(estimated_power)
            line += " & " + str(average_power)

            file.write(line + " \\\\\n")
        file.write("\\hline\n")

        # End the tabular environment
        file.write("\\end{tabular}")
        print("Saved power table")
