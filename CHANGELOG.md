# Changelog

## 1.1.0-dev.2 — unreleased development candidate

### Changed

- Build now protects native AE2 network hardware: placement, mining, wrench removal, rotation, configuration, storage-cell management, and crafting-unit upgrades.
- Placement and connection changes check affected native multiblocks and reject merges containing multiple Security Terminals.
- Native hardware menus require Build; security and pattern management remain owner-only. Block/part menu actions validate live hosts and an eight-block range.
- Protected network hardware survives explosions, including the entire Security Terminal cable-bus block.
- External disk transfers are disabled in protected drives, ME Chests, ME IO Ports, and Spatial IO Ports, including handlers cached before terminal installation. Ordinary resource automation continues.

### Fixed

- P2P attunement checks Build on the existing part and any new outward ME connection before replacement.
- Monitor configuration, facade installation, and physical view-cell changes in shared terminals require Build. Permitted conversion-monitor deposits and withdrawals remain available without Build.
- Owners can wrench their own terminal through the normal interaction event to resolve a conflicting grid.
- Partial craft-output collection through wired and wireless storage wrappers spends the allowance exactly once.
- Part-click packets validate loaded chunks and interaction reach. Wireless Terminals shortcuts refresh their existing range and quantum-link checks before accessing a cached grid.

### Removed

- The unreleased ME Secure Drive, its PIN system, packets, and resources. Development-world Secure Drive blocks and items are not migrated.

### Compatibility

- Client and server must both use this candidate; its payload protocol is version 5.
- Existing terminal policies, trust entries, and completed-crafting allowances retain their save format.


## 1.1.0-dev.1 — unreleased development candidate

### Added

- Craft-only players can collect their own successfully completed autocrafting outputs, limited to the exact final item or fluid and requested amount, with persistent per-player allowances.
- Craftable ME Secure Drive with AE2 drive storage, a four-digit PIN keypad, and the standard ME Drive inventory layout after unlock.
- PIN-gated storage-cell and priority controls for the network owner and Build-trusted players, with bounded background PIN work and per-player cooldowns.
- Secure Drive owner-only mining and wrench removal, terminal identity binding, and final-owner recovery after terminal removal.
- Server-side player search and bounded directory pages, including players beyond the former snapshot limit.

### Fixed

- Drive menus now close when the player moves away or the block entity is replaced, and PIN results cannot revive a stale opening.
- PIN and policy actions report server results in their screens. Priority changes require a valid signed integer and a deliberate save.
- Secure Drive default naming, icon, mining tag, and background rendering now match their intended AE2 integration.
- The terminal keeps its selected player aligned with the visible search page and preserves transfer confirmation across unrelated directory updates.

### Compatibility

- Client and server must both use this candidate; its payload protocol is version 4.

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
