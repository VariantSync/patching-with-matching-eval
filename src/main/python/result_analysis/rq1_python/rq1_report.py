import yaml
import glob
import os
import pandas as pd
import numpy as np
import io
import matplotlib as mpl
import matplotlib.pyplot as plt
import matplotlib.font_manager as font_manager
from scipy.stats import spearmanr
import math
import json

try:
    from yaml import CLoader as Loader
except ImportError:
    from yaml import Loader

pd.set_option('display.expand_frame_repr', False)  # Avoid line breaks in long columns
pd.set_option('display.max_columns', None)         # Display all columns

yaml_folder = "../../evaluation-workdir/data/cherries/"
repo_sample_yaml = "../../evaluation-workdir/data/repo-sample.yaml"
language_count = 10
sample_per_language = 500

min_denominator = 5     #projects with: "every many-th commit is a cherrypick or more" are noteworthy
min_ratio = 1/min_denominator

green, blue, orange = '#2ca02c', '#1f77b4', '#ff7f0e'
colors = [green, blue, orange]

text_width = 7.1413
column_width = 3.48761
fontsize = 10

c_commits = "total_number_of_commits"
c_commiters = "total_number_of_committers"
c_language = "language"
c_cherries = "total_number_of_results"
c_cherry_ratio = "cherry_to_commit_ratio"
c_repo_name = "repo_name"
c_forks = 'forks'
c_branches = 'total_number_of_branches'
c_stars = 'stars'
c_trivial_cherries = 'trivial_cherries'
c_sampled_cherries = 'sampled_cherries'
c_sampled_projects_per_language = "projects"
c_projects_with_cherries = "projects_with_cherries"

#directly from AlexS, no automatism, yet
sampled_cherry_list = [9227,1405,11215,5522,8375,698,2533,5840,1979,3530]

table_names = {c_repo_name:"repository", "rq_git_cherry":"{\mymakecell{required \\\\ fixes \\\\ \\gitcherrypickshort}}", "rq_pwm_f2":"{\mymakecell{required \\\\ fixes \\\\ \\approach}}", "ap_git_cherry":"{\mymakecell{fully \\\\ automatable \\\\ \\gitcherrypickshort{} \%}}", "ap_pwm_f2":"{\mymakecell{fully \\\\ automatable \\\\ \\approach{} \%}}", c_projects_with_cherries:"{\mymakecell{projects \\\\ with \\\\ cherry \\\\ picks}}", c_language:"{\mymakecell[l]{main \\\\ repository \\\\ language}}", c_sampled_projects_per_language:"{\mymakecell[l]{sampled \\\\ projects}}", c_cherries:"{\mymakecell{cherry \\\\ picks}}", c_cherry_ratio:"{\mymakecell{cherry \\\\ pick \%}}", c_trivial_cherries:"{\mymakecell{complex \\\\ cherry \\\\ pick \%}}", c_sampled_cherries:'{\mymakecell{sampled \\\\ cherry \\\\ picks}}'}

c_total = "total"
table_pos = "!tb"

plot_labels = {c_commits:"\#Commits", c_language:"Language", c_cherries:"\#Cherrypicks", c_cherry_ratio:"$\frac{\#Cherrypicks}{\#Commits}$"}


def add_midrule(latex_str: str, index: int = 0) -> str:
    lines = latex_str.splitlines()
    if not index:
        index = len(lines) - 4
    else:
        index = len(lines) - index
    lines.insert(index, r'\midrule')
    return '\n'.join(lines)

def add_total_row(df):
    df.loc[c_total] = df.sum()
    return df

# useful for DataFrames
# possibly adds a bottom total row, with its own \midrule
def to_latex(df, float_format=None, label="tab:???", caption="???", position=table_pos, index=True, add_total=False, add_mean=False, two_column=False, column_format=None, csv_name="", addmr=False):
    if not csv_name:
        csv_name = label.split(":")[-1] + ".csv"
    else:
        csv_name += ".csv"
    df.to_csv(path_or_buf="." + csv_name, index_label=True)


    latex_str = df.to_latex(label=label, caption=caption, index=False, position=position, column_format=column_format, escape = False)  # .replace('NaN', '')

    if addmr:
        latex_str = add_midrule(latex_str)
    if two_column:
        latex_str = latex_str.replace('\\begin{table}', '\\begin{table*}')
        latex_str = latex_str.replace('\\end{table}', '\\end{table*}')
    file_name = "." + label.split(":")[-1]
    print(latex_str)
    with open(file_name, "w+") as f:
        f.write(latex_str)
    return latex_str




#returns header(main info about project), cherries of yaml
def read_cherry(f):
    with open(f, 'r', encoding='utf-8') as file:
        start_stream = "".join(file.readlines()[:6])
        cherries = yaml.load(start_stream, Loader=Loader)
        project_info = cherries[0]
        project_info[c_language], project_info[c_commits], project_info[c_cherries] = project_info[c_language], int(project_info[c_commits]), int(project_info[c_cherries])
        return project_info

def find_trivial_cherries(file_name):
    with open(file_name, 'r', encoding='utf-8') as file:
        lines = file.readlines()
    return sum([1 if l == "    is_trivial: true\n" else 0 for l in lines])

def read_yamls(files):
    #first define (headers for) dataFrames
    header = read_cherry(files[0])
    header[c_trivial_cherries] = np.nan
    pr_df, ch_df = pd.DataFrame(columns=list(header.keys())), pd.DataFrame

    #read and append main project info and cherries for each file to their respective data_frames
    for e, f in enumerate(files):
        short_f = f.replace('\\', '/').split('/')[-1]
        print(f"{e}: {short_f}")
        header = read_cherry(f)
        header[c_trivial_cherries] = find_trivial_cherries(f)

        pr_df = pr_df.append(header, ignore_index=True)

    #add new columns of interest

    with open(repo_sample_yaml, 'r', encoding='utf-8') as file:
        repo_sample_info = yaml.load(file, Loader=Loader)

    pr_df[c_forks] = np.nan
    pr_df[c_stars] = np.nan
    for i, repo in enumerate(repo_sample_info):
        row_num = pr_df[pr_df[c_repo_name] == repo['full_name']].index.values.astype(int)[0]
        pr_df.at[row_num, c_forks] = repo['forks_count']
        pr_df.at[row_num, c_stars] = repo['stargazers_count']
    pr_df[c_forks] = pr_df[c_forks].astype(int)
    pr_df[c_stars] = pr_df[c_stars].astype(int)
    return pr_df, ch_df

def column_report(lang, column_description, column, df):
    quantiles = [0, 0.25, 0.5, 0.75, 1]
    quantile_list = list(df[column].quantile(quantiles))
    formatted_list = [f"{x:.3g}" for x in quantile_list]
    print(f"{lang}, for column \"{column_description}\" the quantiles ({quantiles} chosen) are: {formatted_list}")

#this function does not handle extreme numbers like 0.0000000000000123
def get_siunit_column_format(df):
    column_header = ""
    for c in df:
        if pd.api.types.is_numeric_dtype(df[c]):
            before_dot = 0
            after_dot = 0
            for num in df[c]:
                f_num = f"{num:.3g}" if abs(num) < 1e3 else f"{num:.0f}"
                if f_num.__contains__("e"):
                    raise Exception("An error occurred: number too small or too big!")
                before_dot = max(before_dot, len(str(int(num))))
                if f_num.__contains__("."):
                    after_dot = max(after_dot, len(f_num.split(".")[1]))
            column_header += f'S[table-format={before_dot}.{after_dot}, round-precision={after_dot}]'
        else:
            column_header += 'l'
    return column_header


##projects,  #projects with cherry picks, #cherry picks, cherry pick %, complex cherry pick %, #sampled cherry picks
def df_to_latex(pr_df):
    df = pr_df.copy()
    df.iloc[:, 2:] = pr_df.iloc[:, 2:].astype(int, errors='ignore')
    df = df.groupby(c_language).sum().reset_index()
    df[c_trivial_cherries] = (1 - df[c_trivial_cherries] / df[c_cherries]) *100
    df[c_cherry_ratio] = df[c_cherries] / df[c_commits] *100
    df[c_sampled_cherries] = sampled_cherry_list
    df[c_sampled_projects_per_language] = sample_per_language
    df[c_projects_with_cherries] = pr_df[pr_df[c_cherries] > 0].groupby(c_language)[c_cherries].count().values

    c_pick_list = [c_language, c_sampled_projects_per_language, c_projects_with_cherries, c_cherries, c_cherry_ratio, c_trivial_cherries]
    df = df[c_pick_list]



    df.loc[c_total] = [c_total] + [df[p].sum() for p in c_pick_list if pd.api.types.is_numeric_dtype(df[p])]
    df.loc[c_total, c_cherry_ratio] = pr_df[c_cherries].sum() / pr_df[c_commits].sum() * 100
    df.loc[c_total, c_trivial_cherries] = (1 - pr_df[c_trivial_cherries].sum() / pr_df[c_cherries].sum()) *100

    df[c_language].replace('C#', 'C\#', inplace=True)
    df = df.rename(columns=table_names)


    to_latex(df, label="tab:overall", caption="Overview of our collection of GitHub projects.", position=table_pos, column_format=get_siunit_column_format(df), addmr=True)
    pass

def report_projects(pr_df):
    df_to_latex(pr_df)

    pr_df[c_cherry_ratio] = pr_df[c_cherries] / pr_df[c_commits]
    num_languages = len(pr_df[c_language].unique())
    if num_languages > 1:
        lang = "ALL"
        print(f"\n\nGiving report for {lang} {num_languages} languages:")
    else:
        lang = pr_df[c_language].iloc[0]
        print(f"\n\nNow Reporting for language {lang}:")

    repos_with_cherries = len(pr_df[pr_df[c_cherries] > 0])
    print(f"{lang}, number of repositories with cherries: {repos_with_cherries}, and without cherries: {num_languages*sample_per_language - repos_with_cherries}, ratio: {repos_with_cherries/(num_languages*sample_per_language)}.")
    print(f"{lang}, total number of commits, within all projects {'' if num_languages > 1 else 'of '+lang}: {sum(pr_df[c_commits])}")
    print(f"{lang}, total number of cherries, within all projects {'' if num_languages > 1 else 'of '+lang}: {sum(pr_df[c_cherries])}, mean cherry to commit ratio: {sum(pr_df[c_cherries])/sum(pr_df[c_commits]):.3g}")
    column_report(lang, c_commits, c_commits, pr_df)
    column_report(lang, c_cherries, c_cherries, pr_df)
    column_report(lang, c_cherry_ratio, c_cherry_ratio, pr_df)

    if len(pr_df[c_language].unique()) > 1:
        extreme_projects = pr_df[pr_df[c_cherry_ratio] > min_ratio].sort_values(by=c_cherry_ratio)
        print(f"Most extreme projects, with more than 1 in {min_denominator} (>{min_ratio}) commits being a cherry-pick:")
        print(extreme_projects)
        print(f"Most extreme projects, with most total cherry picks:")
        print(pr_df.sort_values(by=c_cherries).iloc[-10:])
    df = pr_df[pr_df[c_cherries] > 0]
    langs = list(df[c_language].unique())
    print(f"Project# distribution by language: {[(l, len(df[df[c_language] == l])) for l in langs]}")
    print(f"Report end for {lang}.")
    return pr_df

def setup_plt():
    path_font = "C:\\Users\\lanpi\\AppData\\Local\\Microsoft\\Windows\\Fonts\\cmunrm.ttf"
    font_manager.fontManager.addfont(path_font)
    prop = font_manager.FontProperties(fname=path_font)
    plt.rcParams['font.family'] = prop.get_name()
    plt.rc('font', size=fontsize)
    plt.rc('legend', fontsize=fontsize)
    plt.rc('text', usetex=True)

    fig, ax = plt.subplots(figsize=(3.3374, 2.5))
    return fig, ax

def plot_projects(df):
    setup_plt()
    plt.scatter(df[c_commits], df[c_cherries], s=1, color=colors[0])

def correlate(df):
    df = df.drop(columns=[c_repo_name, c_language])
    df = df.apply(pd.to_numeric, errors='coerce')
    corr = df.corr(method='spearman')
    fig, ax = plt.subplots(figsize=(column_width, column_width))
    plt.matshow(corr, fignum=0, vmin=-1, vmax=1)
    plt.colorbar(orientation="horizontal", anchor=(0.5, 2))
    plt.xticks(range(corr.select_dtypes(['number']).shape[1]), corr.select_dtypes(['number']).columns, rotation=90)
    plt.yticks(range(corr.select_dtypes(['number']).shape[1]), corr.select_dtypes(['number']).columns)

    for (i, j), val in np.ndenumerate(corr):
        if abs(val) >= 0.3:
            plt.text(j, i, f"{val:.2g}".lstrip("0").replace("-0", "-"), ha='center', va='center', color='white', fontsize=fontsize)

    plt.show()

def impact_file(file):
    rq = 0                                                               #total required fixes of file
    ap = 0                                                               #patches that were automatically executable
    seen = 0                                                             #seen patches (not skipped)
    with open(file, 'r', encoding='utf-8') as file:
        lines = file.readlines()
        for l in lines:
            if l == "\n":                                                #skip if empty line
                continue
            di = json.loads(l)
            nR = di["normalResult"]
            if nR["missing"] + nR["applied"] + nR["wrongLocation"] == 0:       #skip if nothing should have been done
                continue
            seen += 1
            rq += nR["wrongLocation"]*2 + nR["missing"] + nR["invalid"]    #required fixes + FP + FN
            if nR["wrongLocation"] + nR["missing"] + nR["invalid"] == 0: #nothing went wrong?, then it is automatically executable
                ap += 1
    return rq, ap/seen, seen

def impact_analysis(pr_df):
    approaches = ["git_cherry", "pwm_f2"]
    prefix = 'C:\\work\\patching-with-matching-eval\\evaluation-workdir\\results\\cherries\\rep-1\\'

    df = pr_df.sort_values(by=c_cherry_ratio)[-5:].append(pr_df.sort_values(by=c_cherries)[-5:])

    langs = list(df[c_language])
    projects = list(df[c_repo_name])
    pnames0 = [p.split("/")[0] for p in projects]
    pnames1 = [p.split("/")[1] for p in projects]

    idf = pd.DataFrame(columns=[c_repo_name] + ["rq_" + app for app in approaches] + ["ap_" + app for app in approaches])

    for i in range(len(pnames0)):
        idf.loc[len(idf)] = [projects[i]] + [np.nan]*len(approaches)*2
        for approach in approaches:
            file = prefix + f"{langs[i]}_{pnames0[i]}_{pnames1[i]}.yaml_{approach}.results"
            rq, ap, seen = impact_file(file)
            idf.loc[len(idf)-1, "rq_"+approach] = rq / seen
            idf.loc[len(idf)-1, "ap_" + approach] = ap*100
            #idf.loc[len(idf)-1, "seen"] = seen

    idf.insert(1, c_language, np.nan)
    idf.insert(2, c_cherry_ratio, np.nan)
    idf.insert(3, c_cherries, np.nan)
    idf.insert(4, c_trivial_cherries, np.nan)

    for index, row in idf.iterrows():
        idf.loc[index, c_language] = pr_df[pr_df[c_repo_name] == row[c_repo_name]][c_language].values[0]
        idf.loc[index, c_cherries] = pr_df[pr_df[c_repo_name] == row[c_repo_name]][c_cherries].values[0]
        idf.loc[index, c_cherry_ratio] = pr_df[pr_df[c_repo_name] == row[c_repo_name]][c_cherries].values[0] / pr_df[pr_df[c_repo_name] == row[c_repo_name]][c_commits].values[0]
        idf.loc[index, c_trivial_cherries] = pr_df[pr_df[c_repo_name] == row[c_repo_name]][c_cherries].values[0] - pr_df[pr_df[c_repo_name] == row[c_repo_name]][c_trivial_cherries].values[0]
    idf[c_cherries] = idf[c_cherries].astype(int, errors='ignore')
    idf[c_trivial_cherries] = idf[c_trivial_cherries].astype(int, errors='ignore')
    idf = idf.rename(columns={c_language:"{\mymakecell{main \\\\ language}}"})
    idf = idf.rename(columns={c_trivial_cherries:"{\mymakecell{complex \\\\ cherry \\\\ picks}}"})
    idf = idf.rename(columns=table_names)

    tl = to_latex(idf, label="tab:impact", caption="Potential impact for the projects with the most relative and most absolute cherry picks. We compare \\approach{} to \\gitcherrypick.", position=table_pos, column_format="llS[table-format=2.2, round-precision=2]S[table-format=5.0, round-precision=0]S[table-format=4.0, round-precision=0]S[table-format=2.1, round-precision=1]S[table-format=2.1, round-precision=1]S[table-format=2.1, round-precision=1]S[table-format=2.1, round-precision=1]")
    tl = tl.replace('JetBrains', '\t\midrule\n' + 'JetBrains')

    print(tl)
    return idf



if __name__ == '__main__':
    yml_files = [f for f in glob.glob(os.path.join(yaml_folder, '**', '*.yaml'), recursive=True)]
    pr_df, ch_df = read_yamls(yml_files)
    pr_df = report_projects(pr_df)
    impact_analysis(pr_df)

    correlate(pr_df)
    plot_projects(pr_df)
    for language in pr_df[c_language].unique():
        report_projects(pr_df[pr_df[c_language] == language])
