# Third-party licenses

Custom Dialog's project-authored code and resources are licensed under the
[MIT License](LICENSE), copyright (c) 2026 Limo. Third-party components retain
their own licenses; the project's MIT license does not replace them.

## Release application

The resolved `releaseRuntimeClasspath` contains:

| Component | Version | License |
| --- | --- | --- |
| Kotlin standard library | 2.2.10 | Apache-2.0 |
| JetBrains annotations (transitive) | 13.0 | Apache-2.0 |

The APK includes [third-party notices](app/src/main/assets/licenses/NOTICE.txt),
the [Apache 2.0 license](app/src/main/assets/licenses/Apache-2.0.txt), and a
[copy of the project MIT license](app/src/main/assets/licenses/MIT.txt) under
`assets/licenses/`. Keep the MIT copy synchronized with the root `LICENSE`.
Recheck dependency licenses when updating the build or dependencies.

The Android framework and default Android icon are referenced from the device's
system resources; no Android platform binary or copied robot image is vendored
in this repository.

## Gradle Wrapper

`gradlew`, `gradlew.bat`, and `gradle/wrapper/gradle-wrapper.jar` are Gradle
components licensed under Apache-2.0. Keep the existing copyright and license
headers in both scripts. The wrapper JAR includes `META-INF/LICENSE`; a readable
copy is also available in the Apache 2.0 license linked above.

Upstream: https://github.com/gradle/gradle
License information: https://docs.gradle.org/current/userguide/licenses.html

## Build and test dependencies

These tools and libraries are downloaded by Gradle rather than vendored here.
Test dependencies are not part of the release APK.

| Direct dependency/tool | Version | License |
| --- | --- | --- |
| Android Gradle Plugin | 9.4.1 | Apache-2.0 |
| JUnit 4 | 4.13.2 | EPL-1.0 |
| AndroidX Test JUnit extension | 1.3.0 | Apache-2.0 |
| AndroidX Espresso Core | 3.7.0 | Apache-2.0 |

This table lists direct build/test dependencies, not their complete transitive
graph. If distributing test APKs or build-tool distributions, include the
licenses and notices for their resolved transitive dependencies as well.

Sources: dependency POM license declarations in the resolved Gradle artifacts;
[Kotlin](https://github.com/JetBrains/kotlin/tree/v2.2.10/license),
[Android Gradle Plugin](https://android.googlesource.com/platform/tools/base/+/refs/heads/mirror-goog-studio-main/NOTICE),
[JUnit 4](https://github.com/junit-team/junit4/blob/r4.13.2/LICENSE-junit.txt),
[AndroidX Test](https://github.com/android/android-test/blob/main/LICENSE).
