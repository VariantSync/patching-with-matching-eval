# mpatch - A patching tool and library

## Requirements
To install `mpatch`, you require an up-to-date installation of the rust toolchain. You can find instructions on how to install Rust on the [official website](https://www.rust-lang.org/tools/install).

## Usage
### As a CLI tool
You can install `mpatch` locally by calling `cargo install --path .` in the root of the `mpatch` directory. Afterwards, you can call `mpatch --help` to get usage instructions. 
The CLI of `mpatch` should be called directly in the directory of the target version. The path to the patchfile should point to a patch created with `diff -Nur <source-dir> <changed-source-dir>`.


#### Usage:
```shell
mpatch --sourcedir=<path-to-source> --patchfile=<path-to-patch> 
```

#### Example:
```shell
# Inside the targets directory, e.g., /home/user/patching/TargetVersion
mpatch --sourcedir=/home/user/patching/SourceVersion --patchfile=/home/user/patching/patch.diff 
```

### As a library 
You can also use `mpatch` as a library in your own Rust projects. You can read the documentation by calling `cargo doc --open` in the root directory of the repository. 


