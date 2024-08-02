# Reproduction Package for Pushing the Boundaries of Patch Automation

This artifact comprises the files and data to reproduce our evaluation of various patchers, including mpatch.

## Content
Our sample of GitHub repositories and our dataset of mined patch scenarios is located in the [data](evaluation-workdir/data) directory of the evaluation's working directory.
The sample and dataset are compressed as zip archives that have to be unpacked before they can be used.

Our implementation of mpatch was written in Rust and can be found under [mpatch](mpatch). In its [README](mpatch/README.md), you can also find instructions on
how to generate the documentation for mpatch.

The implementation of our evaluation setup can be found in the Java and Kotlin sources in the [src](src/main) folder. 
The main file of the evaluation is [PatcherEvaluationMain.kt](src/main/kotlin/org/anon/evaluation/PatcherEvaluationMain.kt).

Our scripts for applying the various metrics to the different patchers and analyzing the statistics can be found under [src/main/python](src/main/python/result_analysis). 
The raw results of our evaluation are archived under [evaluation-workdir/results](evaluation-workdir/results)

## Results without outliers
In our paper (cf. Section VI.2), we mention that we re-analyzed our results after excluding outliers. 
Specifically, we removed the top 0.05% of results with the highest number of required fixes for each patcher; thus, treating patchers equally in this regard. 
The updated version of Table IV is shown below. 

![results-without-outliers.png](results/results-without-outliers.png)
