# ME Security

ME Security is a NeoForge addon for Applied Energistics 2 that restores a classic-inspired ME Security Terminal. The terminal is an AE2 cable part; attaching it makes an ME grid private to its owner and lets that owner grant per-player access.

The current stable release is **1.0.0**. This checkout builds the unreleased
**1.1.0-dev.2** candidate, whose protocol is incompatible with 1.0.0. See
[CHANGELOG.md](CHANGELOG.md) for release notes.

## Requirements

Install the same mod set on the client and dedicated server.

| Component | Supported version | Required |
|---|---:|---:|
| Minecraft | 1.21.1 | Yes |
| Java | 21 | Yes |
| NeoForge | 21.1.250 | Yes |
| Applied Energistics 2 | 19.2.17 | Yes |
| GuideME | 21.1.1 | Yes; supplied by AE2 |
| AE2 Wireless Terminals | 19.5.1 | No |

The dependency ranges are deliberately exact. ME Security uses version-targeted enforcement hooks and refuses unsupported combinations instead of silently disabling a check.

## Using the terminal

Attach the terminal to an ME cable or a compatible device face, just like an ordinary AE2 terminal. It shares the cable-bus block with cables and other parts and uses one channel. The player who places a fresh terminal becomes its owner. Terminals must be placed by a player, not a machine such as a deployer. Right-click the terminal to open its management screen, search players previously seen by the server, and grant or remove trust. Trust always includes permission to open and view the network; a newly trusted player starts without any additional permissions.

Only the owner can edit permissions or manage patterns. Transferring ownership opens a confirmation field that requires the selected player's name. Trusted entries remain after transfer, while the previous owner loses access unless the new owner trusts them later.

| Permission | Effect |
|---|---|
| Insert | Deposit items and fluids through terminals, container interactions, and supported wireless shortcuts. |
| Extract | Withdraw items and fluids, including network ingredients used by manual terminal crafting. |
| Craft | Calculate, submit, pause, and cancel autocrafting jobs, and collect your own completed final products up to the requested quantity. |
| Build | Place, remove, recolor, and configure native AE2 network hardware, including physical storage cells. |

Changes affect subsequent server actions immediately. Reducing a player's permissions, including removing trust, closes their affected open ME screen. Permission buttons send individual toggle intents so multiple clicks during a slow connection are applied in order to the live server policy.

Craft-only collection becomes available after the entire job finishes successfully. Its allowance is tied to the requesting player, the protected network's Security Terminal, and the exact final item or fluid; it is spent as products are withdrawn and survives server restarts. Canceled jobs, intermediate ingredients, byproducts, and other players' jobs grant no collection allowance. The player must still have Craft permission when collecting. Jobs started before installing this feature have no recorded allowance. Manual terminal crafting still requires Extract for network ingredients.

Build follows actual connections: differently colored, disconnected cables do not require permission from a neighboring network, and isolated side parts in a block without a central cable are separate grids. Adding a center cable checks the internal parts it joins and exposed, compatible neighbors. Removing a part checks its own internal and outward nodes; mining a whole cable-bus block checks every part. Recoloring checks the current network and neighbors the new color would connect to, and rejects a merge containing multiple Security Terminals.

Only one Security Terminal may be attached to a connected grid. Normal player placement of a second terminal is rejected without consuming the item. If two protected networks are merged through unloaded topology or another nonstandard path, the existing conflict state still denies player network access until one terminal is removed. Each terminal owner can still open their own management screen to diagnose that fallback conflict.

Wireless terminals still require normal AE2 access-point pairing and range or quantum-link conditions. A paired item does not grant ME Security permissions.

Only its owner can dismantle a Security Terminal or break the cable-bus block containing it, regardless of Build permission. Removing it retires its policy and drops a fresh, unbound part item. Policy identities and conservative topology associations survive chunk unloads and server restarts without chunk loading. Power loss and channel exhaustion do not turn protection off.

### Hardware protection

The same terminal protects ordinary AE2 drives, controllers, crafting CPUs, quantum bridges, spatial devices, cables, and cable parts. Build permission grants full hardware trust: it allows mining, wrench removal, configuration, and manual storage-cell management, including access to the contents of removable cells. Security and pattern-management screens remain owner-only.

Build also covers P2P attunement, facade installation, monitor settings, and physical view cells in shared terminals. Conversion-monitor deposits and withdrawals use Insert and Extract permissions independently of Build.

Protected hardware survives explosions. Storage cells in drives, ME Chests, ME IO Ports, and Spatial IO Ports must be inserted and removed manually by a player with Build permission. Cell-slot hoppers and buses are disabled on protected networks; ordinary item/fluid automation and internal IO Port processing continue. Cached handlers cannot retain access after protection is installed. Unprotected networks retain AE2's normal behavior.

The unreleased Secure Drive and PIN system have been removed outright. This candidate does not migrate Secure Drive blocks or items from development worlds.

## Scope and limitations

ME Security controls player access through supported AE2 and AE2 Wireless Terminals menus, network storage transactions, crafting requests, crafting controls, and wireless restock inventory synchronization. Machine automation has no player principal and continues normally. Jobs already accepted by a crafting CPU continue after the initiating player's access changes.

Hardware protection applies to native AE2 network devices and follows actual network connections, including conservative protection during unresolved topology. It does not protect arbitrary third-party movers or tools, administrative commands, external inventories, or machine outputs. The mod does not provide land claims, access history, or lockdown controls.

Compatibility is limited to the exact versions in the table. Adding another terminal mod or changing an existing menu or packet path requires an explicit integration review; unknown integrations are not claimed to be protected.
