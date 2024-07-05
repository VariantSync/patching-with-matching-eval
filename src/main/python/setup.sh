#!/bin/bash

ENV_NAME=".env"

# Command to initialize local python environment and install requirements
init_env() {
    python3 -m venv $ENV_NAME

    echo "activate environment"
    source $ENV_NAME/bin/activate

    if [ -f "result_analysis/requirements.txt" ]; then
        pip install -r result_analysis/requirements.txt
    else
        echo "requirements.txt not found!"
    fi
}

init_env
