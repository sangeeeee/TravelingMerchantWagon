# TravelingMerchantWagon

**English** | [简体中文](README.zh-CN.md)

TravelingMerchantWagon adds customizable wooden wagons to Minecraft. Build a wagon from individual parts, hitch your horses, and take your supplies and companions on the road. Whether you are moving house or setting out as a travelling merchant, your wagon can be a storehouse, workshop and place to rest.


Available for NeoForge 26.3, NeoForge/Fabric 1.21.1 and Forge 1.20.1. Fabric 26.3 gameplay is not yet implemented. See [26.3 target notes](versions/mc-26.3/README.md) for its available integrations.

## What can you do?

- **Build your own wagon.** Choose a standard, extended or wide cargo box, single- or double-horse shafts, and a one-, two- or three-person driver's bench. Mix wooden parts and choose colours for cushions and fabric.
- **Drive or push it.** Hitch horses, donkeys, skeleton horses or zombie horses with leads. Drive from the driver's position, build up speed, brake and reverse. A pair of hitched animals accelerates faster and softens cargo's effect on sprint speed. Push an unhitched wagon to reposition it or help it out of a difficult spot.
- **Carry useful cargo.** Place permitted blocks in cargo slots. Open supported containers and use workstations directly on board. Cargo and stored items stay with the wagon when it changes form.
- **Make room for companions.** Add cargo stools for passengers, or straw mats for sleeping through the night. Sleeping players and maids stay with a moving wagon.
- **Fit optional equipment.** Add an under-seat cabinet, a rollable cargo cover, or a canvas canopy with opening curtains. Covers and canopies adapt to your cargo box.
- **Park, rebuild and reuse.** Convert the wagon between parked block form and a drivable entity at an assembly jack. Use a carpenter's hammer to recover its parts and cargo.

## Getting started

1. Place an assembly jack and fit a cargo box to its raised platform.
2. Add a driver's bench, shafts, two small front wheels and two large rear wheels.
3. Use the jack to assemble the wagon.
4. Lead your draft animals over and use the shafts to hitch them.
5. Take the driver's position and use your movement keys. Tap the sprint key while moving forward to engage the higher speed until you release forward.

For illustrated assembly instructions, recipes and controls, install **Patchouli** and open the **Coachman's Handbook**. Players receive it automatically when joining a world, unless the server disables the gift. You can also craft one with a book, a lead and any planks.

## Mod integrations

These integrations are optional; install the ones you want to use. Availability varies by game version: 26.3 currently supports Carry On and both backpack mods, while Patchouli, Touhou Little Maid/TACZ and Sable integrations belong to the older targets.

| Mod | What it adds to your wagon experience |
| --- | --- |
| [Patchouli](https://modrinth.com/mod/patchouli) | The Coachman's Handbook, with assembly diagrams, recipes and gameplay instructions. |
| [Traveler's Backpack](https://modrinth.com/mod/travelersbackpack) | Load backpacks, open their own storage screens on board, and retain their contents. Separate sleeping bags fit across two cargo slots for player sleep without changing the respawn point. |
| [Sophisticated Backpacks](https://www.curseforge.com/minecraft/mc-mods/sophisticated-backpacks) | Transport backpacks and use their storage and settings screens while they are on the wagon. |
| [Carry On](https://modrinth.com/mod/carry-on) | Move permitted cargo onto and off the wagon, or seat a carried entity on a cargo stool. Pickup follows your Carry On key and configuration. |
| [Touhou Little Maid](https://www.curseforge.com/minecraft/mc-mods/touhou-little-maid) | Wagon Companion and Wagon Passenger tasks, mounted bow/crossbow/TACZ combat for the companion, straw-mat sleep during rest time, and maid release/capture interactions with the original tools. |
| [Sable](https://modrinth.com/mod/sable) | Wagons and their passengers account for physical structures when moving or boarding. Assemble block-form components in the normal world before travelling around structures. |

Supported vanilla-style modded chests and barrels also work as interactive cargo, including **[BetterEnd](https://modrinth.com/mod/betterend) barrels that use BCLib** on supported older targets. Compatibility depends on the container's storage behaviour.

For either backpack mod, sneak-right-click an empty slot to load a held backpack, right-click the loaded backpack to open it, and sneak-right-click it to unload. Carry On takes priority when its pickup conditions are met.

**[GeckoLib](https://modrinth.com/mod/geckolib) is required** for wagon animations. The Fabric version also requires **Fabric API** and **[Forge Config API Port](https://modrinth.com/mod/forge-config-api-port)**. Fabric integrations use the corresponding Fabric releases, including the Orihime maid port and the Sophisticated Backpacks/Core and TaCZ ports. Server owners can customize cargo rules, driving behaviour and handbook gifts in `tm_wagon-server.toml`.

By default, two hitched draft animals provide **20% more forward and reverse acceleration**, and reduce the cargo deduction from sprint speed by **15%**. Server owners can adjust both benefits in the `draftTeam` section of the same configuration file.

## License

For source builds and the multi-version project layout, see [BUILDING.md](BUILDING.md).

TravelingMerchantWagon is licensed under the **GNU General Public License, version 2 only (GPL-2.0-only)**. See [LICENSE](LICENSE) for the full terms and [NOTICE](NOTICE) for attribution. The original NeoForged MDK template retains its [MIT license notice](TEMPLATE_LICENSE.txt).
