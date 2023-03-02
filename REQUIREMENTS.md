## Hardware Requirements
There are no special requirements regarding the CPU or GPU. 

### Primary Memory
We recommend to run the simulation on a system with at least __16GB__ of primary memory (RAM). 

### Secondary Memory  
I/O operations have a considerable impact on the total runtime of the simulation. Therefore, we recommend storing the repository on an SSD, and to configure Docker to store its data (e.g., images and containers) on an SSD as well. 

The repository requires about __4GB__ of space. The Docker image and container require about __20GB__ of space as they contain the extracted ground truth. __In total__, we recommend to have at least __30GB__ of free space on the used storage device.



## Software Requirements
The study does not require a certain operating system or prepared environment.
We tested our setup on Windows 10, WSL2, and Manjaro.

### With Docker
Our study can be replicated on any system supporting [Docker](https://docs.docker.com/get-docker/).
Docker will take care of all requirements and dependencies to replicate our validation.

### Without Docker
To replicate the study on a system without Docker, JDK17 and [Maven](https://maven.apache.org/what-is-maven.html) are required.
Please note that Intellij and Eclipse support Maven out-of-the-box - there should be no need to install Maven manually when using an IDE to replicate our study.

We also recommend to run the study under Linux, as Window's file system might not support all file paths. 

Dependencies to other packages are documented in the maven build file ([pom.xml](pom.xml)) and are handled automatically by Maven.
