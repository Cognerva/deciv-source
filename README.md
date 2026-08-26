# DeCiv — Hex Empire Strategy

DeCiv is an independent iOS port of Unciv, an open-source turn-based strategy game. It is not affiliated with, endorsed by, or supported by the Unciv project or its authors.

Unciv is © yairm210 and contributors, licensed under the Mozilla Public License 2.0. DeCiv's modified source is published at [github.com/Cognerva/deciv-source](https://github.com/Cognerva/deciv-source).

Civilization is a trademark of Take-Two Interactive. DeCiv is not associated with or endorsed by Take-Two Interactive.

The app can load Unciv-format mods. The free game content, rules, saves, settings, multiplayer, and mod support are not paywalled; optional support purchases are limited to iOS extras such as alternate icons.

See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for dependency and icon attributions.

Under MPL-2.0 §3.2(a), the corresponding source for the distributed iOS build is available in this repository at the matching immutable [DeCiv source tag](https://github.com/Cognerva/deciv-source/tree/v4.21.11-build1274). The source snapshot includes the upstream notices, the DeCiv modifications, and the icon artwork attributions listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).


## What is this?

DeCiv is a moddability-focused iOS strategy game built with [libGDX](https://github.com/libgdx/libgdx). Its rules and data are derived from the open-source Unciv codebase, with iOS-specific porting, branding, support, and release work maintained separately.

## Is this any good?

DeCiv is intended to be small, fast, moddable, and approachable while retaining the depth of a 4X strategy game.

## How do I install?

- **iOS** - DeCiv is built and staged through App Store Connect from this source repository.
- **Source builds** - Follow the workspace instructions in `IOS_PROJECT_WORKFLOW.md` and the app's local `AGENTS.md`.
- **Original project** - See [Unciv](https://github.com/yairm210/Unciv/) for its upstream platform releases and documentation.

## What's the roadmap?

In this order:

* Polish!
    * UI+UX improvements ([suggestions welcome!](https://github.com/yairm210/Unciv/issues/new?assignees=&labels=feature&template=feature_request.md&title=Feature+request%3A+))
    * Better automation, AI etc. in-game
* G&K mechanics - see [#4697](https://www.github.com/yairm210/Unciv/issues/4697)
* BNW mechanics - trade routes, world congress, etc.

## Contributing

Programmers can start with the workspace workflow and the [Unciv developer documentation](https://yairm210.github.io/Unciv/Developers/Building-Locally/).

Translation structure and upstream guidance are documented [here](https://yairm210.github.io/Unciv/Translating/Translating/).

Modders can start [here](https://yairm210.github.io/Unciv/Modders/Mods/); DeCiv accepts Unciv-format mod data.

For DeCiv-specific issues, contact [support@cognerva.com](mailto:support@cognerva.com). Changes to the shared source should preserve the upstream license and attribution.

The original project remains the authoritative place for upstream issues and community discussion.


## FAQ

### How about iOS?

DeCiv is the independently maintained iOS port. iOS-specific bugs and support requests belong with Cognerva at [support@cognerva.com](mailto:support@cognerva.com), while upstream gameplay and rules questions belong with the original project.

### Steam release?

Steam has decided that they don't want to host Unciv, they probably don't want to risk legal issues with Firaxis (although those should be non-existent, see below).
 
### Will you implement {feature}?

If it is part of the reference game or an existing Unciv ruleset, it may be available in DeCiv or through a mod.

If not, then the feature won't be added to the base game - possibly it will be added as a way to mod the game, which is constantly expanding.

#### Why not? This is its own game, why not add features that aren't in the reference game?

Having a clear vision is important for actually getting things done.

Anyone can make a suggestion. Not all are good, viable, or simple. Not many can actually implement stuff.

As an open source project, this stuff is done in our spare time, of which there isn't much.

We need a clear-cut criteria to decide what to work on and what not to work on.

#### Will you implement a later reference-game edition?

DeCiv focuses on its iOS port and on loading compatible Unciv-format mods.

### How can I learn to play? Where's the wiki?

All the tutorial information is available in-game at menu > civilopedia > tutorials

The in-app tutorials and Civilopedia explain the game. For historical reference, the [Civilization wiki](https://civilization.fandom.com/wiki/) covers the game family that inspired the original ruleset, but DeCiv is an independent project.

Alternatively, you could [join us on Discord](https://discord.gg/bjrB4Xw) and ask there =D

### How does DeCiv relate to the reference game?

According to the [US Copyright Office FL-108](https://upload.wikimedia.org/wikipedia/commons/9/96/U.S._Copyright_Office_fl108.pdf), intellectual property rights *do not* apply to mechanics - as I'm sure you know, there are a billion Flappy Bird knockoffs.

DeCiv does not ship assets from the reference commercial game and does not claim affiliation with its publisher. It uses open-source code and original/attributed assets as described above. This is a project description, not legal advice; consult qualified counsel for legal questions.

## Run with Docker [![Docker](https://github.com/yairm210/Unciv/actions/workflows/dockerPublish.yml/badge.svg)](https://github.com/yairm210/Unciv/actions/workflows/dockerPublish.yml)

If you have docker compose installed:

 ```$ docker compose build && docker compose up```

and then goto http://localhost:6901/vnc.html?password=headless

If just docker:

```$ docker build . -t unciv && docker run -d -p 6901:6901 -p 5901:5901 unciv  ```

Or just use our already built one:

```$ docker run -d -p 6901:6901 -p 5901:5901 ghcr.io/yairm210/unciv ```

and then goto http://localhost:6901/vnc.html?password=headless
## [Credits and 3rd parties](docs/Credits.md)
