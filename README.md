# ME Security

ME Security is a NeoForge addon for Applied Energistics 2 that restores a classic-inspired ME Security Terminal. The terminal is an AE2 cable part; attaching it makes an ME grid private to its owner and lets that owner grant per-player access.

The current release is **1.2.3**. Its payload protocol (version 6) is incompatible with 1.1.0 and
earlier, so clients and servers must update together. See [CHANGELOG.md](CHANGELOG.md) for release notes.
ME Security is licensed under the GNU Lesser General Public License v3.0 or later.

## Requirements

Install the same mod set on the client and dedicated server.

| Component | Supported version | Required |
|---|---:|---:|
| Minecraft | 1.21.1 | Yes |
| Java | 21 | Yes |
| NeoForge | 21.1.250 or newer 21.1.x | Yes |
| Applied Energistics 2 | 19.2.20 | Yes |
| GuideME | 21.1.1 or newer | Yes; separate mod that AE2 requires |
| AE2 Wireless Terminals | 19.5.1 | No |
| Jade or WTHIT | Any version AE2 supports | No |

The AE2 and AE2 Wireless Terminals versions are deliberately exact. ME Security uses version-targeted enforcement hooks and refuses unsupported combinations instead of silently disabling a check. NeoForge and GuideME accept newer releases.

## Using the terminal

Attach the terminal to an ME cable or a compatible device face, just like an ordinary AE2 terminal. It shares the cable-bus block with cables and other parts and uses one channel. The player who places a fresh terminal becomes its owner. Terminals must be placed by a player, not a machine such as a deployer. Right-click the terminal to open its management screen, search players previously seen by the server, and grant or remove trust. Trust always includes permission to open and view the network; a newly trusted player starts without any additional permissions.

Only a player who helped build a network can secure it: placing a terminal on an existing network requires owning at least one of its nodes, while networks where no node has a recorded owner (machine-placed hardware, very old worlds) stay open to anyone. The same rule stops you from joining your protected network to someone else's unprotected network with a cable. Extending your own network is unaffected.

Only the owner can edit permissions or manage patterns. Transferring ownership, from the arrow tab in the screen's left toolbar, opens a confirmation window that requires the selected player's name. Trusted entries remain after transfer, while the previous owner loses access unless the new owner trusts them later.

| Permission | Effect |
|---|---|
| Insert | Deposit items and fluids through terminals, container interactions, and supported wireless shortcuts. |
| Extract | Withdraw items and fluids, including network ingredients used by manual terminal crafting. |
| Craft | Calculate, submit, pause, and cancel autocrafting jobs. Does not allow withdrawing the results. |
| Collect | Withdraw your own completed final products, up to the requested quantity, without Extract. With reversible patterns (blocks and ingots, for example) this can turn network stock into withdrawable items, so grant it deliberately. |
| Build | Place, remove, recolor, and configure AE2 network hardware, including add-on devices and physical storage cells. |

Changes affect subsequent server actions immediately. Removing trust, or reducing a permission the open screen depends on (for example Extract for a storage terminal or Build for hardware), closes that player's open ME screen; losing an unrelated permission such as Craft leaves a storage terminal open. Permission buttons send individual toggle intents so multiple clicks during a slow connection are applied in order to the live server policy.

Collect-only withdrawal becomes available after the entire job finishes successfully, and only for jobs the player started with Craft. Its allowance is tied to the requesting player, the protected network's Security Terminal, and the exact final item or fluid; it is spent as products are withdrawn and survives server restarts. Canceled jobs, intermediate ingredients, byproducts, and other players' jobs grant no collection allowance. The player must have Collect permission when withdrawing; Craft alone lets a player request jobs but not take the results. Jobs started before installing this feature have no recorded allowance. Manual terminal crafting still requires Extract for network ingredients. Worlds upgraded from 1.0.0 or 1.1.0 keep exactly their existing permissions: those releases had no crafted-output collection, so nobody gains Collect. Unreleased development builds in which Craft could collect (saved-data formats 2 and 3) grant Collect to every trusted player who has Craft, so their behavior is unchanged; format 4 data is never migrated again.

Build follows actual connections: differently colored, disconnected cables do not require permission from a neighboring network, and isolated side parts in a block without a central cable are separate grids. Adding a center cable checks the internal parts it joins and exposed, compatible neighbors. Removing a part checks its own internal and outward nodes; mining a whole cable-bus block checks every part. Recoloring checks the current network and neighbors the new color would connect to, and rejects a merge containing multiple Security Terminals.

Only one Security Terminal may be attached to a connected grid. Normal player placement of a second terminal is rejected without consuming the item. If two protected networks are merged through unloaded topology or another nonstandard path, the existing conflict state still denies player network access until one terminal is removed. Each terminal owner can still open their own management screen to diagnose that fallback conflict.

With Jade or WTHIT installed, looking at a Security Terminal shows its owner's name to every player, and below it your own access: Owner, the permissions you have been granted, or None. A network with conflicting terminals or an unresolved policy says that access is blocked. The tooltip uses AE2's built-in tooltip support, so neither mod is a dependency.

Wireless terminals still require normal AE2 access-point pairing and range or quantum-link conditions. A paired item does not grant ME Security permissions.

Only its owner can dismantle a Security Terminal or break the cable-bus block containing it, regardless of Build permission (operators can use the recovery commands under Administration). Removing it retires its policy and drops a fresh, unbound part item. A terminal copied with its block data (structure templates, `/clone`, creative pick-block, or world editors) receives its own identity with the original's owner and trusted players once the original is found still in place, and removing such a copy never retires the original's policy; Spatial IO and other movers that vacate the old location keep the identity. A copy that is never loaded together with its original cannot be told apart from a move and keeps sharing the policy until both are loaded. Policy identities and conservative topology associations survive chunk unloads and server restarts without chunk loading. Power loss and channel exhaustion do not turn protection off.

### Hardware protection

The same terminal protects ordinary AE2 drives, controllers, crafting CPUs, quantum bridges, spatial devices, cables, and cable parts. Hardware from AE2 add-ons is protected the same way when its block hosts an AE2 grid node, whichever mod registered it; add-on cable parts already live in AE2's cable bus. A device whose grid node is not available yet counts as unresolved and stays protected. Build permission grants full hardware trust: it allows mining, wrench removal, configuration, and manual storage-cell management, including access to the contents of removable cells. Security and pattern-management screens remain owner-only.

Menu access is classified with three menu-type tags: `ae2security:owner_menus` (owner only), `ae2security:hardware_menus` (Build), and `ae2security:storage_menus` (View; storage transactions are still checked separately). Owner beats hardware, which beats storage. AE2's crafting amount and confirmation screens need Craft, and its crafting CPU and status screens need View. Any other AE2-based menu, such as an add-on machine screen, needs Build. Pack makers can extend or override the classification with a datapack at `data/ae2security/tags/menu/owner_menus.json`, `hardware_menus.json`, or `storage_menus.json`.

Build also covers P2P attunement, facade installation, monitor settings, and physical view cells in shared terminals. Conversion-monitor deposits and withdrawals use Insert and Extract permissions independently of Build.

Protected hardware survives explosions. Storage cells in drives, ME Chests, ME IO Ports, and Spatial IO Ports must be inserted and removed manually by a player with Build permission. Cell-slot hoppers and buses are disabled on protected networks; ordinary item/fluid automation and internal IO Port processing continue. Cached handlers cannot retain access after protection is installed. Unprotected networks retain AE2's normal behavior.

The unreleased Secure Drive and PIN system have been removed outright. This candidate does not migrate Secure Drive blocks or items from development worlds.

## Administration

Operators (permission level 2 or higher) can recover networks whose owner is gone with `/mesecurity`. Terminal UUIDs are shown by `info` and can be copied by clicking them in chat.

| Command | Effect |
|---|---|
| `/mesecurity info` | Shows the terminal of the network you are looking at: its UUID, owner, trusted players with their permissions, and a conflict or unresolved warning. |
| `/mesecurity info <terminal UUID>` | The same for a terminal by UUID. Works from the server console. |
| `/mesecurity setowner <terminal UUID> <player>` | Makes an online or previously seen player (matched by name, ignoring case) the owner. Trusted entries remain and the previous owner loses access, like an in-game transfer. |
| `/mesecurity retire <terminal UUID>` | Ends the terminal's policy. The network becomes unprotected and the leftover part is ordinary hardware. Use it on a terminal reported as unresolved. |
| `/mesecurity bypass` | Toggles an admin bypass for yourself. While on, every ME Security check passes for you and you can remove any Security Terminal. It does not open the owner screen (use `setowner` for that), is kept in memory only, and ends on logout or server stop. Each toggle is logged. |

A terminal placed with no owning player, such as by an automated placer that has no player profile, is retired immediately and protects nothing instead of locking its network.

## Scope and limitations

ME Security controls player access through supported AE2 and AE2 Wireless Terminals menus, network storage transactions, crafting requests, crafting controls, and wireless restock inventory synchronization. Machine automation has no player principal and continues normally. Jobs already accepted by a crafting CPU continue after the initiating player's access changes.

Hardware protection applies to native AE2 network devices and to add-on blocks that host AE2 grid nodes, and follows actual network connections, including conservative protection during unresolved topology. Add-on menus that the tags above do not classify require Build; add-on pattern-management screens become owner-only only when a pack adds them to `owner_menus`. Hopper and bus protection for storage-cell slots covers add-on devices only when they extend AE2's drive, ME Chest, IO Port, Spatial IO Port, or quantum bridge. It does not protect arbitrary third-party movers or tools, vanilla and third-party world-editing commands such as `/setblock` or WorldEdit, external inventories, or machine outputs. The only administrative commands the mod provides are the `/mesecurity` recovery commands below; it does not provide land claims, access history, or lockdown controls.

Compatibility is limited to the versions in the table; AE2 and AE2 Wireless Terminals must match exactly. Adding another terminal mod or changing an existing menu or packet path requires an explicit integration review; unknown integrations are not claimed to be protected.
