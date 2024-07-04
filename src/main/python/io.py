import json
import os
from eval_setup import Patcher
from eval_setup import PatchResult


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


if __name__ == "__main__":
    directory_path = '/home/alex/data/cherry-picks/results/'
    total_results = 0
    for file_path in find_results_for_patcher(directory_path, Patcher.MPatch):
        result_objects = read_results_from_file(file_path)
        print("Read " + str(len(result_objects)) + " results.")
        total_results += len(result_objects)
    print("Total: " + str(total_results))
