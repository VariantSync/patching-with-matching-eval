#! /bin/bash

cherries() {
    echo "Running evaluation on cherry picks."
    echo "User id: $(id -u)"
    echo "Group id: $(id -g)"

    if [ "$1" == 'replication' ]; then
        java -jar -Dtinylog.configuration=/home/user/tinylog.properties cherries.jar config-reproduction.properties
        java -jar result-analysis-cherries.jar config-reproduction.properties
    elif [ "$1" == 'composition' ]; then
        java -jar -Dtinylog.configuration=/home/user/tinylog.properties composition.jar config-reproduction.properties
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
    echo "./execute.sh replication"
    exit
else
    cherries $1
fi
