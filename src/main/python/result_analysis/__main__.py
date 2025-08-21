from result_analysis.tables import (
    find_example,
    metrics_table_generation,
    patch_sizes,
    venn_diagram,
    direct_runtime_comparison,
)
from result_analysis.analyze_results import find_outliers
from rq3_report import rq3_analysis

import argparse


def main(repo_sample, results_dir, metrics_file):
    metrics_table_generation(
        results_dir,
        repo_sample,
        only_non_trivial=False,
        file_metrics=metrics_file,
        file_power="",
    )


def example(results_dir, repo_sample):
    find_example(results_dir + "rep-1/", repo_sample, False)


def outliers(results_dir, repo_sample):
    find_outliers(results_dir + "rep-1/", repo_sample, False)


def compare(results_dir, repo_sample):
    venn_diagram(results_dir + "rep-1/", repo_sample, False)


def sizes(results_dir, repo_sample):
    patch_sizes(results_dir + "rep-1/", repo_sample)


def runtime(results_dir, repo_sample):
    direct_runtime_comparison(results_dir + "rep-1/", repo_sample, False)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description="Run evaluation scripts with specified paths."
    )
    parser.add_argument(
        "--repo_sample", required=True, help="Path to the repo sample YAML file"
    )
    parser.add_argument(
        "--results_dir", required=True, help="Path to the results directory"
    )
    parser.add_argument(
        "--metrics_file", required=True, help="Path to the metrics output file"
    )
    args = parser.parse_args()
    main(
        args.repo_sample,
        args.results_dir,
        args.metrics_file,
    )
