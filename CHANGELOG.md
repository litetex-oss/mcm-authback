# 1.5.0
* [Server] Added `disable-legacy-query-handler`
* [Client] Reconfigure some initial option values to be less annoying

# 1.4.0
* Replaced default implementation of `UserNameToIdResolver` (`usercache.json`) with a better alternative that is more efficient and prevents cache corruption
* Fix NPE when a new user intially joins a server
* Added safety check for player name validation

# 1.3.0
* [Client] Added more configuration options for UserAPI
    * Can now be completely replaced by a dummy
* [Server] Added `force-disable-enforce-secure-profile`
* Correctly handle account name changes
* Improved logging

# 1.2.1
* Disabled useless server related network traffic
    * Enabled `block-address-check` by default as this causes laggy reverse DNS lookups when using literal IPs
    * Prevent legacy server status ping for servers running 1.6.4 or lower

# 1.2.0
* Rework cleanup behavior of expiring cached values (e.g. game profile information)
    * now always executed when first accessed
    * now persisted in the correct order (newest = at the end)
    * should be much faster
    * _NOTE:<br/>This MIGHT remove some cached values from previous versions. The cache will automatically be rebuilt over time._
* Improved command
    * Keys are now truncated at the start (e.g. `...f55112bb38e1d9d0`)
        * Previously they were truncated at the end - which always showed the same `302a300506032b65...`
    * Highlight player names in the chat with heads when possible
* Various cleanups and optimizations

# 1.1.1
* Fix crash when no mod state directory is present

# 1.1.0
* Moved `.authback` directory to `.mods/authback` for a cleaner game directory
* Prevent signature validation crash when servers send an empty signature
    * The error itself is unrelated to the mod but it's frequently encountered when using it and spams the log

# 1.0.0
* Updated to 1.21.11

_Initial stable release after finding no problems during testing_

# 0.4.0
* Make it possible to join to a server during an outage when the mod was previously not installed
    * A temporary GameProfile will be created that lacks any profile properties
        * Properties related content like skins will not work

# 0.3.2
* Improve logging

# 0.3.1
* Fixed crash because expected external libraries are not present

# 0.3.0
_Initial preview release_
