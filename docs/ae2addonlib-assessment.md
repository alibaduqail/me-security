# AE2AddonLib assessment for ME Security

Date: 2026-09-22

## Bottom line

AE2AddonLib **is compatible on paper with this project's runtime**, but it is **not a foundation for a more reliable security rewrite**. The published `1.0.3-1.21.1` artifact targets Minecraft 1.21.1, compiles against NeoForge 21.1.203 and AE2 19.2.16, and declares runtime minimums of NeoForge 21.1.203 and AE2 19.2.16. Therefore NeoForge 21.1.250 and AE2 19.2.17 satisfy its declared ranges. Its Maven artifact is published as `io.github.pedroksl:ae2addonlib:1.0.3-1.21.1`. Sources: [release properties](https://github.com/pedroksl/AE2AddonLib/blob/6f29c148b29d3d5993d0b55c766382fd81b062ba/gradle.properties), [mod metadata template](https://github.com/pedroksl/AE2AddonLib/blob/6f29c148b29d3d5993d0b55c766382fd81b062ba/src/main/templates/META-INF/neoforge.mods.toml), [published POM](https://repo1.maven.org/maven2/io/github/pedroksl/ae2addonlib/1.0.3-1.21.1/ae2addonlib-1.0.3-1.21.1.pom).

However, the library is a collection of registry, UI-widget, networking, datagen, fluid-slot, and wireless-linked-item conveniences. It has no security service, permission model, authorization callback, storage/crafting enforcement hook, cable placement/removal hook, or general event layer around AE2's sensitive operations. Its complete 1.21.1 source tree contains no mixins and no equivalents for the internal AE2 methods ME Security currently intercepts. See the [1.21.1 source tree](https://github.com/pedroksl/AE2AddonLib/tree/7be8135ca985e917498981f247be1985bf77b3d8/src/main/java/net/pedroksl/ae2addonlib) and the library's own [feature inventory](https://github.com/pedroksl/AE2AddonLib/blob/7be8135ca985e917498981f247be1985bf77b3d8/README.md).

Recommendation: **do not rewrite ME Security around AE2AddonLib, and do not add it as a required dependency merely to reduce boilerplate**. Keep the security model and tests, reduce mixins only where NeoForge/AE2 public hooks can provide equivalent server-side guarantees, and treat each remaining mixin as a deliberately version-pinned enforcement adapter. AE2AddonLib could be evaluated later for a UI-only cleanup, but that would not improve the reliability of authorization.

## Compatibility with the current stack

| Component | ME Security | AE2AddonLib 1.0.3-1.21.1 | Assessment |
|---|---:|---:|---|
| Minecraft | 1.21.1 | exactly 1.21.1 | Compatible |
| Java | 21 | Java 21 toolchain | Compatible |
| NeoForge | 21.1.250 | compiled with 21.1.203; manifest requires `[21.1.203,)` | Declared compatible |
| AE2 | 19.2.17 | compiled with 19.2.16; manifest requires `[19.2.16,)` | Declared compatible; binary compatibility still depends on the internal AE2 types the library imports |
| GuideME | 21.1.1 compile pin in this repo | published POM brings 21.1.14 at runtime | Version-resolution change must be tested |

The published POM makes AE2 a compile dependency and GuideME 21.1.14 a runtime dependency. Adding it to this build would therefore alter the dependency graph rather than acting as a source-only helper. Gradle would normally select the newer direct/transitive version, but this project deliberately uses exact pins, so dependency resolution and dedicated-server startup should be verified before any experiment. Sources: [AE2AddonLib build](https://github.com/pedroksl/AE2AddonLib/blob/6f29c148b29d3d5993d0b55c766382fd81b062ba/build.gradle), [published POM](https://repo1.maven.org/maven2/io/github/pedroksl/ae2addonlib/1.0.3-1.21.1/ae2addonlib-1.0.3-1.21.1.pom).

The current `1.21.1` branch has advanced to a source version labelled `1.0.4-1.21.1`, but Maven Central does not contain that version; the newest published 1.21.1 artifact is still `1.0.3-1.21.1`. The branch head is a May 4, 2026 merge, while the latest tagged 1.21.1 release is November 14, 2025. Sources: [1.21.1 branch head](https://github.com/pedroksl/AE2AddonLib/commit/7be8135ca985e917498981f247be1985bf77b3d8), [1.0.3 tag commit](https://github.com/pedroksl/AE2AddonLib/commit/6f29c148b29d3d5993d0b55c766382fd81b062ba), [Maven metadata](https://repo1.maven.org/maven2/io/github/pedroksl/ae2addonlib/maven-metadata.xml).

## What the library could help with

### Small, optional reductions in boilerplate

- `MenuRegistry` wraps a NeoForge deferred menu register and AE2's `MenuTypeBuilder`. It is usable only for menus extending `AEBaseMenu`; ME Security's `SecurityMenu` currently extends vanilla `AbstractContainerMenu`, so adoption would itself require a menu refactor. It does not add access checks. Source: [`MenuRegistry`](https://github.com/pedroksl/AE2AddonLib/blob/7be8135ca985e917498981f247be1985bf77b3d8/src/main/java/net/pedroksl/ae2addonlib/registry/MenuRegistry.java).
- `NetworkHandler` shortens payload registration for records implementing AE2's `ClientboundPacket` or `ServerboundPacket`. It could replace a few lines in `SecurityPayloads`, but the present NeoForge `PayloadRegistrar` code is already small and explicit. Source: [`NetworkHandler`](https://github.com/pedroksl/AE2AddonLib/blob/7be8135ca985e917498981f247be1985bf77b3d8/src/main/java/net/pedroksl/ae2addonlib/network/NetworkHandler.java).
- Widget classes such as `AddonIconButton`, `AddonActionButton`, `AddonSettingToggleButton`, and `ToolbarActionButton` could be used in the owner UI. They are presentation helpers only; they cannot make server authorization safer. Source: [widget package](https://github.com/pedroksl/AE2AddonLib/tree/7be8135ca985e917498981f247be1985bf77b3d8/src/main/java/net/pedroksl/ae2addonlib/client/widgets).
- The block, item, menu, component, creative-tab, and block-entity registry wrappers could reduce registration boilerplate. The library does not have a dedicated AE2 cable-part registry helper, so the Security Terminal's core part integration remains ME Security's responsibility. Source: [registry package](https://github.com/pedroksl/AE2AddonLib/tree/7be8135ca985e917498981f247be1985bf77b3d8/src/main/java/net/pedroksl/ae2addonlib/registry).
- `IGridLinkedItem` is useful when creating a wireless item that links to an AE2 access point. It reads AE2's `WIRELESS_LINK_TARGET`, resolves an `IWirelessAccessPoint`, and offers `onLink`/`onUnlink` callbacks. ME Security is currently a cable part, so this API does not replace grid discovery or any enforcement hook. Source: [`IGridLinkedItem`](https://github.com/pedroksl/AE2AddonLib/blob/7be8135ca985e917498981f247be1985bf77b3d8/src/main/java/net/pedroksl/ae2addonlib/api/IGridLinkedItem.java).

None of these helpers justifies a whole-mod rewrite. At most, they support a narrowly scoped UI or registration cleanup.

## What it cannot replace

| Current enforcement area | Current targets | AE2AddonLib substitute? | Recommendation |
|---|---|---|---|
| Network storage transfer | `NetworkStorage.insert/extract` | No | Keep a version-pinned enforcement adapter until AE2 exposes a public transaction authorization hook. This is a security boundary, not registration boilerplate. |
| Menu lifetime and interaction | `AEBaseMenu`, `MEStorageMenu`, menu broadcast/inventory paths | No | Keep server-side checks. Consolidate shared policy logic, but do not rely on client UI visibility. |
| Menu opening | `MenuTypeBuilder.open` / `MenuHostLocator.locate` | No. Its `MenuRegistry` calls the same `MenuTypeBuilder`; it does not wrap authorization. | Keep the opening check or replace only with an equivalent AE2-supported callback if one is added. |
| AE2 server packets | `ServerboundPacket` dispatch | No. Its networking helper itself uses `appeng.core.network.ServerboundPacket`. | Do not migrate security packets merely for convenience; doing so increases coupling to the same internal packet layer currently being intercepted. |
| Crafting calculation/submission/control | `CraftingService`, `CraftingCpuLogic`, `CraftingCPUMenu` | No | Keep enforcement at calculation, submission, and control boundaries; no library callback covers them. |
| Cable-part placement, removal, recolor, whole-block break | `PartPlacement`, `CablePart`, `CableBusBlockEntity`, NeoForge break event | No | Existing event coverage should be preferred where complete; the part-specific paths still need AE2-aware enforcement. |
| Pattern management | `PatternAccessTermMenu` | No | Keep owner-only server validation. |
| Internal grid-node location access | `InWorldGridNode` accessor | No | Isolate this accessor behind a small adapter and test it against every supported AE2 pin. |
| AE2WTLib wireless restock | AE2WTLib `MagnetHandler` | No | Remains an explicit optional-integration patch; AE2AddonLib is unrelated to AE2WTLib interception. |

This conclusion follows both from the published feature set and from the actual 1.21.1 source inventory: the library has no classes referencing `NetworkStorage`, `CraftingService`, `MEStorageMenu`, `PartPlacement`, security permissions, or NeoForge player/block interaction events. Source: [complete source tree](https://github.com/pedroksl/AE2AddonLib/tree/7be8135ca985e917498981f247be1985bf77b3d8/src/main/java/net/pedroksl/ae2addonlib).

## Reliability and maintenance risks

1. **It adds a runtime mod and another compatibility surface.** This project currently controls its AE2/NeoForge pins directly. AE2AddonLib adds its own AE2 and GuideME dependency metadata and must be installed on both sides according to its mod metadata. That is more deployment and resolution surface for very little security benefit.

2. **Several helpers use AE2 implementation packages, not only public API packages.** For example, `NetworkHandler` imports `appeng.core.network.*`, `MenuRegistry` imports `appeng.menu.*`, screens use `appeng.init.client.InitScreens`, and `IGridLinkedItem` calls `appeng.util.Platform`. These are exactly the kinds of version-sensitive dependencies a reliability rewrite should reduce. Sources: [`NetworkHandler`](https://github.com/pedroksl/AE2AddonLib/blob/7be8135ca985e917498981f247be1985bf77b3d8/src/main/java/net/pedroksl/ae2addonlib/network/NetworkHandler.java), [`MenuRegistry`](https://github.com/pedroksl/AE2AddonLib/blob/7be8135ca985e917498981f247be1985bf77b3d8/src/main/java/net/pedroksl/ae2addonlib/registry/MenuRegistry.java), [`IGridLinkedItem`](https://github.com/pedroksl/AE2AddonLib/blob/7be8135ca985e917498981f247be1985bf77b3d8/src/main/java/net/pedroksl/ae2addonlib/api/IGridLinkedItem.java), [main mod class](https://github.com/pedroksl/AE2AddonLib/blob/7be8135ca985e917498981f247be1985bf77b3d8/src/main/java/net/pedroksl/ae2addonlib/AE2AddonLib.java).

3. **Maintenance is active but small and split across game versions.** The repository was created in October 2025, has about 30 commits, one principal maintainer, and recent work is focused on the newer 26.1 branch. The 1.21.1 branch did receive a February 2026 `IGridLinkedItem` enhancement and a May 2026 merge, but no newer 1.21.1 artifact has been published. Sources: [repository activity](https://github.com/pedroksl/AE2AddonLib), [February commit](https://github.com/pedroksl/AE2AddonLib/commit/dcbbf353809ea2689bf08be033ac10bfdf968a0f), [branch head](https://github.com/pedroksl/AE2AddonLib/commit/7be8135ca985e917498981f247be1985bf77b3d8), [Maven metadata](https://repo1.maven.org/maven2/io/github/pedroksl/ae2addonlib/maven-metadata.xml).

4. **The CI is build-only.** The repository's workflow runs `./gradlew build`, and the source tree contains no test source set or game tests. Its convenience code may be useful, but there is no upstream regression suite demonstrating security-sensitive behavior or broad binary compatibility. Source: [build workflow](https://github.com/pedroksl/AE2AddonLib/blob/7be8135ca985e917498981f247be1985bf77b3d8/.github/workflows/build.yml).

5. **License metadata is inconsistent.** `gradle.properties` and the published POM say LGPL-3.0, while the repository's `LICENSE` file contains GPL-3.0 and GitHub identifies the repository as GPL-3.0. Depending on the library as a separate mod is usually less entangled than copying code, but this inconsistency should be resolved with the maintainer before vendoring or adapting source. Sources: [declared build license](https://github.com/pedroksl/AE2AddonLib/blob/7be8135ca985e917498981f247be1985bf77b3d8/gradle.properties), [license file](https://github.com/pedroksl/AE2AddonLib/blob/7be8135ca985e917498981f247be1985bf77b3d8/LICENSE), [published POM](https://repo1.maven.org/maven2/io/github/pedroksl/ae2addonlib/1.0.3-1.21.1/ae2addonlib-1.0.3-1.21.1.pom).

## Recommended rewrite direction

If reliability is the goal, use a **security-core plus version adapter** design rather than an AE2AddonLib rewrite:

1. Keep policy persistence, access decisions, and action-source attribution free of mixin/AE2 implementation details.
2. Group version-sensitive hooks by boundary (menu, storage, crafting, topology, optional AE2WTLib) and keep each adapter small.
3. Replace a mixin only when a NeoForge event or documented AE2 public API provides the same server-side, fail-closed guarantee. A hook count reduction is not valuable if it opens a bypass.
4. Continue exact AE2/NeoForge pinning and make injection validation plus focused GameTests part of release qualification.
5. If desired, run a separate UI-only spike with AE2AddonLib `1.0.3-1.21.1`; measure how much custom code it actually deletes before accepting a required runtime dependency. Do not mix that spike with the enforcement rewrite.

In short: **AE2AddonLib is compatible and competently useful for ordinary AE2 addon ergonomics, but it does not solve ME Security's hard problem.** The mod's reliability depends on complete, server-side enforcement over AE2 operations. This library neither supplies those hooks nor removes the need for the current mixin boundaries.
