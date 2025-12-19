# 1.2.0
* Rework cleanup behavior of expiring cached values
    * now executed initially
    * now alwawys persisted in the correct order (newest = at the end)
    * should be much faster
    * _NOTE:<br/>This MIGHT remove some cached values from previous versions. The cache will automatically be rebuilt over time._
* Further code cleanup

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
