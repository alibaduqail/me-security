# ME Security: full-mod audit and implementation plan

Audit date: 2026-09-27. Baseline: `ae26eb2`, including the pre-existing uncommitted Secure Drive work in this workspace. Runtime: Minecraft 1.21.1, NeoForge 21.1.250, AE2 19.2.17, GuideME 21.1.1, Java 21; optional AE2WTLib 19.5.1.

**Assessment:** the existing automated suite passes, but the current workspace is not ready to be called polished end to end. This audit identifies 14 actionable findings, including two high-priority server/menu problems. The requested deliverable is this investigation and implementation plan. Production code has not been changed.

## 1. Scope, evidence, and limitations

Reviewed all 51 main Java files, all eight test-source files, build configuration, mixin registration and implementations, recipes and advancements, language strings, models and blockstates, guide pages, texture-generation tooling, release documentation, and the existing UI screenshot. This includes both the Security Terminal and the unreleased Secure Drive.

Checked the actual cached AE2 19.2.17 bytecode by decompiling relevant dependency classes, and checked the generated Minecraft/NeoForge sources. Findings about inherited menus, widgets, naming, and rendering are based on this pinned implementation rather than assumptions about newer AE2 versions.

### Verification performed

| Check | Result |
| --- | --- |
| `./gradlew test build --offline --console=plain` | Pass: 52 tests, zero failures/errors/skips; distribution and sources JARs build. |
| `./gradlew runGameTestServer --offline --console=plain` | Pass: all 14 existing GameTests. |
| `./gradlew test build runGameTestServer -PwithWireless=true --offline --console=plain` | Pass: 52 unit tests and 14 GameTests with AE2WTLib loaded. |
| `./gradlew runGuideClient -PguideWorld=GuidePreview --offline --console=plain` | Client loads its existing isolated preview world; both pages, navigation entries, item links, and compiled page text are confirmed by logs. |
| Resource/JAR inspection | All project JSON parses. Expected resources are packaged. No `.DS_Store` entries in the distribution JAR. |
| Additional isolated diagnostic GameTest | Pass: 15 total tests, including a probe that confirms the current faulty range, naming, and mining behavior and measures PIN hashing. |
| Final normal build after diagnostics | Pass: 52 tests; verified no audit-probe classes in the normal JAR. |

The preview client was deliberately stopped after inspection; its Gradle task therefore ends with exit code **143**. That termination is not a client startup failure. The client log contains a GuideME dark-mode sprite-scaling warning belonging to the pinned dependency; it is not evidence of a broken addon guide page.

The computer-use tool could not attach to the launched Java window: it was absent from the app inventory and selection by `java` returned `Invalid app`. Consequently, **fresh interactive screenshots, a full two-client gameplay session, and visual verification at different GUI scales were not completed**. The checked-in screenshot is historical evidence only. UI findings below are marked as source-verified; claims about the precise visual appearance still require the specified client checks.

### Reproducible diagnostic evidence

The retained [audit-only probe](</Users/alibaduqail/Documents/ChatGPT/ae2 security terminal/docs/audit/AuditProbes.java>) produced:

```text
AUDIT_PROBE: drive menu and PIN remain usable at 100-block distance
AUDIT_PROBE: representative_name=Air
AUDIT_PROBE: diamond_pick_speed secure=1.0 stock=8.0
AUDIT_PROBE: mean_PIN_verify_ms=18.72805415
```

The timing is a small local sample after warm-up, not a production load benchmark. It demonstrates that the operation is significant relative to a 50 ms server tick. The probe asserts existing bad behavior intentionally; convert it into expected-correct regression tests during implementation. See [probe instructions](</Users/alibaduqail/Documents/ChatGPT/ae2 security terminal/docs/audit/README.md>).

## 2. Prioritized findings

P1 means fix before releasing the Secure Drive changes. P2 means functional or usability work required for the polished release. P3 means smaller polish or maintenance work. “Source” means a concrete implementation defect established by reading the relevant code, without an interactive reproduction this session.

| ID | Priority | Finding | Evidence |
| --- | --- | --- | --- |
| F01 | P1 | Secure Drive menu/actions remain valid at arbitrary distance; host-identity comparison checks the object against itself. | Runtime probe + source |
| F02 | P1 | Expensive PIN hashing runs on the server thread with no limit on successful retries or owner resets. | Timing probe + source |
| F03 | P2 | Static PIN-attempt state outlives an integrated server, but its cooldown clock restarts; entries also accumulate. | Source |
| F04 | P2 | Secure Drive renders its background twice per frame. | Pinned superclass + source |
| F05 | P2 | PIN flow lacks in-screen results, a pending state, and a synchronized cooldown display. | Source; visual confirmation pending |
| F06 | P2 | Priority input can silently disagree with the saved value and is cleared by screen reinitialization. | Source |
| F07 | P2 | Unrelated snapshots cancel ownership confirmation; pending policy actions look actionable but ignore clicks. | Source |
| F08 | P2 | Searching or paging can hide the player whose permission controls remain active. | Source |
| F09 | P2 | Player search silently excludes everyone beyond the first 8,192 snapshot entries. | Source |
| F10 | P2 | Secure Drive lacks AE2 representative-item registration; inherited naming reports “Air.” | Runtime probe + pinned API |
| F11 | P2 | Secure Drive is missing the pickaxe mining tag. | Runtime probe + resource inspection |
| F12 | P3 | Search field paints a wider box without widening its editor; its AE2 tooltip has no renderer in this screen. | Pinned widget/screen + source |
| F13 | P2 | Release identity and documentation do not distinguish the new work from the existing 1.0.0 artifact. | Build/docs inspection |
| F14 | P3 | Texture-generation script generates an obsolete texture rather than the three textures actually used. | Source/resources |

### F01 — Enforce a live, nearby Secure Drive menu context

**Evidence:** [SecureDriveMenu.java:100](</Users/alibaduqail/Documents/ChatGPT/ae2 security terminal/src/main/java/dev/ae2security/menu/SecureDriveMenu.java:100>), especially `stillValid` at line 114. The drive field and `getTarget()` refer to the same object, so comparing their UUIDs does not validate the block entity currently at that position. AE2's inherited `stillValid` does not provide a distance check for this block entity. Its separate broadcast-time replacement check is not a substitute for validating each action.

**Reproduction:** open a valid drive menu, move the player 100 blocks away, then submit its PIN. The diagnostic probe confirms that both `stillValid` and the unlock action still succeed. A normal stationary player often will not notice this; teleportation and a modified client make the defect clear. This does not grant an outsider Build permission or reveal the PIN, but it defeats the intended physical-menu boundary.

**Implementation:**

1. Capture the opening dimension, block position, and drive identity in the menu.
2. Create a nonrecursive server-side context validator: same actor, appropriate current menu, same dimension, loaded chunk, live block entity at the position is the expected drive, drive not removed, matching captured identity, and distance within the chosen interaction range. Use the Terminal's existing eight-block boundary unless a deliberate product change is made.
3. Check that context before PIN/reset/priority actions and cell mutations, including shift-click and inherited AE2 actions. Keep current Build and PIN-revision checks.
4. Invalidate and close stale menus consistently. Do not load chunks to keep a menu usable.
5. Keep block ownership recovery separate: the final owner must still be able to remove a drive after terminal retirement.

**Acceptance:** near-boundary access works; beyond-boundary access fails. Teleport, dimension change, block replacement at the same position, removal, unload, and stale menu IDs all reject actions. A queued request cannot operate on a newly placed drive. Item counts and carried stacks are conserved when invalidation closes the menu.

### F02 — Bound PIN work and remove hashing from the server tick

**Evidence:** [SecureDrivePin.java:15](</Users/alibaduqail/Documents/ChatGPT/ae2 security terminal/src/main/java/dev/ae2security/security/SecureDrivePin.java:15>), [SecureDriveBlockEntity.java:70](</Users/alibaduqail/Documents/ChatGPT/ae2 security terminal/src/main/java/dev/ae2security/blockentity/SecureDriveBlockEntity.java:70>), and [SecurityPayloads.java:23](</Users/alibaduqail/Documents/ChatGPT/ae2 security terminal/src/main/java/dev/ae2security/network/SecurityPayloads.java:23>). PBKDF2 runs with 120,000 iterations inside the enqueued server action. The failure cooldown does not throttle successful checks, and an unlocked menu still accepts another unlock request. Owner PIN resets are also unbounded.

**Impact:** a Build-authorized player who knows the PIN can repeatedly trigger expensive work. The measured mean is about 18.7 ms per verification locally; several requests in one tick consume a substantial part of, or exceed, its budget. This is a responsiveness problem and an authenticated resource-exhaustion path, not evidence of PIN disclosure.

**Implementation:**

1. Reject malformed inputs before hashing; reject redundant unlocks of an already unlocked session.
2. Allow at most one pending PIN operation per menu/player, with bounded per-player and per-drive request rates covering successes, failures, and resets.
3. Execute derivation on a small bounded executor with a bounded queue and explicit busy response. Do not create an unbounded pool or merely move unlimited work off-thread.
4. Pass only immutable operation data to the worker. Never access world, policy maps, menus, players, or inventories from that worker.
5. Return to the server thread to apply the result. Revalidate F01's context, current Build/owner permission, drive identity, PIN revision, and operation token. Discard stale completions after reset, revocation, closing, transfer, or replacement.
6. Shut the worker down on server stop. Never log or return PIN plaintext, salts, or hashes to clients.
7. Preserve the existing hash format and strength; this is scheduling/admission control work, not a reason to weaken hashing.

**Acceptance:** repeated success/reset requests cannot queue unlimited work or stall ticks; a concurrent legitimate player still receives service. Revoking Build or resetting a PIN while a verification is pending prevents that completion from unlocking. Closing the menu cancels or harmlessly discards its result. Record tick-time and queue metrics under a bounded stress fixture.

### F03 — Give PIN attempt tracking a correct server lifecycle

**Evidence:** [SecureDriveBlockEntity.java:32](</Users/alibaduqail/Documents/ChatGPT/ae2 security terminal/src/main/java/dev/ae2security/blockentity/SecureDriveBlockEntity.java:32>). `FAILED_ATTEMPTS` is static, indexed only by drive/player, and stores deadlines using the current server's tick counter. No server-stop cleanup exists. `resetForPlacement` changes the identity before calling `clearFailures`, so it cannot clear entries belonging to the old identity.

**Reproduction scenario:** trigger a cooldown after a single-player server has run for an hour, leave the world without exiting Minecraft, and reopen it. The same drive identity can encounter the previous server's approximately hour-long tick deadline against the new server's near-zero tick counter. A nominal 60-second wait can become much longer. A complete JVM restart, by contrast, drops the map. Attempts below the lockout threshold and abandoned drive IDs can remain indefinitely.

**Implementation:**

1. Move attempt state into a server-owned service used by F02, with explicit cleanup at server stop and drive retirement/replacement where appropriate.
2. Specify the cooldown contract. Recommended default: retain it over menu reopen, logout/relogin, and chunk reload within the same server; reset cleanly at a complete server stop unless restart persistence is explicitly implemented.
3. Use a monotonic clock valid for that service's lifetime, bounded expiry, and periodic cleanup. Do not carry old tick-counter deadlines into a new server instance.
4. Clear old identity entries before replacing the identity. Bound records for abandoned drives/players without allowing menu reopen to reset a live cooldown.
5. Send authoritative remaining time rather than repeating “Wait 60 seconds” after most of the cooldown has already elapsed.

**Acceptance:** five failures lock only that player; another player can still attempt the PIN. Reopen and chunk reload do not bypass the lockout. Switching worlds or reopening a world in the same JVM never imports an unrelated tick epoch. Expired/abandoned records are reclaimed. Tests use an injected clock, not sleeps.

### F04 — Render the Secure Drive background once

**Evidence:** [SecureDriveScreen.java:420](</Users/alibaduqail/Documents/ChatGPT/ae2 security terminal/src/main/java/dev/ae2security/client/SecureDriveScreen.java:420>). It explicitly calls `renderBackground` before `super.render`. Minecraft/NeoForge 1.21.1's `AbstractContainerScreen.render` already calls the virtual `renderBackground`, which draws the transparent world overlay and this screen's background.

**Impact:** the backdrop is darkened twice and the PIN/drive/priority background is drawn twice on every frame. The Security Terminal uses the normal single render path, so the two screens can look inconsistent.

**Implementation:** remove the redundant call while preserving the correct tooltip pass. Confirm the order of background, widgets, slots, labels, floating carried items, and tooltips. This is a small targeted change; it does not require replacing the screen framework.

**Acceptance:** one background pass per frame; comparable world dimming to a stock ME Drive; all three pages render correctly. Capture stock drive and Secure Drive views from the same scene. No implementation-mirroring unit test is needed for this one-line correction.

### F05 — Make the PIN flow an explicit, acknowledged screen state

**Evidence:** [SecureDriveScreen.java:224](</Users/alibaduqail/Documents/ChatGPT/ae2 security terminal/src/main/java/dev/ae2security/client/SecureDriveScreen.java:224>), [SecureDriveMenu.java:128](</Users/alibaduqail/Documents/ChatGPT/ae2 security terminal/src/main/java/dev/ae2security/menu/SecureDriveMenu.java:128>). Submission immediately clears the typed PIN and exits reset mode; the screen has no pending request or result. Errors and “PIN saved” are HUD action-bar messages rather than content in the opaque keypad panel. Cooldown state is not synchronized to its controls. Before initial data arrives, `canManage == false` also looks like denial rather than loading.

**Impact:** success/reset/failure can look like an unexplained cleared keypad, especially at smaller scaled viewports where the panel covers the action-bar area. The buttons invite retries during cooldown and can send another operation before the last one resolves. Exact occlusion needs the graphical acceptance check.

**Implementation:**

1. Define loading, owner-needs-PIN, waiting-for-owner, locked, submitting, wrong-PIN, cooldown, unlocked, resetting, and access-lost states. Drive them from authoritative menu responses.
2. Add a bounded server-to-client action result/state message, correlated with the current menu session and request. Include a result enum and cooldown duration; exclude credential material.
3. Show result text inside the panel with enough reserved height for wrapping. Disable submission while pending or cooling down. Preserve the existing rule that setting a PIN requires entering it again to unlock, and explain that transition visibly.
4. Change reset mode only after acknowledgement. Provide Cancel, clear focus on hidden widgets, and clear sensitive entry on close or loss of authorization.
5. Coordinate this protocol with F02/F03 rather than adding an unrelated second request state.

**Acceptance:** initial sync never flashes “No network access” as a final result. Wrong PIN, saved PIN, busy, cooldown, reset, and access loss are visible in the panel without relying on chat. Leading zeroes, physical keyboard, numpad, backspace/delete, Enter, and mouse keypad all work. At 250–500 ms simulated latency, one request produces one coherent transition. A late result for an old menu cannot unlock a new one.

### F06 — Make priority editing truthful and resilient

**Evidence:** [SecureDriveScreen.java:138](</Users/alibaduqail/Documents/ChatGPT/ae2 security terminal/src/main/java/dev/ae2security/client/SecureDriveScreen.java:138>), `openPriority`, `submitPriority`, and `init`. The filter accepts up to ten digits, including values outside a signed integer. Parsing failures are silently ignored, while valid prefixes are sent on every edit. `priorityPage` survives screen reinitialization but the new `priorityInput` is not repopulated. Open inputs also do not reconcile a second viewer's priority changes.

**Reproduction scenarios:** type `2147483648` normally: the field accepts it, the final value is rejected, and the server can retain the previously sent `214748364` prefix. Open Priority and resize: the recreated field is blank while the page remains open and the server still has its previous priority. Have two unlocked players edit one drive: a viewer can see an obsolete local value without knowing it.

**Implementation:**

1. Store a priority draft independently of the widget, plus last authoritative value and dirty/validation state.
2. Validate the full signed 32-bit range. Empty input, a lone minus sign, and overflow are draft states with visible feedback; they must not be represented as saved settings.
3. Prefer one deliberate commit on Enter/Done. If the back tab commits, label that behavior and block invalid commits. Escape/cancel should discard the draft. Apply the same semantics to step buttons.
4. Recreate the widget from the draft on resize. Preserve caret/selection where practical and prevent initialization from sending updates.
5. Reconcile server updates while not editing; while a local draft is dirty, expose a concurrent-change state and a deliberate overwrite/reload choice or revision check.
6. Keep the existing two-half synchronization of the full integer unless replacing it with an atomic state payload. Do not regress negative or large values to short precision.

**Acceptance:** `0`, `-1`, `32768`, `-32769`, `Integer.MIN_VALUE`, and `Integer.MAX_VALUE` round-trip. Over/underflow cannot silently appear saved. Step buttons clamp correctly. Resize, fullscreen toggle, focus changes, reopen, two viewers, and reset while editing all preserve a truthful UI/server state.

### F07 — Separate policy acknowledgements from unrelated snapshots

**Evidence:** [SecurityScreen.java:297](</Users/alibaduqail/Documents/ChatGPT/ae2 security terminal/src/main/java/dev/ae2security/client/SecurityScreen.java:297>) and [SecurityScreen.java:336](</Users/alibaduqail/Documents/ChatGPT/ae2 security terminal/src/main/java/dev/ae2security/client/SecurityScreen.java:336>). Any new snapshot clears `pending` and calls `clearTransferConfirmation`, including a directory-only update caused by another player logging in. Buttons remain active while `send` silently ignores non-toggle actions during `pending`.

**Reproduction:** begin typing a transfer confirmation, then let an unrelated player join for the first time. The server's directory revision produces a snapshot and the confirmation disappears. With latency, click a non-toggle action while another is pending: the button still appears enabled, but the action is discarded.

**Implementation:** identify which state actually changed: owner, selected target, trust eligibility, policy revision, directory, or topology status. Preserve confirmation across unrelated directory updates. Invalidate it when its target or authorization changes. Track action completion explicitly, preferably with request/result correlation; a directory snapshot is not an acknowledgement. Disable affected non-toggle buttons and show a pending state. Preserve ordered live-server toggles so the existing rapid-click behavior does not regress.

**Acceptance:** a login or name update for another player does not erase typed confirmation. A target/owner change safely invalidates it. Rapid Insert/Build toggles all apply in order. Stale trust/remove/transfer revisions receive a clear refresh/result and cannot leave the screen indefinitely pending. Keyboard focus never remains captured by a hidden confirmation field.

### F08 — Keep the visible list and active permission target aligned

**Evidence:** [SecurityScreen.java:71](</Users/alibaduqail/Documents/ChatGPT/ae2 security terminal/src/main/java/dev/ae2security/client/SecurityScreen.java:71>), `selection`, the search responder, and page actions. Filtering/paging updates visible rows but does not clear `selected`; the right panel resolves selection from the full snapshot.

**Reproduction:** select trusted Alice, then search for Bob. Alice disappears from the list while Alice's permission toggles and removal/transfer controls remain active. A no-results search can still leave an actionable access panel. The panel heading identifies Alice, but there is no selected row supporting that context.

**Implementation:** clear selection and confirmation when a user changes the query or page such that the selected row is no longer visible. After trusting/untrusting resorts rows, deliberately retain and reveal the same selected UUID, or clear it consistently; never silently retarget to a different row. Keep UUID as the identity throughout.

**Acceptance:** empty/no-results states have no hidden active target. Query changes, pagination, trust-sort changes, duplicate names, offline UUID fallback names, and a directory update never send an action to someone other than the explicitly selected player.

### F09 — Search the complete known-player directory

**Evidence:** [SecurityMenu.java:78](</Users/alibaduqail/Documents/ChatGPT/ae2 security terminal/src/main/java/dev/ae2security/menu/SecurityMenu.java:78>), [PolicySnapshot.java:13](</Users/alibaduqail/Documents/ChatGPT/ae2 security terminal/src/main/java/dev/ae2security/network/PolicySnapshot.java:13>), and the client-only filter. The server sorts and truncates to 8,192 entries before the user's search runs. There is no truncation flag or remote lookup.

**Impact:** long-running servers can have valid known players that the owner cannot find or trust. Raising the constant only postpones the same problem and increases snapshot traffic on every policy change.

**Implementation:** introduce bounded, authenticated server-side player search and pagination for an open owner menu. Separate policy state from directory pages so permission clicks do not retransmit the entire directory. Return total/result counts, an explicit page or continuation cursor, and a query sequence so outdated responses cannot replace a newer search. Preserve trusted-first ordering and deterministic name/UUID tie-breaking. Resolve the selected player by UUID even across page updates. Debounce client queries and bound server work and response size.

**Acceptance:** with 0, 1, 6, 7, 8,192, and more than 8,192 known players, the last player is discoverable by name and UUID. Every trusted player remains manageable. Stale query responses are ignored. Unauthorized or stale menus cannot enumerate the directory. Packet bounds and rates remain finite. Test a much larger synthetic directory for responsiveness without claiming support based solely on the threshold test.

### F10 — Register the Secure Drive's identity with AE2

**Evidence:** [MESecurity.java:85](</Users/alibaduqail/Documents/ChatGPT/ae2 security terminal/src/main/java/dev/ae2security/MESecurity.java:85>). Setup attaches the block entity type to the block but never calls AE2's representative-item registration. In the pinned `AEBaseBlockEntity`, the unregistered fallback is `Items.AIR`. The runtime probe confirms `drive.getName().getString()` returns `Air`.

**Impact:** inherited naming and exported memory-card source descriptions are wrong. The custom menu's explicitly supplied title hides this defect, so this is not a claim that the current screen title itself says “Air.”

**Implementation:** register the block entity type with `AEBaseBlockEntity.registerBlockEntityItem` and the Secure Drive item during common setup. Review inherited `getMainMenuIcon` as well; it currently returns the stock drive icon. Keep custom names and translated names working. Test memory-card behavior deliberately rather than accidentally enabling its generic priority path around the PIN gate.

**Acceptance:** the block entity's default name and settings source resolve to ME Secure Drive; custom names survive save/reload and ordinary recovery. Any main-menu icon refers to the Secure Drive. Memory cards cannot bypass PIN-protected priority writes.

### F11 — Restore ordinary drive mining behavior

**Evidence:** [SecureDriveBlock.java:19](</Users/alibaduqail/Documents/ChatGPT/ae2 security terminal/src/main/java/dev/ae2security/block/SecureDriveBlock.java:19>) uses AE2's metal properties, but there is no addon block-tag resource. AE2's pickaxe tag includes its own drive, not this addon block. The probe gives diamond-pickaxe destroy speed **1.0** for Secure Drive versus **8.0** for stock ME Drive.

**Implementation:** add `src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json` with `replace: false` and `ae2security:secure_drive`. Match the stock drive's tool-tier/drop behavior deliberately; do not add a new tier requirement merely because the block is metal. Keep owner-only mining and explosion protection intact.

**Acceptance:** the owner's tools mine at the expected speed, the correct block and installed cells are recovered once, and non-owners still cannot break the block with any tool or in Creative. Wrench recovery remains correct and does not duplicate cell drops.

### F12 — Finish search-field geometry and tooltip integration

**Evidence:** [SecurityScreen.java:121](</Users/alibaduqail/Documents/ChatGPT/ae2 security terminal/src/main/java/dev/ae2security/client/SecurityScreen.java:121>) and [SecurityScreen.java:470](</Users/alibaduqail/Documents/ChatGPT/ae2 security terminal/src/main/java/dev/ae2security/client/SecurityScreen.java:470>). `FullWidthSearchField` creates a 128-pixel AE2 editor and adds four painted pixels and a wider hit region. It never widens the underlying editing region. Also, `setTooltipMessage` populates AE2's `ITooltip`; rendering that interface occurs in `AEBaseScreen`, while this screen extends `AbstractContainerScreen`.

**Implementation:** make editor width, painted width, hitbox, clipping, and cursor calculations agree. Draw the extended background before the text, using a segmented/stretched middle rather than an overlay that only extends the frame. Use the normal Minecraft `Tooltip` API here, or explicitly render AE2 tooltip interfaces. Give fields meaningful narration labels instead of relying only on placeholders. Verify state narration for permission toggles as part of the same accessibility pass.

**Acceptance:** hover exposes the promised name/UUID search help; clicks at both edges place the cursor consistently; long text selection, horizontal scrolling, focused/unfocused backgrounds, and GUI scaling show no seams. Keyboard navigation and the configured inventory key still work inside both text fields. Keep this change narrow; a whole-screen framework migration is unnecessary.

### F13 — Give the upcoming artifact an accurate release identity

**Evidence:** [gradle.properties:3](</Users/alibaduqail/Documents/ChatGPT/ae2 security terminal/gradle.properties:3>) still emits `1.0.0` while [CHANGELOG.md](</Users/alibaduqail/Documents/ChatGPT/ae2 security terminal/CHANGELOG.md>) marks Secure Drive as unreleased. README describes the new drive next to a stable 1.0.0 declaration. [release-readiness-1.0.0.md:38](</Users/alibaduqail/Documents/ChatGPT/ae2 security terminal/docs/release-readiness-1.0.0.md:38>) links to a missing `review-2026-09-19.md`. Its historical 50-test/12-GameTest counts and hash cannot qualify the current work.

**Implementation:** select a distinct development/next-release version, such as a `1.1.0` development prerelease if that matches the project's release policy. Keep the historical release assessment historical; add a new assessment with the actual artifact/hash and completed qualification. Label unreleased README features until publication. Repair or remove the dead review link without inventing historical evidence. Update changelog, guide, and published listing for final behavior and exact supported versions. Check icon metadata and published image URLs as part of the release presentation pass; relative repository image links in `MODRINTH.md` should not be assumed to work on an external listing.

Coordinate network compatibility versions with the actual protocol changes in F05/F07/F09. A version bump is not permission to loosen the exact dependency pins or claim compatibility with untested addons.

**Acceptance:** build filename, metadata, changelog, README, release notes, and tested artifact identify the same release. Old/new client-server combinations either work as intentionally supported or reject clearly. Documentation links and remote images resolve in their publication context. No current readiness claim rests on the old hash or unperformed multiplayer checks.

### F14 — Make texture maintenance reproducible

**Evidence:** [generate_textures.py:42](</Users/alibaduqail/Documents/ChatGPT/ae2 security terminal/tools/generate_textures.py:42>) writes `security_terminal_face.png` and `blank.png`; current item/part models use `security_terminal_bright.png`, `security_terminal_medium.png`, and `security_terminal_dark.png` instead.

**Implementation:** either update the generator to produce the three authoritative layers from documented source artwork, or explicitly retire it and document the actual asset workflow. Do not run the old generator over production resources expecting it to regenerate the current face. Remove obsolete cleanup behavior only after checking whether any maintained assets depend on it.

**Acceptance:** generating into a temporary directory reproduces the intended dimensions, alpha, and pixel layers without modifying unrelated files. Model references resolve. On/off/channel states, cable tinting, item appearance, and guide item rendering remain recognizable. Pixel-perfect output checks are appropriate only if the generator is intended to be deterministic.

## 3. Investigation and qualification work that is not yet a confirmed bug

These are explicit gaps and risks, not additional defects counted in the 14 findings.

### Q1. Real client/server synchronization and carried items

Current drive GameTests instantiate server menus directly. They do not validate the complete sequence of open-menu packets, masked cell contents, data slots, client prediction, correction packets, and screen transitions. In particular, the screen suppresses slot drawing/click dispatch on PIN/Priority pages, but its underlying slots remain active and vanilla still has hover/carried-stack logic.

Exercise unlock, relock, priority-page entry, guide navigation, owner reset from another client, and Build revocation while a cell is on the cursor. Check ordinary clicks, shift-click, right-click, drag, number keys, offhand swap, drop, double-click collection, and Creative cloning. Establish that nothing is lost, duplicated, silently exposed, or drawn over the keypad unexpectedly. If active hidden slots cause integration or hover artifacts, gate their activity using a coherent page/session model. Do not blindly make all slot contents empty on the client; synchronization must still work.

### Q2. Real world lifecycle and conservative topology

The suite covers NBT round trips and a loaded split/rejoin. It does not establish a complete multi-process restart/unload matrix. Qualify terminal unload before remote chunks, inverse load order, split across unloaded chunks, cross-dimension/quantum paths, PIN reset followed by restart, ownership transfer followed by retirement, and final-owner drive recovery.

Preserve fail-closed behavior on missing policies. Test malformed/partial saved data and define an administrator recovery story separately from ordinary player access. Historical footprints accumulate conservatively; do not prune them simply to make a test pass or expose detached storage earlier.

### Q3. Cost of grid checks and full-directory copies

`SecurityGridService.terminalIds()` scans connected nodes on each call; `onServerEndTick` reconstructs chunk sets and walks nodes every tick, including public grids. Storage authorization invokes this path repeatedly. `SecuritySavedData.players()` returns a fresh map copy, including for existence checks. This establishes unnecessary work, but this audit did not measure a production-scale slowdown.

Profile stable public/protected grids at increasing node counts and repeated storage actions before prescribing a large refactor. A likely improvement is cached terminal membership, chunk reference counts, and topology-dirty generations updated at node callbacks, plus direct directory lookup methods. Any cache must invalidate synchronously on add/remove/merge/retire and preserve the delayed split proof. Acceptance must cover authorization correctness and measured allocations/tick time, not just a microbenchmark win.

### Q4. All visual states and inherited block interactions

Verify all 24 drive orientations, populated/empty cells, status LEDs, powered/unpowered/channel-starved states, every cable tint, stock/custom resource packs, and inventory/hand/guide models. Reusing `ae2:block/drive` is not itself a defect: the pinned AE2 registers its built-in drive model at that resource location.

Audit memory-card import/export, normal wrench rotation, alternate-use dismantling, controller/subnet changes, and any spatial movement within the advertised integration scope. Priority imports currently pass through a server-disabled setter; preserve that protection while deciding what settings interactions should report to players. Do not broaden the promised physical protection to arbitrary third-party movers, tools, or inventories without separate implementation and testing.

### Q5. End-to-end enforcement coverage

Existing tests are useful but do not exercise every full crafting, wireless, and packet path. The optional-integration unit test confirms the patched class loads; that is not proof of every restock transfer. Add scenario-level coverage for manual crafting ingredients, fluid containers, autocrafting calculation/submission, pause/cancel, result extraction, pattern access/encoding, wireless range/pairing, and quantum linking. Use an actual owner, trusted player, and outsider on a dedicated server for final acceptance.

No fresh dependency-vulnerability audit was performed. The older release document's advisory assessment is historical and should not be represented as a current result.

## 4. Implementation sequence

Keep changes in reviewable units, each with a concrete regression and acceptance result. The work does not require an AE2AddonLib migration or a security-system rewrite. Preserve the existing uncommitted feature work before starting implementation; do not reset the workspace to the audit commit.

| Order | Work package | Findings | Dependencies | Completion evidence |
| --- | --- | --- | --- | --- |
| 0 | Capture a reproducible baseline and fixtures. | All | None | Snapshot current work; document supported matrix; separate disposable test worlds; turn diagnostic observations into expected-correct tests. |
| 1 | Fix drive context and ordinary block integration. | F01, F10, F11 | 0 | Distance/replacement regressions pass; proper representative name and mining tag; drop conservation. |
| 2 | Build bounded PIN operations and server-owned attempt state. | F02, F03 | 1 | Controlled clock tests, bounded worker/admission tests, stale-result rejection, stress timing. |
| 3 | Introduce acknowledged drive states and finish its UI. | F04, F05, F06 | 2 for PIN states | Client/server state tests; GUI screenshots; valid priority commits; no duplicate background render. |
| 4 | Repair terminal selection, transfer, pending, and widget behavior. | F07, F08, F12 | 0; coordinate protocol with 5 | Latency/selection tests; no unrelated transfer reset; tooltip and keyboard checks. |
| 5 | Add complete bounded player-directory search. | F09 | 4's selection/ack model | Large-directory query tests, deterministic paging, stale response suppression, bounded packets. |
| 6 | Resolve performance/compatibility risks supported by measurement. | Q1–Q5 | Relevant earlier packages | Profiles and targeted fixes; complete lifecycle and integration scenarios. |
| 7 | Repair asset workflow and prepare release docs/build identity. | F13, F14 | Version can be set early; final docs after 6 | Reproducible resources, working publication links, unique candidate version, clean packaged artifact. |
| 8 | Qualify the exact candidate and sign off. | All | 1–7 | Both dependency matrices pass; two-client visual/gameplay evidence; exact hash; no open release blockers. |

### Suggested boundaries for code changes

- **Menu/context patch:** `SecureDriveMenu`, minimal block-entity helper methods, drive GameTests. Avoid mixing screen layout changes into the permission fix.
- **PIN service patch:** `SecureDrivePin` remains a pure hashing utility; a new server-owned operation/attempt service handles admission, clocks, executor lifecycle, and result validation. `SecureDriveBlockEntity` owns persisted PIN data and revisions. `SecureDriveMenu` owns the opening's session.
- **Drive protocol/UI patch:** action/result types, payload registration, menu snapshot/state, translations, and `SecureDriveScreen`. Avoid widget booleans that disagree with authoritative states.
- **Terminal UI patch:** selection and confirmation state in a small testable state model, with rendering/widgets remaining in `SecurityScreen`. Preserve UUID identity and ordered permission intents.
- **Directory patch:** query/page payloads, server owner-menu checks, directory lookup/search API, and the client paging model. Do not turn one missing-player bug into unlimited payloads.
- **Block/resource patch:** representative item registration, mining tag, main-menu icon, and targeted tests.
- **Release patch:** version and protocol compatibility checks, docs, asset workflow, and automated verification configuration. Keep historical release evidence separate from new evidence.

## 5. Detailed acceptance matrix

### Permissions and actions

Use distinct owner, trusted player, and outsider UUIDs. For each supported menu/device, test public, protected, unresolved, and conflicting networks where applicable.

| Principal/state | Required result |
| --- | --- |
| Owner | Manage terminal; full network access; set/reset PIN; drive cell/priority access still requires this opening's PIN; owner recovery works without PIN. |
| Trusted, View only | Read supported network views; no insert/extract/craft/build; no drive opening. |
| Trusted, Insert only beyond View | Item/fluid insertion succeeds; withdrawal and manual network ingredients fail. |
| Trusted, Extract only beyond View | Item/fluid withdrawal succeeds; insertion and autocrafting requests fail. |
| Trusted, Craft only beyond View | Calculate/submit/pause/cancel; CPU's authorized initial ingredients work; collecting products still requires Extract. |
| Trusted, Build only beyond View | Cable actions work; drive opens but cells/priority remain PIN-gated; no terminal or drive removal. |
| Outsider | No protected view/storage/crafting/settings; no protected cable changes or drive removal. |
| Revoked player | Subsequent actions denied immediately; affected menu closes/relocks; delayed responses cannot restore access. |
| Former owner | Owner privileges disappear after transfer; existing other trusted entries remain. |
| Machine automation | Existing principal-free network automation remains functional; drive cell-slot item handlers remain unavailable. |

For combinations, retain the 32-mask policy tests and add integration scenarios for the boundaries most likely to interact: Craft without Extract, Build without Extract, and Build revoked during PIN verification.

### Secure Drive lifecycle

1. Place through the real item, in survival and Creative, against each legal face. Test no terminal, exactly one, unresolved policy, and multiple terminals. Denied placement consumes nothing and leaves no ghost block.
2. Set a PIN with leading zeroes; incorrect PIN fails; correct PIN unlocks only the current opening. Close/reopen requires PIN again.
3. Reset from a second owner menu while the first session is unlocked, while it is hashing, while Priority is open, and while carrying a cell. Old work/results cannot unlock or alter the new revision.
4. Validate all inventory gestures with unique named cells and known item/fluid contents. Count the cells and their stored contents before and after every denial/closure.
5. Edit priority through its supported UI and attempt the generic AE2 menu/settings paths. Validate signed limits and simultaneous viewers.
6. Transfer ownership; verify old/new owner rights, preserved other trust, and current PIN behavior.
7. Remove/retire the terminal, restart, and recover the locked drive as final owner. Confirm exactly one drive item and the original cells, with no previous PIN/binding carried into a new placement.
8. Verify explosion immunity, forbidden non-owner mining, valid owner mining/wrench recovery, and correct tool speed.

### Client polish

- Capture both screens at common window sizes and every usable GUI scale, including a 320×240 scaled viewport. Confirm the 304×218 Terminal and outside tabs remain reachable; add compact layout/scrolling if the actual supported viewport exposes clipping.
- Cover loading, empty directory, no search results, one page, multiple pages, untrusted selection, trusted selection, every permission combination, transfer confirmation, pending actions, conflict, and disconnected states.
- Cover every PIN state and normal/priority pages, populated inventories, item tooltips, guide tooltips, and carried-stack transitions.
- Test longest valid player names, UUID fallbacks, Unicode/custom names where supported, forced Unicode font, and an expanded-language resource pack. Wrap/truncate deliberately and expose full values via tooltips.
- Test mouse and keyboard focus order, arrows/Tab/Shift-Tab, Enter/Escape, number row and numpad, nondefault inventory key, text selection, paste into priority/search, and resize while focused. Never narrate an entered PIN.
- Test multiple rapid toggles, delayed trust results, out-of-order search responses, login during transfer, two viewers of one drive, and a server rejection while a control is visible.
- Compare drive/world models to stock AE2 at identical lighting. Confirm double dimming is gone and any remaining artifacts belong to the correct component.

### Persistence and network protocol

- Test actual server stop/start in a saved multi-chunk world, not only serialization of an isolated object.
- Test terminal-first and remote-chunk-first reload, verified splits, unresolved splits, rejoin, conflict creation/recovery, and power/channel loss.
- Round-trip every payload codec; exercise invalid enum/length/count handling, stale menu IDs, stale/future policy revisions, mismatched query sequences, and packet bursts. Denials must neither mutate state nor crash the server.
- Keep a client/server version matrix: matching candidate, existing stable versus candidate in both directions, optional Wireless installed/missing as intended. Capture the actual rejection message for incompatible combinations.

## 6. Repeatable validation and release gates

Add a CI workflow or equivalent reproducible script; the repository currently has no checked-in CI workflow. Run the two dependency configurations separately, with isolated GameTest directories when concurrent execution is desired. Do not run parallel servers against the same `run/world`.

Required automated jobs:

1. Java 21 compile/build with deprecation warnings visible.
2. Unit and meaningful state/codec tests.
3. Dedicated GameTests without Wireless Terminals.
4. Unit/GameTests with `-PwithWireless=true`, including behavioral wireless scenarios as coverage is added.
5. JSON parsing, translation/reference checks, block tags, model/texture references, both guide page compilation checks in a supported client environment, and JAR inspection.
6. Packaged-JAR client/server smoke test, not solely a source-classpath development run.

Required manual evidence before a polished-release claim:

- A two-client dedicated-server session completing the role/action and drive matrices.
- Fresh screenshots of the corrected UI states at small and normal scales, including invalid priority, cooldown, transfer, and long-name cases.
- A real saved-world restart/unload run with item-count checks.
- Recorded disposition for Q1–Q5, with remaining scope limits accurately documented.

Release gates:

- F01–F11 and F13 resolved; F12/F14 completed for the requested polish pass or explicitly scheduled with an accepted narrow limitation.
- No unauthorized state mutation, duplication, loss, stale-session unlock, or unbounded PIN work in the qualified scenarios.
- No unexplained client/resource/mixin errors. Dependency warnings are classified rather than silently discarded or misattributed.
- Unique candidate version and protocol identity; exact artifact SHA-256; no diagnostic classes accidentally packaged.
- Documentation describes verified behavior and clearly distinguishes intended protection boundaries from bugs and unfinished validation.

Passing today's tests is the starting point. Completion means the fixes above are implemented and the exact candidate passes the full acceptance evidence, including the gameplay checks that could not be performed in this audit.
