# Decades of GNU Patch and Git Cherry-Pick: Can We Do Better? 

This is the reproduction package for our paper _Decades of GNU Patch and Git Cherry-Pick: Can We Do Better?_ which has been accepted to the 48th International Conference on Software Engineering (ICSE 2026). 

## Content
The reproduction package consists of three main parts: 

1. [__mpatch__](/mpatch/README.md): The implementation of our novel match-based patcher, written in Rust. 
2. [__Mined cherries__](dataset/): Our dataset of cherry picks mined from 5,000 GitHub repositories. 
3. [__Empirical evaluation__](src/main/kotlin/org/variantsync/evaluation/PatcherEvaluationMain.kt): Our empirical evaluation of different language-agnostic patchers. 

## Requirements
Software Requirements
- [Docker](https://www.docker.com/)

Hardware Requirements
- We recommend running the evaluation on a system with at least __64GB__ of primary memory (RAM).
- 100GB—2TB of free drive space, depending on the configuration of the Docker image.

> [!WARNING]  
> The used storage medium should be very fast, e.g., M.2 NVMe SSD with 5000 MB/s, otherwise the evaluation may take an extremely long time.

Other Requirements
- A stable internet connection.


## Installation

### [Optional] Configuration
Before building the Docker image, you may __optionally__ configure how the evaluation is executed. 
To this end, we provide two configuration files: [config-reproduction.properties](docker/config-reproduction.properties) for the configuration of the reproduction of the entire evaluation, and [config-verification.properties](docker/config-verification) for the verification of the correct installation of the reproduction package. 

Depending on the available hardware, you may need to adjust the following settings:

- The number of threads used (i.e., how many repositories are processed in parallel). Please note that each thread requires an additional `40GB` of free space on your drive.
- Whether all repositories should be cloned before the evaluation. This eliminates the need for a stable internet connection once all repositories have been cloned.
- Whether repositories should be deleted after they have been evaluated. This significantly reduces the amount of required free space on your drive (around 100GB should be enough).

> [!WARNING]
> The entire set of repositories considered by our evaluation requires about 600 GBs of free space on our drive, if `clean-repositories` is set to `false`. 

> [!NOTE]  
> Every change in the configuration must be followed by rebuilding the Docker image.


### Building the Docker image
The reproduction package is meant to be run in the Docker image that can be built using the provided Dockerfile. 

#### Linux
On Linux, you can execute the provided `build.sh` script to build the Docker image.

> **Note:** The build process may take a while. (~5 minutes)

> **Note:** The build process may require sudo privileges.

```shell 
./build.sh
```

#### Other OS
On other machines, you may call Docker directly. 
In this case, you have to provide a USER_ID and GROUP_ID for the user in the Docker container:
```bash
# For example, under Linux, both variables are set as follows:
# USER_ID=$(id -u ${SUDO_USER:-$(whoami)})
# GROUP_ID=$(id -g ${SUDO_USER:-$(whoami)})

docker build --network=host --build-arg USER_ID=$USER_ID --build-arg GROUP_ID=$GROUP_ID -t mpatch-reproduction .
```
Ideally, the `USER_ID` and `GROUP_ID` match the ids of the user running the command (not root!). 
Under Windows, you may provide any suitable id (e.g., `1000` for both)

```shell 
docker build --network=host --build-arg USER_ID=1000 --build-arg GROUP_ID=1000 -t mpatch-reproduction .
``` 


### Verifying the correct installation
Once the building of the Docker image has completed, you can verify its correct installation. 
By default, the verification will be executed within the [evaluation-workdir](evaluation-workdir) directory.

#### Starting the verification
On Linux, you can execute the provided `execute.sh` script with the `verification` argument: 
```shell
./execute.sh verification
```

On other machines, you may start a Docker container from the Docker image with the following command: 
```bash 
# Depending on your OS, you may have to change how the first path to evaluation-workdir is defined
docker run --rm -v "./evaluation-workdir/":"/home/user/evaluation-workdir" mpatch-reproduction verification
```

> [!NOTE]  
> Depending on your hardware, the verification should require 5-30 minutes.

#### Verification in a custom directory
> [!NOTE]  
> You may provide any directory as first argument for `-v`, either by altering the `execute.sh` script or changing the command above. 
> The `evaluation-workdir` is where the evaluation stores all its data while processing the repositories and evaluating patchers. 
> The results will also be saved to this directory, once the evaluation or verification finishes. 
 
 For example, your may start the evaluation with 
```shell
 docker run --rm -v "/home/YOUR_USERNAME/ICSE-reproduction/":"/home/user/evaluation-workdir" mpatch-reproduction verification
```

#### Expected outcome

TODO TODO TODO


# Starting the reproduction
Once you have verified the correct installation, you can start the reproduction similar to how you started the verification. 
You may also change the working directory to a custom directory as described for the verification.

On Linux, you can execute the provided `execute.sh` script with the `reproduction` argument: 
```shell
./execute.sh reproduction
```

On other machines, you may start a Docker container from the Docker image with the following command: 
```bash 
# Depending on your OS, you may have to change how the first path to evaluation-workdir is defined
docker run --rm -v "./evaluation-workdir/":"/home/user/evaluation-workdir" mpatch-reproduction reproduction
```



> [!NOTE]  
> Our evaluation processes large amounts of data. 
> The main bottleneck is not the available CPU but the speed of the drive in which the `evaluation-workdir` is located. 
> Depending on your hardware, the full reproduction may require a very long time. The expected runtime are 5-10 days, but the reproduction may also require several weeks if the drive is too slow. 


## Local dataset setup
- The `repo-sample.zip` file in `evaluation-workdir/data/` contains a single yaml file which enumerates the metadata for all sampled repositories.
- To unpack the dataset of cherry picks, you may need to perform the following command on a Linux console first. Thereafter, you can extract all the mined cherry picks from the created `unsplit-mined-cherries.zip` file.
```shell
zip -s 0 mined-cherries.zip --out unsplit-mined-cherries.zip
```
