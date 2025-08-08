# Installation
## Installation Instructions
In the following, we describe how to setup the evaluation of our paper step-by-step.
The instructions explain how to build the Docker image and run the validation in a Docker container.
For __Windows__ users, we recommend the use of WSL2 or a similar Linux environment. 

### 1. Install Docker (if required)
How to install Docker depends on your operating system:

- _Windows or Mac_: You can find download and installation instructions [here](https://www.docker.com/get-started).
- _Linux Distributions_: How to install Docker on your system, depends on your distribution. The chances are high that Docker is part of your distributions package database.
Docker's [documentation](https://docs.docker.com/engine/install/) contains instructions for common distributions.

Then, start the docker deamon.

### 2. Open a Suitable Terminal
```
# Windows Command Prompt:
 - Press 'Windows Key + R' on your keyboard
 - Type in 'cmd'
 - Click 'OK' or press 'Enter' on your keyboard

# Windows PowerShell:
 - Open the search bar (Default: 'Windows Key') and search for 'PowerShell'
 - Start the PowerShell

# Linux:
 - Press 'ctrl + alt + T' on your keyboard
```

Clone this repository to a directory of your choice using git:
```shell
git clone https://github.com/VariantSync/patching-with-matching-eval.git
```

### 3. Build the Docker Container
To build the Docker container you can run the `build` script:
```
# Linux/Mac/WSL2 (bash):
  ./build.sh
```

## 4. Verification & Replication

### Running the Replication or Verification
To execute the replication you can run the `execute` script corresponding to your operating system with `reproduction` as first argument. 

`./execute.sh reproduction`

> WARNING!
> TODO: RUNTIME WARNING 
> TODO: DISK USAGE WARNING
> Therefore, we offer a short verification (TODO minutes) ...
> You can run it by providing "verification" as argument instead of "reproduction" (i.e., `./execute.sh verification`).
> If you want to stop the execution, you can call the provided script for stopping the container in a separate terminal.
> When restarted, the execution will continue processing by restarting at the last unfinished state.
> `./stop-execution.sh`

You might see warnings or errors reported from SLF4J like `Failed to load class "org.slf4j.impl.StaticLoggerBinder"` which you can safely ignore.
Further troubleshooting advice can be found at the bottom of this file.

The results of the verification will be stored in the [evaluation-workdir](evaluation-workdir) directory.

### Expected Output of the Verification
TODO

## Troubleshooting

### 'Got permission denied while trying to connect to the Docker daemon socket'
`Problem:` This is a common problem under Linux, if the user trying to execute Docker commands does not have the permissions to do so.

`Fix:` You can fix this problem by either following the [post-installation instructions](https://docs.docker.com/engine/install/linux-postinstall/), or by executing the scripts in the replication package with elevated permissions (i.e., `sudo`).

### 'Unable to find image 'pwm-reproduction:latest' locally'
`Problem:` The Docker container could not be found. This either means that the name of the container that was built does not fit the name of the container that is being executed (this only happens if you changed the provided scripts), or that the Docker container was not built yet.

`Fix:` Follow the instructions described above in the section `Build the Docker Container`.

### Failed to load class "org.slf4j.impl.StaticLoggerBinder"
`Problem:` An operation within the initialization phase of the logger library we use (tinylog) failed.

`Fix:` Please ignore this warning. Tinylog will fall back onto a default implementation (`Defaulting to no-operation (NOP) logger implementation`) and logging will work as expected.
