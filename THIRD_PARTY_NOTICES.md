# Third-party notices

This source snapshot contains or builds with the following third-party
components. Their licenses apply to the components themselves; they do not
replace the Mozilla Public License notice in `LICENSE` for the Unciv/Deciv
source files.

| Component | Version in this snapshot | License / notice |
| --- | --- | --- |
| libGDX | 1.14.2 | [Apache License 2.0](https://github.com/libgdx/libgdx/blob/master/LICENSE) |
| Kotlin and Kotlin reflection | 2.4.10 | [Apache License 2.0](https://github.com/JetBrains/kotlin/blob/master/license/LICENSE.txt) |
| kotlinx.coroutines | 1.10.2 | [Apache License 2.0](https://github.com/Kotlin/kotlinx.coroutines/blob/master/LICENSE.txt) |
| kotlinx.serialization | version brought by the Kotlin toolchain | [Apache License 2.0](https://github.com/Kotlin/kotlinx.serialization/blob/master/LICENSE.txt) |
| Ktor client/server libraries | 3.2.3 | [Apache License 2.0](https://github.com/ktorio/ktor/blob/main/LICENSE) and the project's [third-party notices](https://github.com/ktorio/ktor/blob/main/THIRDPARTY.md) |
| ThreeTen-Backport | 1.7.1 | [BSD 3-Clause](https://github.com/ThreeTen/threetenbp/blob/main/LICENSE.txt) |
| RoboVM/MobiVM toolchain | configured by `ios/build.gradle` | See the [RoboVM license notes](https://github.com/MobiVM/robovm#license); compiler and runtime terms differ |

## Icon artwork

The DeCiv icon pack includes these Noun Project assets, used under their recorded
CC BY terms:

- Hexagon — kareemovic, Noun Project (CC BY)
- Civilization / Monument — Eucalyp, Noun Project (CC BY)
- Civilization / Forum — Eucalyp, Noun Project (CC BY)
- Mayan Pyramid — WR Graphic Garage, Noun Project (CC BY)
- Sphinx — 1516, Noun Project (CC BY)

Gradle is the authoritative dependency list. When a dependency is upgraded,
recheck its published license and update this file before creating the next
source tag. App Store metadata, signing configuration, and release-only assets
belong in the private `Cognerva/deciv` repository, not here.
