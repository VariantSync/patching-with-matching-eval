from data import PatchStrategy
from data import Experiment


table_header = """
\\begin{table*}
    \\caption{Sync-Study results for all datasets.}
    \\small
    \\begin{tabular}{|l|l||c|c|c|c|c||c|c||c|c|c|}
        \\hline
        \\multirow{2}{*}{Subject} & \\multirow{2}{*}{Approach} & Correct & Invalid & Filtered & Missing & Wrong & Mitigated & Mitigated & \\multirow{2}{*}{Precision} & \\multirow{2}{*}{Recall} & Balanced \\\\
        & & Patches (TP) & Patches (FP) & Patches (TN) & Patches (FN) & Location (FN) & Invalid & Missing & &  & Accuracy\\\\
        \\hline
        & & & & & & & & & & & \\\\
        \\hline
    """
table_end = """
\\end{tabular}
\\end{table*}
"""


def rq1_table(subjects: [str], experiments: [Experiment], outDir):
    latex_table = table_header

    for (subj, exp) in zip(subjects, experiments):
        n = exp.normal
        f = exp.filtered

        row = subj + " & " + generate_row(n)
        row += " & " + generate_row(f)
        row += "\n"
        latex_table += row

    latex_table += table_end

    print(latex_table)


def generate_row(data: PatchStrategy) -> str:
    row = data.name + " & "

    row += str(data.normed_applied()) + " & "
    row += str(data.normed_invalid()) + " & "
    row += str(data.normed_missing()) + " & "
    row += str(data.normed_filteredCorrectly()) + " & "
    row += str(data.normed_wrongLocation()) + " & "

    row += str(data.normed_mitigatedInvalid()) + " & "
    row += str(data.normed_mitigatedMissing()) + " & "

    row += str(data.precision()) + " & "
    row += str(data.recall()) + " & "
    row += str(data.balanced_accuracy()) + " \\\\ "

    return row
