<!-- modrinth_exclude.start -->

[![Version](https://img.shields.io/modrinth/v/authback)](https://modrinth.com/mod/authback)
[![Build](https://img.shields.io/github/actions/workflow/status/litetex-oss/mcm-authback/check-build.yml?branch=dev)](https://github.com/litetex-oss/mcm-authback/actions/workflows/check-build.yml?query=branch%3Adev)

# AuthBack

<!-- modrinth_exclude.end -->

Allows you play as normal even when Mojang's authentication servers are down.

#### How does it work?

1. The responses from Mojang's webservices are cached locally for some time.<br/>
If an outage occurs the previously cached responses are used.<br/>
For example this allows for your skin to be displayed properly.
2. Every client locally stores (or generates if not present) a keypair, which will be used as a fallback to authenticate with servers when Mojang's authentication servers are down (see [Public-key cryptography](https://en.wikipedia.org/wiki/Public-key_cryptography)).<br/>
Please note:
    * The server also needs to have the mod installed
    * You must have already joined the server once (during the past 36 days)

The mod is optional for the client and server.
However if one side lacks the mod joining a server will be impossible when Mojang's authentication servers are down.

## Usage

### Command

You can use the ``/authback`` command to e.g. manage your public keys or those of other players if you are an admin.

### Configuration

#### General

The configuration is dynamically loaded from
* Environment variables prefixed with ``AUTHBACK_<variant>`` 
    * where ``variant`` is either ``SERVER`` OR ``CLIENT``
* Environment variables prefixed with ``AUTHBACK_``
* Environment variables prefixed with ``authback.<variant>`` 
    * where ``variant`` is either ``server`` OR ``client``
* System properties prefixed with ``authback.``
* A configuration file located in ``.config/authback-<variant>.json``
    * where ``variant`` is either ``server`` OR ``client``

#### Client

You can edit the most important configuration via the game options in the UI via
* ``Options > Online... > AuthBack...``
* If ModMenu is installed: ``Mods > AuthBack > Click Icon/Settings``

## FAQ

### The authentification servers are down, my friend got the mod but they can't join my server

This is because they never joined with the mod before.
You can fix this in the following way if you are an Admin:
1. Tell your friend to launch the game with the mod
2. Tell them to send their public key to you <!-- ! TODO (can be found in the UI under ...) -->
3. Associate the public key on the server with them by running `/authback public_key add name <yourFriendsPlayerName> <yourFriendsPublicKey>`

### Where is the mod data stored?

In the game directory (e.g. `%APDDATA%\.minecraft`) inside the ``.authback`` directory

<!-- modrinth_exclude.start -->

## Installation
[Installation guide for the latest release](https://github.com/litetex-oss/mcm-authback/releases/latest#Installation)

### Usage in other mods

Add the following to ``build.gradle``:
```groovy
dependencies {
    modImplementation 'net.litetex.mcm:authback:<version>'
    // Further documentation: https://wiki.fabricmc.net/documentation:fabric_loom
}
```

> [!NOTE]
> The contents are hosted on [Maven Central](https://repo.maven.apache.org/maven2/net/litetex/mcm/). You shouldn't have to change anything as this is the default maven repo.<br/>
> If this somehow shouldn't work you can also try [Modrinth Maven](https://support.modrinth.com/en/articles/8801191-modrinth-maven).

## Contributing
See the [contributing guide](./CONTRIBUTING.md) for detailed instructions on how to get started with our project.

<!-- modrinth_exclude.end -->
