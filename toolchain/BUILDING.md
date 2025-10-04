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


## Notes/errata on building Graal on Riscv

building labs-openjdk-** for riscv can be challenging, especially since they need to built with 
static libraries to support graal.  Specifically static libs for:
* libjava.a 
* libjvm.a 
* libverify.a

### Building earlier versions of labs-openjdk-21, 22
For reference, a workable recipe for building earlier labs-openjdk build with static libs on debian-minbase riscv64:
```bash
       bash configure \
            --with-debug-level=release \
            --disable-warnings-as-errors \
            --with-native-debug-symbols=none \
            --with-jvm-features=jvmci \
            --with-jvm-variants=server


       make jdk-image static-libs static-libs-image jmods
       export STATIC_JDK_ROOT=/build/labs-openjdk-22/build/linux-riscv64-server-release
       export STATIC_JDK_LIBS=$STATIC_JDK_ROOT/images/static-libs/lib/
       export STATIC_JDK_PATH=$STATIC_JDK_ROOT/images/jdk
       export JAVA_HOME=$STATIC_JDK_PATH


       # do a DIRTY static-jdk-image, 
       # TODO: copy the static-jdk-image makefile task from labs-openjdk HEAD into the build for 23.0.2
       # copy static libs into the jdk:
        cp $STATIC_JDK_LIBS/server/libjvm.a $STATIC_JDK_PATH/lib/
        cp $STATIC_JDK_LIBS/libjava.a $STATIC_JDK_PATH/lib/
        cp $STATIC_JDK_LIBS/libverify.a $STATIC_JDK_PATH/lib/
```

More recent versions of labs-openjdk introduce a make target of static-jdk-image, which simplify the process
but do not work with the versions of graal with a working llvm feature.  Finding the correct combination is 
a challenge.  

### Using pre-built release builds of labs-openjdk-**
Riscv64 builds of labs-openjdk are scarce, and finding one that works with graal 24.0.2 is even rarer.

However, the the last release build of [labs-openjdk-21](https://github.com/graalvm/labs-openjdk-21/releases/tag/jvmci-23.1-b33), it makes the build smoother. One 
build issue may crop up regarding debug symbols. If during the build you encounter errors such as:

```
errors during build.
  
    ld.lld: error: /build/graal/truffle/mxbuild/linux-riscv64/libffi/libffi-build/.libs/libffi.a(prep_cif.o):(.debug_rnglists+0x17): unknown relocation (61) against symbol .Ltext0 ld.lld: error: too many errors emitted, stopping now (use --error-limit=0 to see all errors) clang-16: error: linker command failed with exit code 1 (use -v to see invocation) ninja: build stopped: subcommand failed.
```

The build process has generated Makefile with debug symbols enabled and llvm is unable to parse the generated shared libs.  
The build tool seems to ignore CFLAGS and CXXFLAGS, so it is most expedient to just remove debug symbols from the 
build in the generated Makefile, replacing all of the -g switches with -g0: 

        /build/graal/truffle/mxbuild/linux-riscv64/libffi/libffi-build

and re-reun make clean; make in the ffi folder.  Then restart the build and it will complete as usual
 




