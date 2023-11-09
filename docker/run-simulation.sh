#! /bin/bash

# Function for evaluating results and plotting figures
evaluation() {
	echo "Running result evaluation"
	java -jar result-eval.jar "$1"

	echo "Plotting figures"
	PD=/home/user/simulation-files/plots
	if test -d "$PD"; then
		echo ""
	else
		mkdir $PD
	fi
	cd plots || exit
	MPLCONFIGDIR=/home/user/.config/matplotlib python3 main.py /home/user/simulation-files/results /home/user/simulation-files/plots
}

if [ "$1" == '' ]; then
	echo "Either fully replicate the study as presented in the paper (replication), do quick installation validation (validation),
  clean old result data (cleanup), or evaluate existing result data (evaluation)."
	echo "-- Bash examples --"
	echo "Replicate study: './execute.sh replication'"
	echo "Validate the installation: './execute.sh validation'"
	echo "Clean old result files: './execute.sh cleanup'"
	echo "Evaluate results in 'simulation-files/results.txt': './execute.sh evaluation'"
	exit
fi

if [ "$1" == 'replication' ] || [ "$1" == 'validation' ]; then
	if [ "$1" == 'replication' ]; then
		echo "Running full study replication. This will take several weeks depending on your system. You can stop the execution
    in a separate terminal by calling the stop-execution script. You can resume the replication by specifying the
    corresponding 'runid' in the properties. Please refer to the README for more information. "
		echo ""
		echo ""
		echo ""
		java -jar -Dtinylog.configuration=/home/user/tinylog.properties experiment-execution.jar config-replication.properties
		if [ $? -eq 1 ]; then
			mkdir /home/user/simulation-files/results/ERROR
			cp -r /home/user/simulation-files /home/user/simulation-files/ERROR/
			cp -r /home/user/TARGET /home/user/simulation-files/results/ERROR/
		fi
		evaluation config-replication.properties
	elif [ "$1" == 'validation' ]; then
		echo "Running a (hopefully) short validation of the installation."
		echo ""
		echo ""
		echo ""
		java -jar -Dtinylog.configuration=/home/user/tinylog.properties experiment-execution.jar config-validation.properties
		if [ $? -eq 1 ]; then
			mkdir /home/user/simulation-files/results/ERROR
			cp -r /home/user/simulation-files/main/workdir* /home/user/simulation-files/results/ERROR/
		fi
		evaluation config-validation.properties
	fi
elif [ "$1" == 'evaluation' ]; then
	echo "Running evaluation of results.txt"
	evaluation config-replication.properties
elif [ "$1" == 'cleanup' ]; then
	echo "Running cleanup of old result files."
	rm -r /home/user/simulation-files/results/
else
	echo "Either fully replicate the study as presented in the paper (replication), do quick installation validation (validation),
  clean old result data (cleanup), or evaluate existing result data (evaluation)."
	echo "-- Bash examples --"
	echo "Replicate study: './execute.sh replication'"
	echo "Validate the installation: './execute.sh validation'"
	echo "Clean old result files: './execute.sh cleanup'"
	echo "Evaluate results in 'simulation-files/results.txt': './execute.sh evaluation'"
	exit
fi
