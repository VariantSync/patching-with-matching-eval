#! /bin/bash

start() {
    echo "User id: $(id -u)"
    echo "Group id: $(id -g)"

    mkdir /home/user/evaluation-workdir/tables

    if [ "$1" == 'reproduction' ]; then
        echo "Running full reproduction of evaluation on the entire patch dataset."
        ls -l
        java -jar -Dtinylog.configuration=/home/user/tinylog.properties cherries.jar config-reproduction.properties

        analysis $1
    elif [ "$1" == 'verification' ]; then
        echo "Verifying the evaluation setup on a tiny subset of the patch dataset."
        ls -l
        java -jar -Dtinylog.configuration=/home/user/tinylog.properties cherries.jar config-verification.properties

        analysis $1
    elif [ "$1" == 'cleanup' ]; then
        echo "Running cleanup of old result files."
        rm -r /home/user/evaluation-workdir/results/*
        rm -r /home/user/evaluation-workdir/main/*
        rm -r /home/user/evaluation-workdir/tables/*
    elif [ "$1" == 'analysis' ]; then
        if [ "$2" == '' ]; then
            echo "missing second argument"
            echo "./execute.sh analysis [reproduction|verification]   # Run a quick verification of the setup"
        else
            analysis $2
        fi
    else
        echo "Invalid argument: $1"
    fi
}

analysis() {
    cd /home/user/analysis
    poetry run python result_analysis/__main__.py --results_dir /home/user/evaluation-workdir/results/"$1" --repo_sample /home/user/dataset/repo-sample.yaml --metrics_file /home/user/metrics-"$1".tex

    cd /home/user/
    latexmk -pdf -interaction=nonstopmode -synctex=1 -shell-escape metrics-$1.tex
    cp metrics-$1.pdf evaluation-workdir

    echo "++++++++++++++++++++++++++++++++++++"
    echo "          Analysis done             "
    echo "++++++++++++++++++++++++++++++++++++"

    echo ""
    echo "The result table can be found under evaluation-workdir/metrics-$1.pdf"
}

if [ "$1" == '' ]; then
    echo "Argument required. The following options are available:"
    echo "./execute.sh reproduction   # Reproduce the evaluation"
    echo "./execute.sh verification   # Run a quick verification of the setup"
    echo "./execute.sh analysis [reproduction|verification]   # Run a quick verification of the setup"
    echo "./execute.sh cleanup        # Clean the evaluation files"
    exit
else
    start $1
fi
