# ME Security

ME Security is a NeoForge addon for Applied Energistics 2 that restores a classic-inspired ME Security Terminal. The terminal is an AE2 cable part; attaching it makes an ME grid private to its owner and lets that owner grant per-player access.

The current stable release is **1.0.0**. See [CHANGELOG.md](CHANGELOG.md) for release notes.

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

Attach the terminal to an ME cable or a compatible device face, just like an ordinary AE2 terminal. It shares the cable-bus block with cables and other parts and uses one channel. The player who places a fresh terminal becomes its owner. Right-click the terminal to open its management screen, search players previously seen by the server, and grant or remove trust. Trust always includes permission to open and view the network; a newly trusted player starts without any additional permissions.

Only the owner can edit permissions or manage patterns. Transferring ownership opens a confirmation field that requires the selected player's name. Trusted entries remain after transfer, while the previous owner loses access unless the new owner trusts them later.

| Permission | Effect |
|---|---|
| Insert | Deposit items and fluids through terminals, container interactions, and supported wireless shortcuts. |
| Extract | Withdraw items and fluids, including network ingredients used by manual terminal crafting. |
| Craft | Calculate, submit, pause, and cancel autocrafting jobs. Extract is still required to retrieve products. |
| Build | Attach, remove, and recolor AE2 cables and cable parts on the protected network. |

Changes affect subsequent server actions immediately. Reducing a player's permissions, including removing trust, closes their affected open ME screen. Permission buttons send individual toggle intents so multiple clicks during a slow connection are applied in order to the live server policy.

Build follows actual connections: differently colored, disconnected cables do not require permission from a neighboring network, and isolated side parts in a block without a central cable are separate grids. Adding a center cable checks the internal parts it joins and exposed, compatible neighbors. Removing a part checks its own internal and outward nodes; mining a whole cable-bus block checks every part. Recoloring checks the current network and neighbors the new color would connect to, and rejects a merge containing multiple Security Terminals.

Only one Security Terminal may be attached to a connected grid. Normal player placement of a second terminal is rejected without consuming the item. If two protected networks are merged through unloaded topology or another nonstandard path, the existing conflict state still denies player network access until one terminal is removed. Each terminal owner can still open their own management screen to diagnose that fallback conflict.

Wireless terminals still require normal AE2 access-point pairing and range or quantum-link conditions. A paired item does not grant ME Security permissions.

Only its owner can dismantle a Security Terminal or break the cable-bus block containing it, regardless of Build permission. Removing it retires its policy and drops a fresh, unbound part item. Policy identities and conservative topology associations survive chunk unloads and server restarts without chunk loading. Power loss and channel exhaustion do not turn protection off.

## Scope and limitations

ME Security controls player access through supported AE2 and AE2 Wireless Terminals menus, network storage transactions, crafting requests, crafting controls, and wireless restock inventory synchronization. Machine automation has no player principal and continues normally. Jobs already accepted by a crafting CPU continue after the initiating player's access changes.

Build protects AE2 cable-bus parts and player mining of their cable-bus blocks. This release does not protect standalone network blocks such as controllers and drives, prevent storage-cell removal, or secure external inventories and machine outputs through other mods. It does not provide land claims, biometric cards, access history, or lockdown controls.

Compatibility is limited to the exact versions in the table. Adding another terminal mod or changing an existing menu or packet path requires an explicit integration review; unknown integrations are not claimed to be protected.
