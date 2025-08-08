## Hardware Requirements
There are no special requirements regarding the CPU or GPU. 

### Primary Memory
We recommend to run the evaluation on a system with at least __64GB__ of primary memory (RAM). 

### Secondary Memory  
I/O operations have a considerable impact on the total runtime of the evaluation. 
Therefore, we strongly recommend storing the repository on an SSD (M2 technology or better), 
and to configure Docker to store its data (e.g., images and containers) on this SSD as well.
Using an HDD can lead to severe runtime problems and thereby timeouts that threaten the validity of the results. 

The evaluation requires about __2TB__ of space as it considers hundreds of repositories, which in turn are copied dozens of times for multi-threaded patcher evaluation. 
The space requirement can be considerably reduced by changing the number of used threads in the [reproduction config](docker/config-reproduction.properties) (e.g., to __20GB__), but then the evaluation will require considerably more time as well.  

## Software Requirements
The study does not require a certain operating system or prepared environment.

### With Docker
Our study can be reproduced on any system supporting [Docker](https://docs.docker.com/get-docker/).
Docker will take care of all requirements and dependencies to reproduce our evaluation.
