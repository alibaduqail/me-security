# ME Security 1.0.0 release readiness

Assessment date: 2026-09-20

## Release decision

Version 1.0.0 is ready for its first stable release on the exact supported dependency set listed below. The automated checks cover the authorization model, real AE2 grids and transactions, policy persistence serialization, required mixin application, optional Wireless Terminals loading, resources, and client GuideME compilation. The remaining items are manual compatibility exercises rather than known release-blocking defects.

## Supported matrix

| Component | Version |
| --- | --- |
| Minecraft | 1.21.1 |
| Java | 21 |
| NeoForge | 21.1.250 |
| Applied Energistics 2 | 19.2.17 |
| GuideME | 21.1.1 |
| AE2 Wireless Terminals | 19.5.1, optional |

The NeoForge metadata uses exact ranges and required mixins fail startup when their pinned targets no longer match.

## Validation

- Warning-enabled builds pass with and without AE2 Wireless Terminals.
- 50 JUnit tests pass with no failures, errors, or skipped tests.
- 12 dedicated-server GameTests pass with and without AE2 Wireless Terminals.
- The optional wireless test forces its compatibility target through Mixin and fails if the restock-inventory patch is absent.
- The isolated client preview loads a world and recipes, validates the GuideME navigation and item index, compiles the page, and opens it without page-compiler errors.
- A real offline dedicated server accepted the separate `Owner` and `Guest` graphical development clients simultaneously with ME Security 1.0.0, confirming login, mod-channel negotiation, and distinct multiplayer sessions.
- Recipe, advancement, language, model, Mixin, and pack JSON resources parse successfully.
- The packaged JAR contains the guide page, required recoloring Mixin, client tint registration, metadata, recipe, advancement, models, textures, and translations.
- Owner, guest, and local dedicated-server IntelliJ/Gradle launch arguments are generated successfully.

Final distribution artifact: `build/libs/ae2security-1.0.0.jar` (116 KiB)

SHA-256: `26b9c4189ccf4dddc242bcd6279b52dcbd6d5e85997bbafe1c1155bcd77cf5a4`

The detailed code findings fixed during the final audit are recorded in [review-2026-09-19.md](review-2026-09-19.md).

## Dependency advisory assessment

An OSV query found no advisories for the pinned NeoForge, AE2, or GuideME coordinates. The Minecraft 1.21.1 runtime graph contains older Log4j 2.22.1, Netty 4.1.97.Final, and LZ4 Java 1.8.0 artifacts with later advisories. These libraries are supplied and strictly versioned by Minecraft/NeoForge; they are not bundled into the ME Security JAR.

- The flagged Netty advisories concern TLS/SNI, native SSL, or IP-subnet-filter handlers. The Minecraft/NeoForge/AE2 application classes in this build do not reference those handlers, and the Minecraft game protocol used here is not configured through them.
- The flagged Log4j advisories concern XML/RFC5424 layouts, TLS socket appenders, or forbidden XML characters. The generated client and server configurations use console and rolling-file appenders with pattern layouts.
- Minecraft references LZ4 for optional region-file compression. The flagged issues require attacker-controlled compressed region data or invalid local ranges; ME Security neither accepts such data over its payloads nor invokes LZ4 itself.

Forcing replacement versions into Minecraft's strictly pinned runtime would create an unsupported platform and was therefore not done. Server operators should still treat imported world files as untrusted and follow NeoForge/Minecraft security updates for the base runtime.

## Remaining manual coverage

Before publishing to a broad audience, a final human gameplay session should exercise these interactions with the two connected graphical clients:

1. Owner placement, outsider denial, trust, each permission toggle, removal, and ownership transfer.
2. Wired terminals, AE2 wireless terminals, AE2 Wireless Terminals devices, and a quantum-linked network.
3. Shift-clicking, recipe transfer, item and fluid containers, crafting submission/control, and finished-product extraction.
4. Visual denial feedback, selected-player outline, text entry with the inventory key bound to `E`, and absence of ghost parts after denied placement.
5. Chunk unload/reload and a full server-process restart using a world that contains a protected multi-chunk network.

These paths share the server authorization checks covered by the automated suite, but graphical interaction, third-party recipe-transfer clients, real network latency, and multi-process world lifecycle behavior cannot be completely represented by GameTests.

## Stable boundaries

- Supported integrations are AE2 19.2.17 and optional AE2 Wireless Terminals 19.5.1 only.
- Pattern access and pattern encoding are owner-only in 1.0.0.
- Build covers AE2 cable-bus parts, cable-bus mining, and cable recoloring.
- Standalone AE2 blocks, storage-cell removal, external inventories/outputs, explosions, and arbitrary third-party tools remain outside the physical-protection boundary.
- Multiple terminals on one connected grid fail closed until the conflict is resolved.
