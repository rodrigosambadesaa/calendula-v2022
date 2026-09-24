![](https://tec.citius.usc.es/calendula/github-assets/calendula_promo_google_play.png)
# Calendula [![Android CI](https://github.com/rodrigosambadesaa/calendula-v2022/actions/workflows/android-ci.yml/badge.svg)](https://github.com/rodrigosambadesaa/calendula-v2022/actions/workflows/android-ci.yml)

> **Unofficial GitHub mirror/fork.** This repository mirrors the public `Calendula-v2022`
> source originally published by CiTIUS, Universidade de Santiago de Compostela, at
> `https://gitlab.citius.gal/calendula-mp/calendula-mp`. It is not an official CiTIUS
> or SERGAS repository. Upstream copyright and GNU GPLv3 licensing are preserved.

## Purpose of this fork

This repository is maintained independently by **Rodrigo Sambade** as a personal, unofficial
development fork of the public CiTIUS `Calendula-v2022` snapshot.

The goals of this fork are to:

- preserve an accessible GitHub copy of the public upstream source and its history;
- study, test and document the 2022 codebase;
- fix defects and security/robustness issues where they can be validated safely;
- modernize the Android project incrementally without obscuring the historical upstream code;
- experiment with improvements such as stronger connectivity handling and automated CI;
- prepare contributions that may be proposed back to the original CiTIUS project when appropriate.

This fork does **not** claim to be an official continuation, release channel or product of
CiTIUS, Universidade de Santiago de Compostela, SERGAS or Xunta de Galicia. Its maintainer
does not speak on behalf of those organizations. Original authorship, copyright notices and
GNU GPLv3 licensing remain preserved.

### Original upstream

The source mirrored here was originally published publicly by CiTIUS at:

- **Project:** `Calendula-v2022`
- **Upstream GitLab:** https://gitlab.citius.gal/calendula-mp/calendula-mp
- **Organization:** CiTIUS — Universidade de Santiago de Compostela

Development in this GitHub repository may diverge from that snapshot. When synchronizing
with upstream, the CiTIUS GitLab repository should be treated as the authoritative origin for
the original `Calendula-v2022` source.

### Keeping this fork synchronized

A local clone can keep both remotes explicitly:

```bash
git remote add upstream https://gitlab.citius.gal/calendula-mp/calendula-mp.git
git fetch upstream
```

Changes made in this GitHub fork are intentionally developed on separate branches. Upstream
history should be preserved rather than rewritten, so future CiTIUS changes can be compared,
merged or rebased deliberately.

Calendula is an Android assistant for personal medication management, aimed at those who have trouble following their medication regimen, forget to take their drugs, or have complex schedules that are difficult to remember.

The app is available for download in Google Play, F-Droid and Github.
<table>
    <tr>
        <td align="center"><a href="https://play.google.com/store/apps/details?id=es.usc.citius.servando.calendula"><img src="https://play.google.com/intl/en_us/badges/images/badge_new.png" alt="Get it on Google Play" ></a></td>
        <td align="center"><a href="https://f-droid.org/packages/es.usc.citius.servando.calendula/"><img src="https://gitlab.com/fdroid/artwork/raw/master/badge/get-it-on.png" alt="Get it on F-Droid" height="68"></a></td>
        <td align="center"><a href="https://github.com/citiususc/calendula/releases/latest"><img src="https://user-images.githubusercontent.com/663460/26973090-f8fdc986-4d14-11e7-995a-e7c5e79ed925.png" alt="Get it on Github" height="68"></a></td>
    </tr>
</table>

Visit our web page for more info  [https://citius.usc.es/calendula/](https://citius.usc.es/calendula/)

## Getting Started

These instructions will get you a copy of the project up and running on your local machine ready for development. If you want to help developing the app take a look to the contributing section, at the end.

### Development environment setup

We use [Android Studio](https://developer.android.com/studio/index.html) (the official Android IDE) for development, so we recommend it as the IDE to use in your development environment. Once you install Android Studio, you can use the Android SDK Manager to obtain the SDK tools, platforms, and other components you will need to start developing. The most important are:

* Android SDK Tools and Android SDK Platform-tools (upgrade to their last versions is usually a good idea).
* Android SDK Build-Tools 29.0.3.
* Android 10 (API Level 29) SDK Platform.
* Android Support Repository

You can also install other packages like emulators for running the app, if you don't have or don't want to use a real device. The current Gradle configuration uses a minimum SDK of *API level 18 (Android 4.3).*

### Building and installing the app

First of all you need to get the source code, so clone this repository  on your local machine:

```bash
git clone https://github.com/rodrigosambadesaa/calendula-v2022.git
cd calendula-v2022
```

Android Studio uses Gradle as the foundation of the build system, but it's not necessary to install it separately. Instead, you can use the included [Gradle Wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html). To build the app, open a terminal in the repository folder and run:

```bash
./gradlew clean assembleDevelopDebug
```
*Note: "developDebug" is the [build variant](https://developer.android.com/studio/build/build-variants.html) that we use for development. To see other variants, please check `Calendula/build.gradle`.*

Then you may install the app on a device or emulator:

```bash
adb install Calendula/build/outputs/apk/develop/debug/Calendula-develop-debug-*.apk
```

These tasks can also be executed from Android Studio with a few clicks.

## Historical upstream releases

This repository is a development mirror of the public CiTIUS `Calendula-v2022` snapshot.
The Google Play, F-Droid and GitHub release links above refer to the historical upstream
Calendula project and should not be interpreted as releases produced from this fork.

Check out the [contributing guidelines](CONTRIBUTING.md) for the historical upstream branching model.

## How does it look?

We try to follow [Material Design](https://material.google.com/#) principles. Take a look at the result!

  | <img src="https://tec.citius.usc.es/calendula/github-assets/home.png" width="230px"/>  | <img src="https://tec.citius.usc.es/calendula/github-assets/agenda.png" width="230px"/> | <img src="https://tec.citius.usc.es/calendula/github-assets/schedules.png" width="230px"/>
  |:---:|:---:|:---:|
  | <img src="https://tec.citius.usc.es/calendula/github-assets/aviso.png" width="230px"/> | <img src="https://tec.citius.usc.es/calendula/github-assets/navdrawer.png" width="230px"/> | <img src="https://tec.citius.usc.es/calendula/github-assets/profile.png" width="230px"/>

## Future work

We have a lot of development ideas, and we are open to newer ones. Below are some interesting features that could be very useful:

* Information about nearby pharmacies, their locations and timetables
* Trip assistant (how many pills I need for this weekend?)
* Introducing [gamification](https://en.wikipedia.org/wiki/Gamification) concepts to improve adherence.

## Artwork attribution

We are using the the following resources in the app:

* [People Vector Pack](http://www.freepik.com/free-vector/people-avatars_761436.htm) by [Freepik](http://www.freepik.com)
* [Baby](http://www.flaticon.com/free-icon/baby_136272), [Dog](http://www.flaticon.com/free-icon/dog_194178) and [cat](http://www.flaticon.com/free-icon/cat_194179) icons by <a href="https://www.flaticon.com/" title="Flaticon">Flaticon</a> (<a href="http://creativecommons.org/licenses/by/3.0/" title="Creative Commons BY 3.0" target="_blank">CC 3.0 BY</a></div>)
* [Alarm clock animation](https://dribbble.com/shots/1114887-Alarm-Clock-GIF) by  [Daan De Deckere](http://daandd.be/)

## Contributing

Feel free to fork and send a pull request if you want to contribute to this project! Notice that Calendula is licensed under the terms of the [GNU General Public License (v3)](LICENSE.md), so by submitting content to the Calendula repository, you release your work under the terms of this license.

Before starting, take a look at our [contribution guidelines](CONTRIBUTING.md).

### I would like to contribute, but I'm not a developer...

If you're not a developer but you want to help, don't worry! You can help [with app translations](CONTRIBUTING.md#help-with-app-translations), by [joining the BETA group](#app-versions), and [much more](CONTRIBUTING.md#i-would-like-to-contribute-but-im-not-a-developer)! Everyone is welcome!

## License

Copyright 2018 CITIUS - USC

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.
