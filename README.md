<!-- modrinth_exclude.start -->

[![Version](https://img.shields.io/modrinth/v/authback)](https://modrinth.com/mod/authback)
[![Build](https://img.shields.io/github/actions/workflow/status/litetex-oss/mcm-authback/check-build.yml?branch=dev)](https://github.com/litetex-oss/mcm-authback/actions/workflows/check-build.yml?query=branch%3Adev)

# AuthBack

<!-- modrinth_exclude.end -->

_UNSTABLE: Currently still in development_

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

<!-- modrinth_exclude.start -->

TODO/WIP:
* Config UI for client
* Command for server ops to manage public keys
* Describe configuration options

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
