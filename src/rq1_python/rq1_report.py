import yaml
import glob
import os
import pandas as pd
import io
import matplotlib as mpl
import matplotlib.pyplot as plt
import matplotlib.font_manager as font_manager

pd.set_option('display.expand_frame_repr', False)  # Avoid line breaks in long columns
pd.set_option('display.max_columns', None)         # Display all columns

yaml_folder = "../../simulation-files/data/cherries/"
language_count = 10
sample_per_language = 250

green, blue, orange = '#2ca02c', '#1f77b4', '#ff7f0e'
colors = [green, blue, orange]

text_width = 7.1413
column_width = 3.48761

c_commits = "total_number_of_commits"
c_language = "language"
c_cherries = "total_number_of_results"
c_cherry_ratio = "cherry_to_commit_ratio"

plot_labels = {c_commits:"\#Commits", c_language:"Language", c_cherries:"\#Cherrypicks", c_cherry_ratio:"$\frac{\#Cherrypicks}{\#Commits}$"}

#returns header(main info about project), cherries of yaml
def read_cherry(f):
    with open(f, 'r', encoding='utf-8') as file:
        start_stream = "".join(file.readlines()[:4])
        cherries = yaml.safe_load(start_stream)
        project_info = cherries[0]
        project_info[c_language], project_info[c_commits], project_info[c_cherries] = project_info[c_language][1:-1], int(project_info[c_commits]), int(project_info[c_cherries])
        return project_info

def read_yamls(files):
    #first define (headers for) dataFrames
    header = read_cherry(files[0])
    pr_df, ch_df = pd.DataFrame(columns=list(header.keys())), pd.DataFrame

    #read and append main project info and cherries for each file to their respective data_frames
    for e, f in enumerate(files):
        short_f = f.replace('\\', '/').split('/')[-1]
        print(f"{e}: {short_f}")
        header = read_cherry(f)

        pr_df = pr_df.append(header, ignore_index=True)

    #add new columns of interest
    pr_df[c_cherry_ratio] = pr_df[c_cherries] / pr_df[c_commits]
    return pr_df, ch_df

def custom_format(x):
    if x < 10:
        return f"{x:.3g}"
    else:
        return round(x)

def column_report(lang, column_description, column, df):
    quantiles = [0, 0.25, 0.5, 0.75, 1]
    quantile_list = list(df[column].quantile(quantiles))
    formatted_list = [custom_format(x) for x in quantile_list]
    print(f"{lang}, for column \"{column_description}\" the quantiles ({quantiles} chosen) are: {formatted_list}")

def report_projects(pr_df):
    num_languages = len(pr_df[c_language].unique())
    if num_languages > 1:
        lang = "ALL"
        print(f"\n\nGiving report for {lang} {num_languages} languages:")
    else:
        lang = pr_df[c_language].iloc[0]
        print(f"\n\nNow Reporting for language {lang}:")


    print(f"{lang}, number of repositories with cherries: {len(pr_df)}, and without cherries: {num_languages*sample_per_language-len(pr_df)}, ratio: {custom_format(len(pr_df)/(num_languages*sample_per_language))}.")
    print(f"{lang}, total number of commits, within all projects {'' if num_languages > 1 else 'of '+lang}: {sum(pr_df[c_commits])}")
    print(f"{lang}, total number of cherries, within all projects {'' if num_languages > 1 else 'of '+lang}: {sum(pr_df[c_cherries])}, mean cherry to commit ratio: {custom_format(sum(pr_df[c_cherries])/sum(pr_df[c_commits]))}")
    column_report(lang, c_cherries, c_commits, pr_df)
    column_report(lang, c_commits, c_cherries, pr_df)
    column_report(lang, c_cherry_ratio, c_cherry_ratio, pr_df)

    if len(pr_df[c_language].unique()) > 1:
        min_denominator = 5
        min_ratio = 1/min_denominator
        extreme_projects = pr_df[pr_df[c_cherry_ratio] > min_ratio].sort_values(by=c_cherry_ratio)
        print(f"Most extreme projects, with more than 1 in {min_denominator} (>{min_ratio}) commits being a cherry-pick:")
        print(extreme_projects)
        print(f"Most extreme projects, with most total cherry picks:")
        print(pr_df.sort_values(by=c_cherries).iloc[-10:])
    print(f"Report end for {lang}.")
    pass

def setup_plt():
    path_font = "C:\\Users\\lanpi\\AppData\\Local\\Microsoft\\Windows\\Fonts\\cmunrm.ttf"
    font_manager.fontManager.addfont(path_font)
    prop = font_manager.FontProperties(fname=path_font)
    plt.rcParams['font.family'] = prop.get_name()
    plt.rc('font', size=10)
    plt.rc('legend', fontsize=10)
    plt.rc('text', usetex=True)

    fig, ax = plt.subplots(figsize=(3.3374, 2.5))
    return fig, ax

def plot_projects(df):
    setup_plt()
    plt.scatter(df[c_commits], df[c_cherries], s=1, color=colors[0])


if __name__ == '__main__':
    yml_files = [f for f in glob.glob(os.path.join(yaml_folder, '**', '*.yaml'), recursive=True)]
    pr_df, ch_df = read_yamls(yml_files)
    report_projects(pr_df)
    plot_projects(pr_df)
    for language in pr_df[c_language].unique():
        report_projects(pr_df[pr_df[c_language] == language])
