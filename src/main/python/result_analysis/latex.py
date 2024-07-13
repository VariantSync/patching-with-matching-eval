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
                    results = results_per_patcher[patcher][language]
                    if metric == "Precision":
                        value = results.precision
                    elif metric == "Recall":
                        value = results.recall
                    elif metric == "Automation":
                        value = results.patch_automation
                    elif metric == "Edit Distance":
                        value = results.avg_edit_distance
                    elif metric == "Runtime":
                        value = results.avg_runtime
                    else:
                        value = -1
                    line += " & " + f"{value:.2f}"
                file.write(line + " \\\\\n")
            file.write("\\hline\n")

        # End the tabular environment
        file.write("\\end{tabular}")
