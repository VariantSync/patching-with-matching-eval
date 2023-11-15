from data import PatchStrategy
from data import Experiment


table_header = """
\\begin{table*}
    \\caption{Sync-Study results for all datasets.}
    \\footnotesize
    \\begin{tabular}{|l|l||c|c|c|c|c||c|c||c|c|c|}
        \\hline
        \\multirow{2}{*}{Subject} & \\multirow{2}{*}{Approach} & Correct & Invalid & Filtered & Missing & Wrong & Mitigated & Mitigated & \\multirow{2}{*}{Precision} & \\multirow{2}{*}{Recall} & Balanced \\\\
        & & Patches (TP) & Patches (FP) & Patches (TN) & Patches (FN) & Location (FN) & Invalid & Missing & &  & Accuracy\\\\
        \\hline
    """
table_end = """
\\hline
\\end{tabular}
\\end{table*}
"""


def rq1_table(subjects: [str], experiments: [Experiment], outDir):
    latex_table = table_header

    for (subj, exp) in zip(subjects, experiments):
        n = exp.normal
        f = exp.filtered

        row = subj + " & " + generate_row(n)
        row += "\n"
        row += " & " + generate_row(f)
        row += "\n"
        latex_table += row
        latex_table += "\\hline \n"

    latex_table += table_end

    print(latex_table)


def generate_row(data: PatchStrategy) -> str:
    row = data.name + " & "

    percentages = [
        data.normed_applied(),
        data.normed_invalid(),
        data.normed_filtered_correctly(),
        data.normed_missing(),
        data.normed_wrong_location(),
        data.normed_mitigated_invalid(),
        data.normed_mitigated_missing(),
    ]
    metrics = [
        data.precision(),
        data.recall(),
        data.balanced_accuracy()
    ]

    row += " & ".join("{:6.2f}\\%".format(field)
                      for field in percentages) + " & "

    row += " & ".join("{:1.2f}".format(field)
                      for field in metrics) + " \\\\ "
    return row
