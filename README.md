# besu-zkvm

A stateless transition function (STF) implementation of Besu for zkVM-based block proving.

## Overview

This project implements a stateless block execution engine that can be compiled to a RISC-V binary executable on riscv64 based zkVMs. 

While the code can be compiled and run as Java bytecode or native images on other architectures (x86-64, aarch64), these modes are primarily for development and debugging. The project purpose is to compile to riscV64 using graal.

## Prerequisites
- **GraalVM Community 24.0.2** (other versions may have compatibility issues)

## Running and Building a Native Image

### Development native-image build

```bash
JAVA_HOME=/path/to/graalvm ./gradlew clean nativeCompile
```

The native executable is output to `build/native/nativeCompile/blockRunner`

### Running BlockRunner

### Usage:
```Starting BlockRunner .
Usage: BlockRunner [OPTIONS]

Options:
  --state=<path>    Path to state.json file
  --block=<path>    Path to block.rlp file
  --genesis=<path>  Path to genesis config file
                    /mainnet.json, /sepolia.json, and /hoodi.json
                    are bundled. 
  --help, -h, ?     Display this help message

Defaults:
  --state   : bundled /state.json resource
  --block   : bundled /block.rlp resource
  --genesis : bundled /mainnet.json resource
```

Run without arguments to use embedded test data, or provide all three files for custom block execution.

## Running and Building for the JVM

For debugging and development without native compilation:

```bash
./gradlew runBlockRunner --args="/path/to/genesis.json /path/to/block.json /path/to/witness.json"
```

Note: JVM execution is not the primary target. Use native image compilation for actual zkVM proving workflows.  Some graal-specific code will not be executed when running via the jvm, so jvm execution should be considered a debugging convenience only.


## Running and Building on RiscV-64

To produce a riscv64 artifact you can either build the native image directly in a linux riscv-64 system, or cross compile it from another architecture.   Graal does not support riscv64 targets directly, so an llvm-substrate enabled version of graalvm is necessary.  

See [toolchains](toolchains/README.md) for more detail on the availability and setup of graal-llvm-enabled toolchains..

### Prerequisites
Graal 24.0.2 built with the LLVM substrate feature 


### Building from a riscv64 system 
To build in a riscv64 system:
`./gradlew -PNATIVE_IMAGE_OPTIONS="-H:CompilerBackend=llvm" nativeImage`

### Cross-compiling 
To build, for example from linux-x86-64, you will need to configure the (toolchain)[toolchains/README.md] and pass the cross compile parameters to gradle:
```
export GRAAL_PATH=~/dev/riscv
export JAVA_HOME=$GRAAL_PATH/graalvm-dev-java21-24.0.2
export NATIVE_IMAGE_OPTIONS=" -H:CompilerBackend=llvm -Dsvm.targetPlatformArch=riscv64 -H:CAPCacheDir=$GRAAL_PATH/capcache \
        -H:CCompilerPath=$GRAAL_PATH/riscv-nightly/bin/riscv64-unknown-linux-gnu-gcc \
        -H:CustomLD=$GRAAL_PATH/riscv-nightly/bin/riscv64-unknown-linux-gnu-ld \
        -H:CLibraryPath=$GRAAL_PATH/static-libraries \
        --add-exports=jdk.internal.vm.ci/jdk.vm.ci.riscv64=org.graalvm.nativeimage.builder
        
./gradlew -PCROSS_COMPILE_ARCH=riscv64 -PNATIVE_IMAGE_OPTIONS="$NATIVE_IMAGE_OPTIONS" nativeImage
```

The resulting blockRunner binary will be cross compiled to the target riscv64 arch




