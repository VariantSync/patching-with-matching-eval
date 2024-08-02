# Reproduction Package for Pushing the Boundaries of Patch Automation

This artifact comprises the files and data to reproduce our evaluation of various patchers, including mpatch.

## Content
Our implementation of mpatch was written in Rust and can be found under [mpatch](mpatch). In its [README](mpatch/README.md), you can also find instructions on
how to generate the documentation for mpatch, which can also be found under [mpatch/doc](mpatch/doc/mpatch/index.html)

The implementation of our evaluation setup can be found in the Java and Kotlin sources in the [src](src/main) folder. 
The main file of the evaluation is [PatcherEvaluationMain.kt](src/main/kotlin/org/variantsync/evaluation/PatcherEvaluationMain.kt).

Our sample of GitHub repositories and our dataset of mined patch scenarios is located in the [data](evaluation-workdir/data) directory of the evaluation's working directory. 
The sample and dataset are compressed as zip archives that have to be unpacked before they can be used. 

## Results without outliers
In our paper, we mention that we re-analyzed our results after excluding 0.05% of results that were outliers. 
The updated version of Table IV is shown below. 

![results-without-outliers.png](results/results-without-outliers.png)