import json
import os
import yaml
from typing import List
from eval_setup import Patcher
from eval_setup import PatchResult
from eval_setup import Repository


def load_repositories_from_yaml(path_to_yaml: str) -> List[Repository]:
    with open(path_to_yaml, 'r') as file:
        yaml_content = yaml.safe_load(file)
    return [Repository(**repo_data) for repo_data in yaml_content]


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
