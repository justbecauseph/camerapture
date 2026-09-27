# Camerapture — NeoForge 1.21.1 reconstruction

This branch contains source reconstructed from the supplied Camerapture
`2.0.0-neoforge.2` JAR. It is a single-module NeoForge 1.21.1 project.
See [BUILD_PROVENANCE.md](BUILD_PROVENANCE.md) for the input hash, pinned
dependencies, and limits of the reconstruction.

Build with Java 21 and `./gradlew build`. The patched development JAR is
written to `build/libs/camerapture-2.0.0-neoforge.3-dev.jar`. Do not install it
alongside the original JAR, since both register the `camerapture` mod ID.

The frame-menu repair keeps the server menu tied to a live, nearby frame,
closes a client menu that has no matching visible screen, and guards screen
updates until its controls exist. These changes target the invisible menu
state reported after crouch-interacting with a frame using an empty hand.
Compilation and packaging do not establish that the rare interaction is
eliminated in a running client/server; that still needs in-game verification.

Original Camerapture author: chrrrs. This branch is derived from the supplied
NeoForge port artifact and retains its original mod metadata and assets.
