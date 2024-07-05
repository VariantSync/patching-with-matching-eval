from .eval_setup import OutcomeClassification
from .eval_setup import Patcher
from .io import find_results_for_patcher
from .io import read_results_from_file
from .io import load_repositories_from_yaml


if __name__ == "__main__":
    directory_path = '/home/alex/data/cherry-picks/results/'
    total_results = 0
    summed_result = OutcomeClassification()
    for file_path in find_results_for_patcher(directory_path, Patcher.MPatch):
        result_objects = read_results_from_file(file_path)
        print("Read " + str(len(result_objects)) + " results.")
        total_results += len(result_objects)
        summed_result.add_result(result_objects[0].outcome_classification)
    print("Total: " + str(total_results))
    print("summed result: \n" + str(summed_result))

    repo_sample = '../../../simulation-files/data/repo-sample.yaml'
    load_repositories_from_yaml(repo_sample)
