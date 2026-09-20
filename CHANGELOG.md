# Changelog

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
