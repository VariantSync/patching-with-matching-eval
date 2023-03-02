#! /bin/bash

# Function for evaluating results and plotting figures
evaluation () {
    echo "Running result evaluation"
    java -jar ResultEval-jar-with-dependencies.jar "$1"

    echo "Plotting figures"
    PD=/home/user/simulation-files/plots
    if test -d "$PD"; then
      echo ""
    else
      mkdir $PD
    fi

    PD=/home/user/results/plots
    if test -d "$PD"; then
        echo ""
    else
      mkdir $PD
    fi

    cd plots || exit
    python3 main.py /home/user/results/results.txt
    cd ..

    echo "Cleaning temporary directories"
    rm -rf /home/user/simulation-files/workdir*
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


# Extract ground truth
cd /home/user/simulation-files/variability-busybox/data || exit
echo "Unpacking ground truth. This may take a minute..."
unzip -nq \*.zip
echo "done."

cd /home/user || exit

cp target/*Runner*-jar-with* .
cp target/ResultEval-jar-with-dependencies* .

if [ "$1" == 'replication' ] || [ "$1" == 'validation' ]; then
  if [ "$1" == 'replication' ]; then
    echo "Running full study replication. This will take several weeks depending on your system. You can stop the execution
    in a separate terminal by calling the stop-execution script. You can resume the replication by specifying the
    corresponding 'runid' in the properties. Please refer to the README for more information. "
    echo ""
    echo ""
    echo ""
    java -jar StudyRunner-jar-with-dependencies.jar config-replication.properties
    if [ $? -eq 1 ]; then
      mkdir /home/user/results/ERROR
      cp -r /home/user/simulation-files /home/user/results/ERROR/
      cp -r /home/user/TARGET /home/user/results/ERROR/
    fi
    evaluation config-replication.properties
  elif [ "$1" == 'validation' ]; then
    echo "Running a (hopefully) short validation of the installation."
    echo ""
    echo ""
    echo ""
    java -jar StudyRunner-jar-with-dependencies.jar config-validation.properties
    if [ $? -eq 1 ]; then
      mkdir /home/results/ERROR
      cp -r /home/user/workdir* /home/user/results/ERROR/
    fi
    evaluation config-validation.properties
  fi
elif [ "$1" == 'evaluation' ]; then
  echo "Running evaluation of results.txt"
  evaluation config-replication.properties
elif [ "$1" == 'cleanup' ]; then
  echo "Running cleanup of old result files."
  rm -r /home/user/results/plots
  rm /home/user/results/results*
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
