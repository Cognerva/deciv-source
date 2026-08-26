# Deciv source and release workflow

This repository is the complete source snapshot for the Deciv app. It is the
repository that must be published for the MPL-covered Unciv code and all of
our modifications to that code.

## Repository layout

Keep the two Git repositories as sibling checkouts under `unciv-port/`:

```text
unciv-port/
├── deciv-source/   # public source snapshot
└── deciv/          # private App Store/release repository
```

The current source checkout may still be named `unciv` during the migration.

This snapshot is based on upstream `yairm210/Unciv` commit
`241c62f53` (the checkout used for Deciv 4.21.11 / build 1254). Preserve that
provenance when creating later source snapshots, even if the public repository
does not carry the upstream repository's full history.

The legacy upstream checkout also contained a root `keystore.jks`. It is
intentionally excluded from Deciv source snapshots because it is signing
material, not application source. Keep all real signing credentials in the
private release environment.

`Cognerva/deciv-source` must contain:

- the complete upstream source at the exact revision used for the app;
- every modified or newly added MPL-covered source file;
- the iOS module, RoboVM configuration, build files, `LICENSE`, and
  third-party notices; and
- an immutable version/build tag matching the distributed app, such as
  `v4.21.11-build1254`.

It must never be reduced to a patch or a list of changed files. Make the
repository public, and push the matching tag, before distributing the binary.

`Cognerva/deciv` must contain only release-owned material: App Store metadata,
CI/release scripts, screenshot/release assets, and release configuration. It
must pin and build from a public `deciv-source` tag, rather than carrying
unpublished changes to Unciv core or other MPL-covered files. Keep signing
credentials and secrets out of the source repository; use the private release
repository only for approved references or encrypted secret handling.

## Release checklist

1. Finish and verify the source checkout, including the iOS build.
2. Commit the complete source snapshot to `deciv-source` and create the tag
   used by the binary.
3. Confirm the tag is publicly readable before TestFlight/App Store release.
4. Pin the private `deciv` release configuration to that exact tag and build
   from it. Do not build release code from a dirty or divergent private copy.
5. Keep the About/legal link in `AboutTab` pointed at
   `https://github.com/Cognerva/deciv-source/tree/v<version>` and update it
   whenever the tag convention changes.
6. From the workspace root, run the required verification and TestFlight
   staging commands. App Review submission or release still requires an
   explicit request.

The public source tag must exist before the corresponding app binary is
distributed; App Store Connect does not host the source required by the MPL.

## Fast iOS smoke loop

The iOS module includes a local-only launch harness. RoboVM's
`launchIPhoneSimulator` task performs the native compile and installs the app;
stop that task after the normal app has launched, then use `simctl launch` to
run a deterministic scene without manually tapping through the UI:

```bash
SIMULATOR_UDID=1756559E-16EA-47C3-8417-F8A37265E319
xcrun simctl boot "$SIMULATOR_UDID" || true
xcrun simctl bootstatus "$SIMULATOR_UDID" -b
env GRADLE_USER_HOME=/private/tmp/unciv-gradle ./gradlew :ios:launchIPhoneSimulator --no-daemon --console=plain
xcrun simctl launch --console --terminate-running-process "$SIMULATOR_UDID" \
  com.cognerva.unciv --screenshot-scene new-game-start
```

The Gradle task is needed after source changes; the `simctl` command is the
fast repeat step against the already-installed binary. Available local scenes
include `new-game-start`, `mods`, `options-display`, `world`, `civilopedia`,
`map-editor`, and `main-menu`. `new-game-start` invokes the same asynchronous
Start Game action as the button, so its console output is the useful regression
signal. Always terminate the app and shut down the simulator after testing:

```bash
xcrun simctl terminate "$SIMULATOR_UDID" com.cognerva.unciv || true
xcrun simctl shutdown "$SIMULATOR_UDID" || true
ps -axo pid,etime,command | rg '/Unciv( |$)' || true
```
