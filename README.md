# TODOs
- [ ] Overview
- [ ] Short Setup
- [ ] Short Verification instructions
- [ ] Full Replication instructions
- [ ] Documentation overview
- [ ] Dataset overview
- [ ] Custom patcher integration
- [ ] REQUIREMENTS.md
- [ ] INSTALL.md
- [ ] STATUS.md
- [ ] Integrate result analysis in Docker

# Decades of GNU Patch and Git Cherry-Pick: Can We Do Better? 

This is the reproduction package for our paper _Decades of GNU Patch and Git Cherry-Pick: Can We Do Better?_ which has been accepted to the 48th International Conference on Software Engineering (ICSE 2026). 

The reproduction package consists of three parts: 

1. [__mpatch__](/mpatch/README.md): The implementation of our novel match-based patcher, written in Rust. 
2. [__Mined cherries__](evaluation-workdir/data): Our large dataset of cherry picks mined from 5,000 GitHub repositories. 
3. [__Empirical evaluation__](src/main/kotlin/org/variantsync/evaluation/PatcherEvaluationMain.kt): Our empirical evaluation of different language-agnostic patchers. 

## Content
Our sample of GitHub repositories and our dataset of mined patch scenarios is located in the _evaluation-workdir/data_ directory of the evaluation's working directory.
The sample and dataset are compressed as zip archives that have to be unpacked before they can be used.

Our implementation of mpatch was written in Rust and can be found under _mpatch_. In its _mpatch/README.md_, you can also find instructions on
how to generate the documentation for mpatch.

The implementation of our evaluation setup can be found in the Java and Kotlin sources in the _src/main_ folder. 
The main file of the evaluation is _src/main/kotlin/org/anon/evaluation/PatcherEvaluationMain.kt_. 

Our scripts for applying the various metrics to the different patchers and analyzing the statistics can be found under _src/main/python/result_analysis_. 
The raw results of our evaluation are archived under _evaluation-workdir/results_
