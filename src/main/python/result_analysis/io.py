import json
import os
import yaml
from typing import Dict
from result_analysis.eval_setup import Patcher
from result_analysis.eval_setup import PatchResult
from result_analysis.eval_setup import Repository


def load_repositories(path_to_yaml: str) -> Dict[str, Repository]:
    with open(path_to_yaml, 'r') as file:
        yaml_content = yaml.safe_load(file)

    repos = {}
    for repo_data in yaml_content:
        repos[repo_data["name"]] = Repository(
            id=repo_data["id"],
            name=repo_data["name"],
            language=repo_data["language"])
    return repos


def load_all_results(directory_path, patcher: Patcher):
    results = []
    for file_path in find_results_for_patcher(directory_path, patcher):
        results.extend(read_results_from_file(file_path))
    return results


def read_results_from_file(file_path) -> []:
    """Out result files contain lists of JSON objects that are separated by
    blank lines, e.g.,:
    ```json
    {"key":"val",...}


    {"key":"val",...}
    ```
    """
    results = []
    with open(file_path, 'r') as file:
        file_content = file.read().strip()
        # Split the content by blank lines to get the JSON objects
        json_strings = file_content.split('\n\n')
        for json_str in json_strings:
            try:
                obj = json.loads(json_str)
                results.append(PatchResult(obj))
            except json.JSONDecodeError as e:
                print(f"Error decoding JSON from file {file_path}: {e}")

    return results


def find_files_by_postfix(directory_path, postfix: str) -> []:
    """Find all files in the given directory that end
    with the given postfix."""
    files = []
    for filename in os.listdir(directory_path):
        if filename.endswith(postfix):
            file_path = os.path.join(directory_path, filename)
            files.append(file_path)
    return files


def find_results_for_patcher(directory_path, patcher: Patcher) -> []:
    """Find all result files in the given directory for the given patcher"""
    postfix = str(patcher) + ".results"
    return find_files_by_postfix(directory_path, postfix)
