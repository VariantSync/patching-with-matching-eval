# Reproduction Package for Pushing the Boundaries of Patch Automation

This artifact comprises the files and data to reproduce our evaluation of various patchers, including mpatch.

## Content
The [data](evaluation-workdir/data) directory of the evaluation's working directory (evaluation-workdir) contains our minded sample of 5000 GitHub repositories and our dataset of mined patch scenarios.
The repository sample and datasets are compressed as zip archives that have to be unpacked before they can be used.

__Note__:

- The repo.zip file contains a single-yml file which enumerates the metadata for all examined repositories.
- To unpack the dataset of cherry picks, you may need to perform the following command on a Linux console first:

```shell
zip -s 0 mined-cherries.zip --out unsplit-mined-cherries.zip
```

Afterwards you can extract all the mined cherry picks from the created `unsplit-mined-cherries.zip` file.

The [mpatch](mpatch) folder contains the mpatch tool which is implemented in Rust. In its [README](mpatch/README.md), you can find instructions on
how to generate the documentation for mpatch.

The [src](src/main) folder contains the implementation of our evaluation setup in the Java and Kotlin directories.
The main file of the evaluation is [PatcherEvaluationMain.kt](src/main/kotlin/org/anon/evaluation/PatcherEvaluationMain.kt).

The [src/main/python](src/main/python/result_analysis) directory encompasses the scripts for computing the various metrics for the different patchers and for performing the statistical analysis.

## Results without outliers
In Section VI.A (2) of the submitted paper, we mention that we re-analyzed our results after excluding outliers.
Specifically, we removed the top 0.05% of results with the highest number of required fixes for each patcher; thus, treating patchers equally with this regard.
The updated version of Table IV is shown below.

![results-without-outliers.png](results/results-without-outliers.png)




# Reproduction Package for Pushing the Boundaries of Patch Automation

This artifact comprises the files and data to reproduce our evaluation of various patchers, including mpatch. 
Unfortunately, this anonymous artifact is currently not functional due to the anonymization breaking packages and dependencies. 

## Content
Our sample of GitHub repositories and our dataset of mined patch scenarios is located in the _evaluation-workdir/data_ directory of the evaluation's working directory.
The sample and dataset are compressed as zip archives that have to be unpacked before they can be used.

Our implementation of mpatch was written in Rust and can be found under _mpatch_. In its _mpatch/README.md_, you can also find instructions on
how to generate the documentation for mpatch.

The implementation of our evaluation setup can be found in the Java and Kotlin sources in the _src/main_ folder. 
The main file of the evaluation is _src/main/kotlin/org/anon/evaluation/PatcherEvaluationMain.kt_. 

Our scripts for applying the various metrics to the different patchers and analyzing the statistics can be found under _src/main/python/result_analysis_. 
The raw results of our evaluation are archived under _evaluation-workdir/results_

## Results without outliers
In our paper (cf. Section VI.2), we mention that we re-analyzed our results after excluding outliers. 
Specifically, we removed the top 0.05% of results with the highest number of required fixes for each patcher; thus, treating patchers equally in this regard. 
The updated version of Table IV is shown below. 
The updated table shows that after removing outliers `mpatch` requires considerably fewer fixes after a patch application than other patchers, across all project languages. 

![results-without-outliers.png](results/results-without-outliers.png)
