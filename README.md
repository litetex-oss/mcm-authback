<!-- modrinth_exclude.start -->

[![Version](https://img.shields.io/modrinth/v/authback)](https://modrinth.com/mod/authback)
[![Build](https://img.shields.io/github/actions/workflow/status/litetex-oss/mcm-authback/check-build.yml?branch=dev)](https://github.com/litetex-oss/mcm-authback/actions/workflows/check-build.yml?query=branch%3Adev)

# AuthBack

<!-- modrinth_exclude.end -->

Allows you to play as normal even when Mojang's authentication servers are down.

#### How does it work?

1. The responses from Mojang's webservices/API are cached locally for some time.<br/>
If an outage occurs the previously cached responses are used.<br/>
For example this allows for your skin to be displayed properly.
2. Every client locally stores (or generates if not present) a keypair, which will be used as a fallback to authenticate with servers when Mojang's authentication servers are down (see [Public-key cryptography](https://en.wikipedia.org/wiki/Public-key_cryptography)).<br/>
Please note:
    * The server also needs to have the mod installed
    * You must have already joined the server once (during the past 36 days)

The mod is optional for the client and server.<br/>
However if one side lacks the mod joining a server will be impossible when Mojang's authentication servers/APIs are down.

## Usage

### Command

You can use the ``/authback`` command on servers with the mod to manage your public keys or those of other players if you are an admin.

### Configuration

#### Client

You can configure the mod via
* ``Options > Online... > AuthBack...``
* or if ModMenu is installed: ``Mods > AuthBack > Click Icon/Settings``

#### General

<details><summary>The configuration is dynamically loaded from (sorted by highest priority)</summary>

* Environment variables 
    * prefixed with ``AUTHBACK_<variant>`` 
        * where ``variant`` is either ``SERVER`` OR ``CLIENT``
    * prefixed with ``AUTHBACK_``
    * all properties are in UPPERCASE and use `_` (instead of `.`) as delimiter
* System properties
    * prefixed with ``authback.<variant>`` 
        * where ``variant`` is either ``server`` OR ``client``
    * prefixed with ``authback.``
* A configuration file located in ``.config/authback-<variant>.json``
    * where ``variant`` is either ``server`` OR ``client``

</details>

<details><summary>Full list of configuration options</summary>

_Please note that the preconfigured values usually work out of the box.<br/>_
_You should know exactly what you're doing when doing modifications._

##### Common (Client and Server)

| Property | Type | Default | Notes |
| --- | --- | --- | --- |
| `global-public-keys-cache.default-reuse-minutes` | `int` | `120` (2h) | The response for the global public keys of the Mojang's API barely ever changes. As of writing this documentation it has stayed the same the past 2+ years. This option instructs the mod to re-use the last saved response for the specified amount of minutes instead of contacting the API again. Using the cached response is a lot faster and saves network traffic during frequent game restarts. |
| `game-profiles.delete-after-days` | `int` | `36` | 36 days was choosen as the default because when a player changes their username the name will be unavailable for 37 days |
| `game-profiles.max-cache-size` | `int` | `250` | Maximum amount of game profiles to keep in the cache. If the size exceeds the maximum the oldest entries will be removed until the list is at 90% of the configured maximum. |
| `username-to-id-resolver.use-vanilla` | `bool` | `false` | Use the original/"vanilla" username-to-id resolver |
| `username-to-id-resolver.expire-after-days` | `int` | `36` | Days after which the cache entry will be deleted.<br/>36 days was choosen as the default because when a player changes their username the name will be unavailable for 37 days.<br/>The vanilla implementation uses 1 month. |
| `username-to-id-resolver.refresh-before-expire-days` | `int` | `14` | Days before the expiration when a cached entry will be refreshed when it's accessed. |
| `username-to-id-resolver.max-cache-size` | `int` | `1000` | Maximum amount of usernames and ids to keep in the cache. If the size exceeds the maximum the oldest entries will be removed until the list is at 90% of the configured maximum.  |
| `username-to-id-resolver.resolve-offline-users-by-default` | `bool` | `true` | Should offline users (e.g. unvalidated accounts) be cached?.<br/>Please note <ul><li>the offline-user cache is completely separated from the online-user cache and will never be persisted.</li> <li>This is only the initial value</li> <li>Clients will always resolve offline users and server will only do so when `online-mode` was disabled</li> </ul> |
| `username-to-id-resolver.update-on-game-profile-fetch` | `bool` | `true` | When a gameprofile is fetched: Should the information be relayed to the username-to-id-resolver/cache?<br/> The cache will only be updated if required (e.g. unknown username/id or non-existing entry) |
| `username-to-id-resolver.use-game-profile-cache` | `bool` | `true` | Uses  GameProfileCacheManager as secondary cache when all primary caches fail.<br/>Usage should be extremely rare but can happen if the cache was e.g. corrupted |
| `skip-extract-profile-action-types` | `bool` | `false` | Debug-Option |

##### Server

| Property | Type | Default | Notes |
| --- | --- | --- | --- |
| `keys.max-keys-per-player` | `int` | `3` | Maximum amount of public keys to store per player |
| `keys.delete-after-unused-days` | `int` | `36` | If a user does not login with a public key for this amount of time the key will be deleted. 36 days was choosen as the default because when a player changes their username the name will be unavailable for 37 days |
| `fallback-auth.allow-always` | `bool` | `true` | Always allows the use of fallback authentication. Set to `false` if you only want to allow fallback authentication when the server can't communicate with the Mojang's API during a player login |
| `fallback-auth.rate-limit.requests-per-ip-per-minute` | `int` | `20` | The default allows for requests every 3s. If the value is set to 0 or less the rate limiter will be disabled |
| `fallback-auth.rate-limit.bucket-size` | `int` | `1000` | Amount of IP addresses to store (in memory) |
| `fallback-auth.rate-limit.ignore-private-addresses` | `bool` | `true` | Should [private IP addresses](https://en.wikipedia.org/wiki/Private_network) NOT be rate limited? |
| `fallback-auth.rate-limit.ipv6-network-prefix-bytes` | `int` | `8` | Network prefix bytes (not bits!) for IPv6. The default `8` resolves to `/64`. |
| `force-disable-enforce-secure-profile` | `bool` | `true` | Forces `enforce-secure-profile` to be disabled |
| `skip-old-user-conversion` | `bool` | `true` | Skips the migration of user files used by servers before `1.7.6` (released 2014-04). It's extremely unlikely that this is needed by a server and requires contacting the Mojang's API. Therefore the migration is skipped by default |

##### Client

All client specific options can be changed / are described in the game options UI (see above).

</details>

## FAQ

### Where can I find my public key?

The public key can be found/copied under
* ``Options > Online... > AuthBack... > Key management``
* or if ModMenu is installed: ``Mods > AuthBack > Click Icon/Settings > Key management``

### The authentification servers are down, I installed the mod on my server but I can't join

This is because the server doesn't know your public key.

You can fix this in the following way:
1. Make sure that you logged onto the server less than a month ago 
    * that is because the entries in `usercache.json` - which will be used in such a case - will expire after a month
2. Make sure that you can run commands via the server console/RCON
3. Add the mod to your local client/game and launch it
4. and copy the public key from there (see above for details)
5. Then associate the public key on the server with your account: 
    * `/authback public_key add name <yourName> <yourPublicKey>`
    * or `/authback public_key add id <yourUUID> <yourPublicKey>`

You should now be able to log in as usual.<br/>
Note however that profile related content like skins will not not be displayed.

It's also recommended to restart the server once the outage is over to correctly load existing profiles.

### The authentification servers are down, my friend got the mod but they can't join my server

This is because they never joined with the mod before.

You can fix this in the following way if you are an Admin:
1. Tell your friend to launch the game with the mod
2. and to send their public key (see "Where can I find my public key?" above) to you
3. Associate the public key on the server with them by running `/authback public_key add name <yourFriendsPlayerName> <yourFriendsPublicKey>`

### How can I test fallback authentication?

1. Ensure that the server knows your public key (you can check that on the server using `/authback public_key list`)
2. Ensure that `fallback-auth.allow-always` is enabled on the server (it should be enabled by default)
3. Start the client in offline mode
4. Enable `Suppress any joinServer error` in the client options
5. You should now be able to join the server using fallback authentication. Check the log for details.

### Where does the mod store it's data?

In the game directory (e.g. `%APPDATA%\.minecraft`) inside the ``.mods\authback`` directory.

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
