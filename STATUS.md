# STATUS
## Overview
The reproduction package for our paper _Decades of GNU Patch and Git Cherry-Pick: Can We Do Better?_ consists of three parts: 

1. [__mpatch__](/mpatch/README.md): The implementation of our novel match-based patcher, written in Rust. 
2. [__Mined cherries__](evaluation-workdir/data): Our large dataset of cherry picks mined from 5,000 GitHub repositories. 
3. [__Empirical evaluation__](src/main/kotlin/org/variantsync/evaluation/PatcherEvaluationMain.kt): Our empirical evaluation of different language-agnostic patchers. 

## Purpose
Our artifact has the following purposes:

### **Reproducibility**
We provide replication instructions that allow to replicate the evaluation presented in Sections 4 through 6 in our paper. 
The replication is executed in a Docker container.

### **Reusability**
Our evaluation can be extended and reused to evaluate and compare additional patchers with the patchers considered in our paper. 
To do so, the [Patcher](src/main/kotlin/org/variantsync/evaluation/patching/Patcher.kt) interface has to be implemented for each additional patcher. 
Then, an instance of the implementing class can be added to the list of patchers during [evaluation initialization](src/main/kotlin/org/variantsync/evaluation/execution/EvalOperations.kt). 

Our novel patcher _mpatch_ is a fully functional tool that can be used as a patcher alternative to git cherry-pick or GNU patch. 
It can also be integrated into other evaluation setups using its [library](mpatch/src/lib.rs) or [CLI](/mpatch/README.md).

## Claims
We claim the _Artifacts Available_ badge as we made our artifacts publicly available on [Github](TODO) and [Zenodo](TODO) with an open-source license. 
Our dataset and the repositories from which we mined it are also publicly available.

We claim the _Artifacts Evaluated Reusable_ badge as our evaluation and our novel patcher can be reused by other researchers and practitioners.

