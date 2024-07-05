import json
import re
import data


REGEX_JSON_FIELD = r"^(.*)\s*:\s*([^,]*)\s*(,)?\s*$"
REGEX_PATTERN_JSON_FIELD = re.compile(REGEX_JSON_FIELD)


def parse_json_and_add_to_experiment(json_object, experiment):
    outcome = json.loads(json_object)

    experiment.normal.applied += int(outcome['normalApplied'])
    experiment.normal.invalid += int(outcome['normalInvalid'])
    experiment.normal.wrong_location += int(outcome['normalWrongLocation'])
    experiment.normal.missing += int(outcome['normalMissing'])
    experiment.normal.filtered_correctly += int(
        outcome['normalFilteredCorrectly'])
    experiment.normal.filtered_incorrectly += int(
        outcome['normalFilteredIncorrectly'])
    experiment.normal.mitigated_invalid += int(
        outcome['normalMitigatedInvalid'])
    experiment.normal.mitigated_missing += int(
        outcome['normalMitigatedMissing'])

    experiment.filtered.applied += int(outcome['filteredApplied'])
    experiment.filtered.invalid += int(outcome['filteredInvalid'])
    experiment.filtered.wrong_location += int(outcome['filteredWrongLocation'])
    experiment.filtered.missing += int(outcome['filteredMissing'])
    experiment.filtered.filtered_correctly += int(
        outcome['filteredFilteredCorrectly'])
    experiment.filtered.filtered_incorrectly += int(
        outcome['filteredFilteredIncorrectly'])
    experiment.filtered.mitigated_invalid += int(
        outcome['filteredMitigatedInvalid'])
    experiment.filtered.mitigated_missing += int(
        outcome['filteredMitigatedMissing'])

    experiment.normal.commit_patches = experiment.normal.commit_patches + 1
    experiment.filtered.commit_patches = experiment.filtered.commit_patches + 1
    if outcome['lineSuccessNormal'] == outcome['lineNormal']:
        experiment.normal.commit_success = experiment.normal.commit_success + 1
    if outcome['lineSuccessFiltered'] == outcome['lineFiltered']:
        experiment.filtered.commit_success = experiment.filtered.commit_success + 1

    experiment.normal.file += int(outcome['fileNormal'])
    experiment.normal.file_success += int(outcome['fileSuccessNormal'])
    experiment.filtered.file += int(outcome['fileFiltered'])
    experiment.filtered.file_success += int(outcome['fileSuccessFiltered'])

    experiment.normal.line += int(outcome['lineNormal'])
    experiment.normal.line_success += int(outcome['lineSuccessNormal'])
    experiment.filtered.line += int(outcome['lineFiltered'])
    experiment.filtered.line_success += int(outcome['lineSuccessFiltered'])


def parse_file_at(path):
    experiment = data.Experiment()  # type: Experiment

    with open(path) as file:
        json_object = ""
        # This reads lines lazily according to https://stackoverflow.com/questions/519633/lazy-method-for-reading-big-file-in-python
        for line in file:
            stripped = line.strip()
            # print("PARSE", stripped)
            if len(stripped) == 0:
                # print("CONSUME")
                parse_json_and_add_to_experiment(json_object, experiment)
                json_object = ""
            else:
                # print("ADD")
                if stripped != "{" and stripped != "}":
                    match = REGEX_PATTERN_JSON_FIELD.match(stripped)
                    key = match.group(1)
                    val = match.group(2)
                    comma = match.group(3)
                    if not val.isdigit():
                        stripped = key + ": \"" + val + "\""
                        if comma != None:  # If there is a comma
                            stripped += ","
                stripped += "\n"

                json_object += stripped

        if len(json_object) > 0:
            parse_json_and_add_to_experiment(json_object, experiment)

    return experiment
