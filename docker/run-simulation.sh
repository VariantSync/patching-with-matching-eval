#! /bin/bash

start() {
    echo "User id: $(id -u)"
    echo "Group id: $(id -g)"

    if [ "$1" == 'reproduction' ]; then
        echo "Running full reproduction of evaluation on the entire patch dataset."
        java -jar -Dtinylog.configuration=/home/user/tinylog.properties cherries.jar config-reproduction.properties
    elif [ "$1" == 'verification' ]; then
        echo "Verifying the evaluation setup on a tiny subset of the patch dataset."
        java -jar -Dtinylog.configuration=/home/user/tinylog.properties cherries.jar config-verification.properties
    elif [ "$1" == 'cleanup' ]; then
        echo "Running cleanup of old result files."
        rm -r /home/user/evaluation-workdir/results/
        rm -r /home/user/evaluation-workdir/main/
        mkdir /home/user/evaluation-workdir/results
        mkdir /home/user/evaluation-workdir/main
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
