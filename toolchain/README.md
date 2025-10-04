# RISC-V GraalVM Toolchains

This repository contains specialized **GraalVM-based toolchains** designed to enable building native images targeting **Linux riscv64**.  

By default, GraalVM does not support riscv64 as a target architecture. The toolchains here incorporate custom Graal builds with the experimental LLVM backend enabled, along with the necessary supporting artifacts, to make riscv64 cross-compilation and native execution possible.

All toolchains are Debian-based and produce **dynamically linked glibc artifacts**.  Future work will focus on MUSL, but these graal builds are the result of initial discovery and PoC work.

---

## Quick Start
Use the linux-amd64-gnu graal toolchain and riscv64 artifacts to build riscv64 native-images from linux amd64 java environment such as Ubuntu 24.04 or Debian Trixie.

```bash
oras pull ghcr.io/consensys/besu-zkvm-artifacts:graalvm-dev-java21-24.0.2-linux-riscv64
oras pull ghcr.io/consensys/besu-zkvm-artifacts:linux-riscv64-gnu-capcache
oras pull ghcr.io/consensys/besu-zkvm-artifacts:linux-riscv64-gnu-static-libraries
curl -LO https://github.com/riscv-collab/riscv-gnu-toolchain/releases/download/2025.07.16/riscv64-glibc-ubuntu-22.04-llvm-nightly-2025.07.16-nightly.tar.xz
```

Extract all of these to a build directory.  

You can then set your JAVA_HOME to the extracted graal directory, and specify native-image options thusly:

```bash
export GRAAL_PATH=/build
export JAVA_HOME=$GRAAL_PATH/graalvm-dev-java21-24.0.2
export PATH=$JAVA_HOME/bin:$PATH
export NATIVE_IMAGE_OPTIONS=" -H:CompilerBackend=llvm -Dsvm.targetPlatformArch=mipsle -H:CAPCacheDir=$GRAAL_PATH/capcache \
      -H:CCompilerPath=$GRAAL_PATH/riscv/bin/riscv64-unknown-linux-gnu-gcc \
      -H:CustomLD=$GRAAL_PATH/riscv/bin/riscv64-unknown-linux-gnu-ld \
      -H:CLibraryPath=$GRAAL_PATH/static-libraries \
      --add-exports=jdk.internal.vm.ci/jdk.vm.ci.riscv64=org.graalvm.nativeimage.builder " 
```

Then you can use the graal jdk and native-image like so:
```bash
javac HelloWorld.java
native-image HelloWorld
```



## Toolchains

### 1. GraalVM for riscv64 (Cross & Native Builds)

This toolchain includes everything required to build and test riscv64 binaries, either cross-compiled or natively within a QEMU riscv64 environment.  

It provides:
- **Debian minbase qcow2 volume**  
- **Linux kernels** for `qemu-system-riscv64`  
- **Static libraries** required for `linux-riscv64-gnu`  
- **c-library capcache** for Graal environment targeting  
- **Graal 24.0.2 (LLVM backend enabled)** using [labs-openjdk-21.0.2](https://github.com/graalvm/labs-openjdk-21/releases/tag/jvmci-23.1-b33)  

Artifacts available via GHCR (public, no auth required):

- `ghcr.io/consensys/besu-zkvm-artifacts/graalvm-dev-java21-24.0.2-linux-riscv64`  
- `ghcr.io/consensys/besu-zkvm-artifacts:linux-riscv64-gnu-capcache`  
- `ghcr.io/consensys/besu-zkvm-artifacts:deb-minbase-riscv64`  
- `ghcr.io/consensys/besu-zkvm-artifacts:linux-riscv64-gnu-static-libraries`  
- `ghcr.io/consensys/besu-zkvm-artifacts:qemu-linux-riscv64-kernel-5.8`  
- `ghcr.io/consensys/besu-zkvm-artifacts:qemu-linux-riscv64-kernel-6.12`  

---

### 2. GraalVM for amd64 (Cross-Build Host)

This toolchain is a matching **Graal 24.0.2 labs-openjdk-21 build** for **linux-amd64-gnu**.  
It is primarily used for **cross-compiling riscv64 binaries on amd64 hosts**.

Artifact available via GHCR:

- `ghcr.io/consensys/besu-zkvm-artifacts:graalvm-dev-java21-24.0.2-linux-amd64`

---

## Fetching Artifacts with ORAS

Artifacts are stored as OCI images on `ghcr.io`.  
They can be fetched and extracted locally with [`oras`](https://oras.land/). 

### Installing ORAS

On macOS (with Homebrew):

```bash
brew install oras
```

On Linux (manual install or package managers, see oras.land).

Pulling Artifacts
```bash
# Example: fetch and extract the riscv64 GraalVM build
oras pull ghcr.io/consensys/besu-zkvm-artifacts:graalvm-dev-java21-24.0.2-linux-riscv64

# Example: fetch the Debian riscv64 minbase image
oras pull ghcr.io/consensys/besu-zkvm-artifacts:deb-minbase-riscv64
```

This will place the files into your current directory.

# Using the riscv64 Toolchain
## Booting the QEMU riscv64 Environment
Riscv64 hardware is pretty slow and limited in many cases.  It is most expedient to use qemu from a
faster host machine for any necessary testing, building, or exploration.
You may use qemu to boot a riscv64 instance using the qcow2 Debian image and Linux kernel artifacts:

```bash
qemu-system-riscv64 \
    -machine virt \
    -cpu sifive-u54 \
    -m 16G \
    -smp cpus=8 \
    -nographic \
    -serial mon:stdio \
    -kernel qemu-riscv64-linux-6.12-Image \
    -append "root=/dev/vda rw console=ttyS0 sv48" \
    -drive file=deb-minbase-riscv64.qcow2,format=qcow2,id=hd0,if=none \
    -device virtio-blk-device,drive=hd0 \
    -netdev user,id=net0,hostfwd=tcp::10022-:22 \
    -device virtio-net-device,netdev=net0
```

This will boot into a riscv64 console with networking enabled.
Files can be transferred using SSH (scp).

note: =to exit cleanly:
  
 - Run `sync` inside the VM to sync the filesystem
 - Press Ctrl-A X to abort the emulator.

## Preparing the Environment for Native Builds
Inside the QEMU system:

```bash
apt install -y gcc zlib1g-dev
mkdir /build && cd /build 

# pull in a previously downloaded graal riscv64 artifact, then:
tar -xvzf graalvm-dev-java21-24.0.2-linux-riscv64.tar.gz
export JAVA_HOME=/build/graalvm-dev-java21-24.0.2-linux-riscv64
export PATH=$JAVA_HOME/bin:$PATH
```

### Example: Native HelloWorld Build
```bash
# compile the java class
javac HelloWorld.java

# build native binary
native-image -H:CompilerBackend=llvm HelloWorld
```
The resulting file will be a native binary, `helloworld`.

### Generating a Capcache
If you need to re-generate a capcache for cross-compilation, you can specify graal to 
write one in a particular directory, for example:

```bash
native-image HelloWorld -H:CompilerBackend=llvm \
  -add-exports=jdk.internal.vm.ci/jdk.vm.ci.riscv64=org.graalvm.nativeimage.builder \
  -H:+ExitAfterCAPCache \
  -H:CAPCacheDir=/path/to/capcache \
  -H:+NewCAPCache
```

## Cross-Building on amd64 for riscv64
Typically you will want to build a native artifact directly on the host machine, as it will be faster and can integrate with local tooling and IDEs when building java projects.  For example, on a linux-amd64-gnu host with the matching GraalVM installed:

### Example: Cross-build Native HelloWorld
```bash
# compile the java class for java 21:
javac HelloWorld.java

# build cross-compiled riscv64 binary
$GRAALVM_HOME/bin/native-image HelloWorld -H:CompilerBackend=llvm \
  -Dsvm.targetPlatformArch=riscv64 \
  -H:CAPCacheDir=<path-to-artifacts>/capcache \
  -H:CCompilerPath=<path-to-artifacts>/riscv-toolchain-glibc2.33/riscv/bin/riscv64-unknown-linux-gnu-gcc \
  -H:CustomLD=<path-to-artifacts>/riscv-toolchain-glibc2.33/riscv/bin/riscv64-unknown-linux-gnu-ld \
  -H:CLibraryPath=<path-to-artifacts>/static-libraries \
  --add-exports=jdk.internal.vm.ci/jdk.vm.ci.riscv64=org.graalvm.nativeimage.builder
```

This produces a Linux riscv64 dynamically linked binary, which can then be tested using the QEMU riscv64 environment.
