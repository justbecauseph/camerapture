# NeoForge 1.21.1 source and build provenance

The source and resources under `src/main/` start from a decompilation of the
supplied artifact:

- File: `C:\Users\markj\Downloads\Camerapture-2.0.0-neoforge.2+mc1.21.1-neoforge.jar`
- SHA-256: `b2af45bf8f4ab3c4d9b50450c1839e8e2c6a9239ecfb5e97a44b23af15dcce98`
- Embedded metadata: Camerapture `2.0.0-neoforge.2`, Minecraft `1.21.1`
- Decompiler: Vineflower 1.12.0; relevant bytecode was checked against the
  same JAR with `javap`

This branch rebuilds that NeoForge artifact layout from standard
`src/main/java` and `src/main/resources` source sets. Its build pins Minecraft
1.21.1, NeoForge 21.1.243, ModDevGradle 2.0.142, Gradle 8.10, and Java 21.
NeoForge 21.1.243 is the locally available runtime/source reference used for
the frame-menu audit; it was not verified as the affected server's installed
NeoForge build.

The original mod embeds `webp4j-core` 2.1.1, so the Gradle build retains it as a
Jar-in-Jar dependency. Cloth Config, Jade, and First Person Model are compile
APIs for optional integrations. The mixin configuration files and NeoForge mod
metadata remain in `src/main/resources`. An access transformer replaces the
source JAR's access widener for `ItemStackComponentizationFix.ItemStackData`.

The reconstructed and patched candidate uses version `2.0.0-neoforge.3-dev`
so it can be distinguished from the supplied `2.0.0-neoforge.2` JAR.

Use `./gradlew build` to compile and package the source. Decompiled source may
need manual repair where the decompiler could not recover Java syntax or
semantics. A successful build establishes compilation and packaging only; it
does not establish client/server launch, in-game behavior, or deployment.
