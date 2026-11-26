# Changelog

## 1.2.1 - 2026-11-26

### Changed

- Updated to Applied Energistics 2 19.2.20, the newest AE2 for Minecraft 1.21.1. AE2 19.2.20 is now required; 19.2.17 is no longer supported.
- AE2 Wireless Terminals 19.5.1 remains the supported optional version; it officially supports AE2 19.2.17 through 19.x.

### Compatibility

- Minecraft 1.21.1, NeoForge 21.1.250, Applied Energistics 2 19.2.20, GuideME 21.1.1, optional AE2 Wireless Terminals 19.5.1.
- No save or protocol changes from 1.2.0. Payload protocol stays at 6, and 1.2.0 and 1.2.1 clients and servers can connect to each other as long as they use the same AE2 version.

## 1.2.0 - 2026-11-26

The largest update since 1.0.0. The Security Terminal now protects the network's hardware as well as its storage, adds a Collect permission, operator recovery commands and a rebuilt screen. Client and server must both update.

### Added

- **Hardware protection.** Build now covers placing, mining, wrenching, rotating, configuring and upgrading AE2 network hardware, including drives, controllers, crafting CPUs, quantum bridges and spatial devices, plus full-block hardware from AE2 add-ons that hosts grid nodes. Protected hardware survives explosions and mob griefing.
- **Storage-cell protection.** Cells in protected drives, ME Chests, IO Ports and Spatial IO Ports are managed by hand by players with Build; hoppers and buses can no longer pull them out. Ordinary item and fluid automation keeps working.
- **Collect permission.** Players can withdraw their own completed autocrafting outputs, up to the requested amount, without Extract. Craft now only requests, pauses and cancels jobs.
- **Operator commands.** `/mesecurity info`, `setowner`, `retire` and `bypass` (permission level 2) recover networks whose owner is gone. Bypass ends as soon as the operator is de-opped.
- **Menu tags.** `ae2security:owner_menus`, `hardware_menus` and `storage_menus` let pack makers classify add-on menus. Unknown add-on menus require Build.

### Changed

- The Security Terminal screen is rebuilt from AE2's own terminal parts: AE2's frame and toolbar, a scrollable player list with lock icons and per-player permission lights, AE2 toggle switches, a warning badge for conflicting or disconnected networks, and ownership transfer as a toolbar tab with a compact confirmation popup. It follows resource packs that restyle AE2.
- Securing an existing ME network, or bridging a protected network into one, requires having helped build it.
- Security Terminals can no longer be placed by fake players such as Create deployers.
- Reducing a permission only closes the ME screens that depend on it.
- Denial notices are rate-limited per message, so a different reason is no longer hidden.

### Fixed

- Copied Security Terminals (structure templates, `/clone`, pick-block, world editors) get their own identity instead of sharing the original's policy, where removing either copy unprotected the other. Moved terminals keep theirs.
- Terminals placed without an owning player no longer lock their network.
- Terminals forget chunks their network has provably left, so split-off pieces are released and saved footprints stop growing.
- P2P attunement, monitor configuration, facades and view cells in shared terminals require Build. Part-click packets check reach, and Wireless Terminals shortcuts recheck range.
- The transfer prompt and labels no longer get cut off for long player names.
- Lower per-tick overhead for protected networks and cell automation.

### Compatibility and upgrading

- Minecraft 1.21.1, NeoForge 21.1.250, Applied Energistics 2 19.2.17, GuideME 21.1.1, optional AE2 Wireless Terminals 19.5.1.
- Payload protocol 6: client and server must both use 1.2.0.
- Saved data moves to format 4 automatically. Worlds from 1.0.0 and 1.1.0 keep their exact permissions; nobody gains Collect.
- The license is now LGPL-3.0-or-later.

## 1.1.0 - 2026-10-02

- Refined the security terminal screen layout.
- Added server-sided acknowledgements for policy edits and disabled pending trust and ownership-transfer actions until they complete.
- Added the search field's help to standard tooltips and narration.

## 1.0.0 - 2026-09-20

First stable release for Minecraft 1.21.1.

### Added

- AE2 cable-subpart Security Terminal with cable-color rendering and a survival recipe.
- UUID-based ownership and trusted-player access with Insert, Extract, Craft, and Build permissions. Trusted players always receive viewing access.
- Owner-only AE2-style management screen with player search, permission toggles, trust removal, and typed ownership-transfer confirmation.
- Server-side enforcement for supported wired, wireless, quantum-linked, storage, fluid, crafting, crafting-status, and pattern-terminal paths.
- Build enforcement for AE2 cable-bus part placement, wrench removal, block mining, and cable recoloring. Security Terminal removal is always owner-only.
- Persistent fail-closed policy associations, split reconciliation, and explicit multiple-terminal conflict handling.
- Optional AE2 Wireless Terminals 19.5.1 integration, including passive restock-inventory synchronization protection.
- Creative tab, English localization, tooltips, original terminal textures, and an in-game GuideME page.

### Fixed before stable

- Placement checks now follow actual cable colors, occupied faces, and outward grid connections instead of unrelated nearby grids.
- Recoloring cannot bypass Build permission or merge protected grids.
- Denied placement resynchronizes the affected cable-bus positions to prevent client ghost parts.
- Rapid permission clicks are applied in order instead of being lost to stale snapshots.
- Reducing a permission closes affected open AE2 menus immediately.
- The terminal item uses AE2's normal color tinting instead of rendering as a blank white icon.

### Compatibility

- Minecraft 1.21.1
- Java 21
- NeoForge 21.1.250
- Applied Energistics 2 19.2.17
- GuideME 21.1.1 (supplied by AE2)
- Optional AE2 Wireless Terminals 19.5.1

These versions are intentionally exact because the addon uses version-specific enforcement hooks.

### Known boundaries

This release controls supported player access and AE2 cable-bus modifications. It does not protect standalone controllers or drives, removable storage cells, external inventories and machine outputs, explosions, or arbitrary third-party tools. It is not a land-claim mod.
