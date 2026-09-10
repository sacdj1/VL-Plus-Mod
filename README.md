
<p align="center">
<img src="src/main/resources/assets/meteor-client/icon.png" alt="vlplus-logo" width="15%"/>
</p>

<h1 align="center">VL+</h1>
<p align="center">A Minecraft Fabric utility mod for the Ventureland server, forked from <a href="https://github.com/MeteorDevelopment/meteor-client">Meteor Client</a>.</p>

<div align="center">
    <a href="https://discord.gg/FNUFVxjcRS"><img src="https://img.shields.io/discord/0?logo=discord&label=Ventureland" alt="Discord"/></a>
    <br>
    <img src="https://img.shields.io/github/last-commit/sacdj1/VL-Plus-Mod" alt="GitHub last commit"/>
    <img src="https://img.shields.io/github/commit-activity/w/sacdj1/VL-Plus-Mod" alt="GitHub commit activity"/>
</div>

## About

VL+ is a fork of [Meteor Client](https://github.com/MeteorDevelopment/meteor-client) - a Fabric utility mod - customized specifically for the Ventureland server. It keeps the vast majority of Meteor's own modules and features, and adds a curated set of Ventureland-specific modules, HUD elements, and quality-of-life changes on top (XP bar/level recoloring, alert rules with per-rule sounds, Discord Rich Presence tuned for the server, relocatable vanilla HUD pieces, and more).

This is **not** an official Meteor Client build, and it's not affiliated with or endorsed by MeteorDevelopment.

## Usage

### Building
- Clone this repository
- Run `./gradlew build`
- The built jar is `build/libs/vlplus-<version>.jar`

### Installation
Requires [Fabric Loader](https://fabricmc.net/use/) for Minecraft 1.21.1. Drop the built (or released) jar into your `mods` folder like any other Fabric mod.

## Bugs and Suggestions
Bug reports and suggestions should go in this repo's [issue tracker](https://github.com/sacdj1/VL-Plus-Mod/issues).  
Please include as much detail as you can (steps to reproduce, logs/crash reports if relevant) to help get it resolved faster.  
If you don't get a response here, reach out on [Discord](https://discord.gg/FNUFVxjcRS) instead.

## Credits
Built on top of [Meteor Client](https://github.com/MeteorDevelopment/meteor-client) by [MeteorDevelopment](https://github.com/MeteorDevelopment) - the overwhelming majority of this mod's code, architecture, and features come directly from their work.  
The [Fabric Team](https://github.com/FabricMC) for [Fabric](https://github.com/FabricMC/fabric-loader) and [Yarn](https://github.com/FabricMC/yarn)

## Licensing
This project is licensed under the [GNU General Public License v3.0](https://www.gnu.org/licenses/gpl-3.0.en.html), the same license as upstream Meteor Client.

If you use **ANY** code from this source:
- You must disclose the source code of your modified work and the source code you took from this project. This means you are not allowed to use code from this project (even partially) in a closed-source and/or obfuscated application.
- You must state clearly and obviously to all end users that you are using code from this project.
- Your application must also be licensed under the same license.
