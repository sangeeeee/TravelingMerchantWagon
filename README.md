# TravelingMerchantWagon

A Minecraft 1.21.1 NeoForge mod in development, focused on adding traveling merchant wagons.

- Mod ID: `tm_wagon`
- Java package: `com.sange.tm_wagon`
- Java version: 21
- Required dependency: [GeckoLib](https://modrinth.com/mod/geckolib) 4.9.3 or newer in the 4.x series, using the NeoForge build for Minecraft 1.21.1. Install it on both the client and server.

Development builds resolve GeckoLib automatically through Gradle. Its build version and supported runtime range are configured in `gradle.properties`.


Installation information
=======

This template repository can be directly cloned to get you started with a new
mod. Simply create a new repository cloned from this one, by following the
instructions provided by [GitHub](https://docs.github.com/en/repositories/creating-and-managing-repositories/creating-a-repository-from-a-template).

Once you have your clone, simply open the repository in the IDE of your choice. The usual recommendation for an IDE is either IntelliJ IDEA or Eclipse.

If at any point you are missing libraries in your IDE, or you've run into problems you can
run `gradlew --refresh-dependencies` to refresh the local cache. `gradlew clean` to reset everything 
{this does not affect your code} and then start the process again.

Mapping Names:
============
By default, the MDK is configured to use the official mapping names from Mojang for methods and fields 
in the Minecraft codebase. These names are covered by a specific license. All modders should be aware of this
license. For the latest license text, refer to the mapping file itself, or the reference copy here:
https://github.com/NeoForged/NeoForm/blob/main/Mojang.md

Additional Resources: 
==========
Community Documentation: https://docs.neoforged.net/  
NeoForged Discord: https://discord.neoforged.net/
