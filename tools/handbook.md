# Coachman's Handbook

The book definition is `src/main/resources/data/tm_wagon/patchouli_books/coachmans_manual/book.json`.
Chapter content lives in `src/main/resources/assets/tm_wagon/patchouli_books/coachmans_manual/`.
Edit the native JSON files directly. English, Simplified Chinese and Traditional Chinese have full chapters.
Other languages use Patchouli's English content fallback, with translated category names, landing links and recipe headings.

The six categories are introduction/assembly, driving, cargo/passengers, optional equipment, workshop, and help/integrations.
All entries are available without advancements. Carry On, Touhou Little Maid, Sable, Traveler's Backpack and Sophisticated Backpacks entries are gated
by their installed-mod flags. Modded storage, including BetterEnd/BCLib barrels, is explained in Containers;
Patchouli's handbook delivery is explained in Troubleshooting.
Keep unconditional links pointed at unconditional entries.

Driving / Hitching explains the default two-animal team benefits: 20% more forward and reverse
propulsion acceleration and a 15% reduction of the cargo deduction from sprint speed. Driving / Controls
points to the server settings. Keep these defaults in sync with `DrivingConfig` and the `draftTeam`
section of `tm_wagon-server.toml`; the benefits require both living animals to be attached.

The handbook item and its recipe exist only when Patchouli is installed. Login gifts are controlled by the server option.
Each player's persisted data records actual receipt, rather than the first login: existing-world players also receive a book,
and disabled gifts or missing Patchouli do not consume eligibility. Receipt survives reconnects, respawns and saves.

Assembly instruction/image pairs form two-page spreads. See `render_assembly_guide.md` to regenerate images from the actual models.
Equipment entries end with a two-image preview page opposite the recipe. Use the small image arrows to compare states.
Run `python tools/render_equipment_guide.py` to render cabinet drawers, rolled covers and canopy curtains from the
current runtime models, transforms and textures. Review images are saved to `docs/handbook/equipment`; the
text-free 256px book textures live under `textures/gui/handbook/equipment`.

`tm_wagon:component_recipe` pages refer to live recipe IDs. `WagonRecipeComponent` renders a single oak/white example
using the same material projection as JEI. This avoids generating every material combination when opening the book.
Caption text describes material restrictions. A missing server recipe displays an explanatory message.
The second triple wooden bench recipe uses `alternative: 1`. `extra_recipe_mappings` enables item-to-entry lookup.
Recipe examples never enter the authoritative recipe manager.

The maid compatibility entry explains mounted bow, crossbow and TACZ combat for Wagon Companion.
Weapon selection delegates to Little Maid’s native riding tasks; document its ammunition, targeting and enchantment rules.
Wagon Passenger remains a transport task.

Resource checks:

```powershell
python tools/validate_handbook.py
python tools/validate_localizations.py
```

Client smoke test (requires a disposable `build/handbook-client/saves/repro` world with a handbook in its hotbar):

```powershell
.\gradlew.bat runHandbookSmokeTest '-PpatchouliTestJar=<absolute path to Patchouli 1.21.1-93-NEOFORGE.jar>'
```

The test verifies the native book, installed-mod gates, every visible spread, nonempty recipe outputs and text bounds.
It saves screenshots under `build/handbook-client/screenshots` and does not use the normal development saves.
Change the disposable client's language option to check each localized layout.

Use `runGameTestServer build` for the existing recipe projections, handbook delivery and crafting regression tests.
