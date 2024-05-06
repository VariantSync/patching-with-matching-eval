![Maven](https://github.com/VariantSync/SyncStudy/actions/workflows/maven.yml/badge.svg)
[![DOI](https://zenodo.org/badge/DOI/10.5281/zenodo.7025599.svg)](https://doi.org/10.5281/zenodo.7025599)
[![Documentation](https://img.shields.io/badge/Documentation-read%20here-blue)][documentation]
[![Requirements](https://img.shields.io/badge/System%20Requirements-read%20here-blue)](INSTALL.md)
[![Install](https://img.shields.io/badge/Installation%20Instructions-read%20here-blue)](INSTALL.md)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue)](LICENSE)

# Quantifying the Potential to Automate the Synchronization of Variants in Clone-and-Own

This is the replication package of the paper _"Quantifying the Potential to Automate the Synchronization of Variants in
Clone-and-Own"_ accepted at
the 38th IEEE International Conference on Software Maintenance and Evolution (ICSME 2022).
It contains the Java code of our simulation, the dataset extracted from BusyBox, the code used to analyze the results,
and the files containing
the results that we report in the paper.

## Obtaining the Artifacts

Clone the repository to a location of your choice using [git](https://git-scm.com/):

  ```
  git clone https://github.com/VariantSync/SyncStudy.git
  ```

## Project Structure

This is brief overview on the most relevant directories and files:

* [`docker`](docker) contains the script and property files used by the Docker containers.
    * [`docker/config-replication.properties`](docker/config-replication.sync.properties) configures the simulation as
      presented in our paper.
    * [`docker/config-validation.properties`](docker/config-validation.sync.properties) configures a quick simulation
      for validating the correctness of the Docker setup.
* [`docs`](docs) contains the Javadocs of our source code. You can open the Javadocs in your [browser][documentation].
* [`local-maven-repo`](local-maven-repo) contains additional libraries.
* [`plots`](plots) contains pythons scripts for plotting the figures shown in our paper.
* [`results`](results) all results and generated plots will be stored in this directory. Initially it is empty.
* [`simulation-files`](simulation-files) is the working directory of the study. This directory initially contains the
  dataset with the domain knowledge that we extracted from BusyBox. The files comprise lists
  of commits that state whether a commit could be processed, and the domain knowledge for over 8,000 commits. For about
  5,000 commits, the complete domain knowledge could be extracted. After running our study, all results can also be
  found here.
* [`src`](src/main/java/org/variantsync/evaluation) contains the source files used to run the simulation.
* `reported-results-part*.zip` are archives with the raw result data reported in our paper. These files are not used by
  the replication of our study, but instead can be used to inspect the results of our study without running it. To
  evaluate the results, they have to be unpacked and copied into a single file.
* [`synchronization-study.pdf`](synchronization-study.pdf) is the pdf file of our ICSME paper (Submission 109).

We offer a [Docker](https://www.docker.com/) setup to easily __replicate__ the study performed in our paper.
In the following, we provide a quickstart guide for running the replication.
You can find detailed information on how to install Docker and build the container in the [INSTALL](INSTALL.md) file,
including detailed descriptions of each step and troubleshooting advice. Information about the software and hardware
requirements can be found in the [REQUIREMENTS](REQUIREMENTS.md) file.

## Study Replication

[Docker might be slow on Windows](https://www.createit.com/blog/make-docker-on-windows-fast-again-2022/). Therefore, we
recommend replicating the study on any Linux distro or on WSL2.

### 0. Preparation

- Start the docker deamon.
- Clone this repository to a directory of your choice.
- Open a terminal and navigate to the root directory of this repository.

### 1. Build the Docker container

To build the Docker container you should run the `build` script corresponding to your operating system.

> Note: As an alternative to building the Docker image yourself, we also provide a prebuilt image
> via [Zenodo](https://doi.org/10.5281/zenodo.7025599).
> You can load it in Docker with `docker load < sync-study-image.tar`

#### Windows:

`.\build.bat`

#### Linux/Mac (bash):

`./build.sh`

### 2. Validate the successful installation

> The replication will require 10-30 days depending on your hardware.
> Therefore, we offer a short validation (5-10 minutes) which runs a small subset of the study.
> You can run it by providing "validation" as argument instead of "replication" (
> i.e., `.\execute.bat validation`,  `./execute.sh validation`).
> If you want to stop the execution, you can call the provided script for stopping the container in a separate terminal.
> #### Windows:
> `.\stop-execution.bat`
> #### Linux/Mac (bash):
> `./stop-execution.sh`

To execute the validation you can run the `execute` script corresponding to your operating system with `validation` as
first argument.

#### Windows:

`.\execute.bat validation`

#### Linux/Mac (bash):

`./execute.sh validation`

The validation's results are printed to the terminal and saved to the [results](results) directory. The generated plots
are stored in the [results/plots](results/plots) directory.

### 3. Start the replication

**ATTENTION**

> Before running or re-running the replication:
> Make sure to clean all previously collected results by calling `./execute cleanup`. Otherwise, they will be
> counted as results of parallel experiment executions. We only append results data, not overwrite it, to make it
> possible to run multiple instances of the study in parallel.

To execute the replication you can run the `execute` script corresponding to your operating system with `replication` as
first argument.

#### Windows:

`.\execute.bat replication`

#### Linux/Mac (bash):

`./execute.sh replication`

If you stop the replication at a certain point, you can resume it by setting the desired `runID` in
the [properties](docker/config-replication.sync.properties). You can find the `runID` of the last available result at
the end of the [results.txt](results/results.txt) (cf. next section).

### 4. View the results in the [results](results) directory

All raw results are stored in a [results](results/results.txt) file.
The aggregated results are printed to the terminal at the end of the execution and saved to
the [results-summary](results/results-summary.txt) file. The aggregated results are shown in Table 1 in our paper.

Moreover, the results comprise the [plots](results/plots) that are part of our paper. There are three
plots: [rq1_applicability](results/plots/rq1_applicability.pdf) corresponds to Fig. 5 in our
paper, [rq2_correctness](results/plots/rq2_correctness.pdf) to Fig. 6,
and [rq3_domain_knowledge](results/plots/rq3_domain_knowledge.pdf) to Fig. 7.

### Documentation

This replication package is documented with javadoc. The documentation can be accessed on this [website][documentation].

### Docker Experiment Configuration

By default, the properties used by Docker are configured to run the experiments as presented in our paper. We offer the
possibility to change the default configuration, for example to change the number of repetitions or generated variants.

* Open the properties file [`config-replication.properties`](docker/config-replication.sync.properties).
* Change the properties to your liking.
* Rebuild the docker image as described above.
* Delete old results in the `./simulation-files` folder.
* Start the simulation as described above.

### Clean-Up

The more experiments you run, the more space will be required by Docker. The easiest way to clean up all Docker images
and
containers afterwards is to run the following command in your terminal. **Note that this will remove all other
containers and images
not related to the simulation as well**:

```shell
# Will delete all data stored by Docker. 
# WARNING! This includes other images, containers, and volumes as well. 
docker system prune -a
```

**Alternatively**, you can clean only the study's image and containers by calling the provided script. However, we
cannot guarantee that all data is deleted in this case (e.g., third-party images and data not covered by the script).

#### Windows:

`.\clean-docker.bat`

#### Linux/Mac (bash):

`./clean-docker.sh`

Please refer to the official documentation on how to remove
specific [images](https://docs.docker.com/engine/reference/commandline/image_rm/)
and [containers](https://docs.docker.com/engine/reference/commandline/container_rm/) from your system.

[documentation]: https://variantsync.github.io/SyncStudy/
