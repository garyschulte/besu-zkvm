# Building or Rebuilding graal 

For riscv64, getting a working combination of labs-openjdk and graalvm release can be
difficult since the llvm backend feature is still experimental and is broken in recent 
graal releases.  

Currently, the most viable version of graal and openjdk for building with the llvm feature are:
* graal 24.0.2 - https://github.com/oracle/graal/releases/tag/graal-24.0.2
* labs-openjdk-21 - https://github.com/graalvm/labs-openjdk-21/releases/tag/jvmci-23.1-b33

The initial debian target environment is glibc based, and the dev build can be found in 
/build of this (8gb) image:
 ghcr.io/consensys/besu-zkvm-artifacts:debian-dev-qemu-image-riscv64

If you need to regenerate a capcache, or need to rebuild graal or static libraries, or want to
experiment with a different verion of graal or the jdk, 
* download a kernel (e.g. ghcr.io/consensys/besu-zkvm-artifacts:qemu-linux-riscv64-kernel-6.12 ) 
* download the build image (ghcr.io/consensys/besu-zkvm-artifacts:debian-dev-qemu-image-riscv64)

And run via qemu-system-riscv64:

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

Once started, switch to /build where the sources and config are still setup, then setup the path and rebuild graal:
```bash
export JAVA_HOME=/build/labsjdk-ce-21.0.2-jvmci-23.1-b33
export PATH=$JAVA_HOME/bin:~/.pyenv/shims:$PATH
cd /build/graal/substratevm
# build graal with the llvm feature:
mx --dynamicimports /substratevm build

#setup a GRAALVM_HOME for the built graal:
export GRAALVM_HOME=$(mx --dynamicimports /substratevm graalvm-home)
```


Packaging and testing are manual on riscv, since the build suite does not work completely on riscv64.

Similar steps taken for debian/gnu can and should be repeated for an Alpine/MUSL based system.  TBD.
