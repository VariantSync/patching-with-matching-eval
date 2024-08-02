import yaml
import glob
import os

repo_sample_file = "../../../../../evaluation-workdir/data/repo-sample.yaml"
yamls_folder = "../../../../../evaluation-workdir/data/cherries/"
GITHUB_API_URL = "https://api.github.com"
# with open("access_token", "r") as file:
#    ACCESS_TOKEN = file.read()

if __name__ == "__main__":
    with open(repo_sample_file, "r", encoding="utf-8") as file:
        repo_info = yaml.safe_load(file)
    yml_files = [
        f for f in glob.glob(os.path.join(yamls_folder, "**", "*.yaml"), recursive=True)
    ]

    pass

