#! /bin/bash

sync_study() {
	echo "Running evaluation on SPL subjects."
	if [ "$1" == 'replication' ]; then
		java -jar -Dtinylog.configuration=/home/user/tinylog.properties synchronization-study.jar config-replication.sync.properties
		if [ $? -eq 1 ]; then
			mkdir /home/user/simulation-files/results/ERROR
			cp -r /home/user/simulation-files /home/user/simulation-files/ERROR/
			cp -r /home/user/TARGET /home/user/simulation-files/results/ERROR/
		fi
		java -jar result-analysis-sync-study.jar config-replication.sync.properties
	elif [ "$1" == 'validation' ]; then
		echo "Running a (hopefully) short validation of the installation."
		echo ""
		echo ""
		java -jar -Dtinylog.configuration=/home/user/tinylog.properties synchronization-study.jar config-validation.sync.properties
		if [ $? -eq 1 ]; then
			mkdir /home/user/simulation-files/results/ERROR
			cp -r /home/user/simulation-files/main/workdir* /home/user/simulation-files/results/ERROR/
		fi
		java -jar result-analysis-sync-study.jar config-validation.sync.properties
	elif [ "$1" == 'evaluation' ]; then
		echo "Running evaluation of results.txt"
		java -jar result-analysis-sync-study.jar config-replication.sync.properties
	elif [ "$1" == 'cleanup' ]; then
		echo "Running cleanup of old result files."
		rm -r /home/user/simulation-files/results/
	fi
}

cherries() {
	echo "Running evaluation on cherry picks."

	if [ "$1" == 'replication' ]; then
		java -jar -Dtinylog.configuration=/home/user/tinylog.properties cherries.jar config-replication.cherries.properties
		java -jar result-analysis-cherries.jar config-replication.cherries.properties
	elif [ "$1" == 'validation' ]; then
		echo "Running a (hopefully) short validation of the installation."
		echo ""
		echo ""
		java -jar -Dtinylog.configuration=/home/user/tinylog.properties cherries.jar config-validation.cherries.properties
		java -jar result-analysis-cherries.jar config-validation.cherries.properties
	elif [ "$1" == 'evaluation' ]; then
		echo "Running evaluation of results.txt"
		java -jar result-analysis-cherries.jar config-replication.cherries.properties
	elif [ "$1" == 'cleanup' ]; then
		echo "Running cleanup of old result files."
		rm -r /home/user/simulation-files/results/
	fi
}

if [ "$1" == '' ]; then
	echo "TODO: MENU"
	exit
fi

if [ "$1" == 'cherries' ] || [ "$1" == 'sync-study' ]; then
	if [ "$1" == 'cherries' ]; then
		cherries $2
	fi
	if [ "$1" == 'sync-study' ]; then
		sync_study $2
	fi
fi
