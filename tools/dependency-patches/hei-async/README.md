# HEI asynchronous completion repair

This separately built dependency repair prevents HEI search completion from waiting
on Minecraft's task queue while GroovyScript holds that queue and waits for search
termination. It is not part of Reloaded's product sources or a runtime mixin.

Upstream: HadEnoughItems 4.36.1, source commit
`e110d8b45e7a847a09d9e62fd86659baa17d82d2`. Original production SHA-256:
`c9b812ac3396f3628ca314f10dd775e0f0098cdbaec56be13b4a4f57b70c4443`.
The source and MIT notice are retained here with the minimal patch and regression tests.

From the repository root, using the Java 25 toolchain:

```sh
./gradlew -I tools/dependency-patches/hei-async/build.init.gradle testHeiAsyncRepair reobfHeiAsyncRepairJar
python tools/dependency-patches/hei-async/package.py --original PATH_TO_ORIGINAL_HEI --output NEW_OUTPUT_DIRECTORY
```

On Windows, use `gradlew.bat` in place of `./gradlew`.

The packaging command validates both the upstream hash and the single replaced
class. It preserves every other entry and emits a digest receipt. The qualified
repair is identified by its hash, not merely by HEI's embedded 4.36.1 version.
Its SHA-256 is `af38fd9a883f629a4be16a3eac14b95ec5d9b3bb91802b994b8cc7ae19361d6d`,
written as `jei-4.36.1-async-completion.2.jar`. The `.1` name belongs only to the
earlier `f75d36d8...` bytes; give any future byte change a new suffix.
This revision logs completion-callback failures instead of dropping them.
The replacement class retains upstream Java 8 bytecode compatibility so the Java 25
toolchain reproduces the qualified dependency exactly. This does not add a Java 8
lane to Reloaded's Java 25 product.
Publishing or selecting a maintained download location is release delivery work.
Do not advertise unpatched 4.36.1 as passing the asynchronous reload test.
