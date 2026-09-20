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

### Custom terminal face textures

The terminal face uses the same three-layer tint model as AE2's terminals. Supply three 16x16 transparent PNGs:

- `src/main/resources/assets/ae2security/textures/part/security_terminal_bright.png`
- `src/main/resources/assets/ae2security/textures/part/security_terminal_medium.png`
- `src/main/resources/assets/ae2security/textures/part/security_terminal_dark.png`

Put each face pixel in exactly one of those images and leave the same pixel transparent in the other two. Use white pixels when you want the cable color to appear without another hue mixed into it; grayscale pixels can reduce the brightness further. AE2 applies the attached cable's bright, medium, and dark color variants to those layers at render time, so separate PNGs for all cable colors are unnecessary. The outer housing, connection status lights, powered emissive rendering, and cable-color lookup come from AE2's display models and cable bus renderer.

Inventory icons use these same masks with AE2's default Fluix tint colors. No separate composite face texture is required; particles use AE2's monitor housing texture.

### In-game guide

Click the help button at the top right of the Security Terminal screen, or hover its item and hold the configured GuideME guide key (W by default). The page also appears under **Items, Blocks, and Machines** in AE2's guide. It includes the recipe, trust controls, every permission, ownership transfer, Build behavior, disconnections, and protection limits.

The source is `src/main/resources/assets/ae2security/ae2guide/security_terminal.md`. GuideME loads it into AE2's existing guide and associates it with `ae2security:security_terminal`; no extra guide item is needed. `./gradlew runGuideClient` starts an isolated client: create or enter a world and it verifies the page, item link, and navigation entry before opening the page. You can reopen an existing preview world directly with `./gradlew runGuideClient -PguideWorld="World Folder Name"`. GuideME 21.1.1 requires a loaded world for recipes and linked 3D scenes, so the preview waits until the world is ready.

## Building and testing

Use a Java 21 JDK:

```shell
./gradlew build
./gradlew runGameTestServer
./gradlew test -PwithWireless=true
```

The normal build resolves dependencies from Maven Central, ModMaven, Modrinth, and Mojang. If a local JDK cannot negotiate TLS with Mojang's library host, the optional `minecraftLibraryCache` property can point at an existing launcher library directory:

```shell
./gradlew build -PminecraftLibraryCache="/path/to/launcher/meta/libraries"
```

The distributable JAR is written to `build/libs/ae2security-<version>.jar`. The GameTest server places real parts and exercises server menu edits, simulated/executed item and fluid transfers, automation, conflicts, disconnected colors and side parts, Build-controlled placement/removal/recoloring, Creative mining, outward toggle-bus nodes, rapid permission changes, stale screens, cable splits/rejoins, and crafting-context scope. JUnit covers all permission masks, ownership transfer confirmation, revocation, conflicts, immutable snapshots, and policy/world-data NBT. See `docs/release-readiness-1.0.0.md` for the stable-release evidence and remaining manual checks.

### Testing with two local players

Development clients use offline test identities, so opening a client world to LAN can reject the second client with an invalid-session error. Use the included offline local development server instead. Open a terminal and keep this task running:

```shell
./gradlew runMultiplayerServer
```

Open two more terminals in the project directory:

```shell
# Terminal 2
./gradlew runOwnerClient

# Terminal 3
./gradlew runGuestClient
```

In both clients, choose **Multiplayer**, **Direct Connection**, and connect to `localhost:25565`. Do not use **Open to LAN**. The server run automatically uses `online-mode=false`, disables secure-profile enforcement, and starts players in Creative mode.

To connect either development client automatically, add `-PserverAddress=localhost:25565` to its command, for example `./gradlew runOwnerClient -PserverAddress=localhost:25565`.

The two development profiles are named `Owner` and `Guest`, so their UUIDs and permissions remain distinct. Have Owner place the Security Terminal, use Guest first as an outsider, then trust Guest and grant Insert, Extract, Craft, and Build one at a time. The server and both clients keep separate settings, logs, and saves under `run/multiplayer-server`, `run/owner-client`, and `run/guest-client`.

After rebuilding or changing network payloads, stop the development server and close both clients before launching all three again. Development Minecraft processes do not hot-reload mod classes. A payload-version mismatch means at least one process is still running an older build; a `world/session.lock` error means an earlier server process is still using that world.

The permission-button fixes use network protocol **3**. Restart the server and both clients together after updating from an earlier build.

## Scope and limitations

ME Security controls player access through supported AE2 and AE2 Wireless Terminals menus, network storage transactions, crafting requests, crafting controls, and wireless restock inventory synchronization. Machine automation has no player principal and continues normally. Jobs already accepted by a crafting CPU continue after the initiating player's access changes.

Build protects AE2 cable-bus parts and player mining of their cable-bus blocks. This release does not protect standalone network blocks such as controllers and drives, prevent storage-cell removal, or secure external inventories and machine outputs through other mods. It does not provide land claims, biometric cards, access history, or lockdown controls.

Compatibility is limited to the exact versions in the table. Adding another terminal mod or changing an existing menu or packet path requires an explicit integration review; unknown integrations are not claimed to be protected.
