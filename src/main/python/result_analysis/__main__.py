from result_analysis.misc_analyses import print_result_data
from result_analysis.misc_analyses import load_repos
from result_analysis.misc_analyses import print_results_per_repo
from result_analysis.misc_analyses import print_results_per_language
from sys import argv


def main():
    print("This is the entry point of the package.")
    print("Number of minimum results per repo to consider: " + argv[1])
    min = int(argv[1]) if argv[1] is not None else 10
    print_results_per_language(min)


if __name__ == "__main__":
    main()
