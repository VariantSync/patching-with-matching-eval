#! /bin/bash

start() {
    echo "User id: $(id -u)"
    echo "Group id: $(id -g)"

    mkdir /home/user/evaluation-workdir/tables

    if [ "$1" == 'reproduction' ]; then
        echo "Running full reproduction of evaluation on the entire patch dataset."
        ls -l
        java -jar -Dtinylog.configuration=/home/user/tinylog.properties cherries.jar config-reproduction.properties
        cd /home/user/analysis
        poetry run python result_analysis/__main__.py --results_dir /home/user/evaluation-workdir/results/reproduction --repo_sample /home/user/dataset/repo-sample.yaml --metrics_file /home/user/evaluation-workdir/metrics.tex
    elif [ "$1" == 'verification' ]; then
        echo "Verifying the evaluation setup on a tiny subset of the patch dataset."
        ls -l
        java -jar -Dtinylog.configuration=/home/user/tinylog.properties cherries.jar config-verification.properties
        cd /home/user/analysis
        poetry run python result_analysis/__main__.py --results_dir /home/user/evaluation-workdir/results/verification --repo_sample /home/user/dataset/repo-sample.yaml --metrics_file /home/user/evaluation-workdir/metrics.tex
    elif [ "$1" == 'cleanup' ]; then
        echo "Running cleanup of old result files."
        rm -r /home/user/evaluation-workdir/results/*
        rm -r /home/user/evaluation-workdir/main/*
        rm -r /home/user/evaluation-workdir/tables/*
    elif [ "$1" == 'analysis' ]; then
        cd /home/user/analysis
        poetry run python result_analysis/__main__.py --results_dir /home/user/evaluation-workdir/results/reproduction --repo_sample /home/user/dataset/repo-sample.yaml --metrics_file /home/user/evaluation-workdir/metrics.tex
    else
        echo "Invalid argument: $1"
    fi
}

if [ "$1" == '' ]; then
    echo "Argument required. The following options are available:"
    echo "./execute.sh reproduction   # Reproduce the evaluation"
    echo "./execute.sh verification   # Run a quick verification of the setup"
    echo "./execute.sh cleanup        # Clean the evaluation files"
    exit
else
    start $1
fi
