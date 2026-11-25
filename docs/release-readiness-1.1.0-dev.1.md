> Historical assessment: superseded by 1.1.0-dev.2, which removes the Secure Drive. The checks and hash below apply only to the earlier artifact.

# ME Security 1.1.0-dev.1 candidate assessment

Assessment date: 2026-09-27. This is a development candidate, not a polished
release sign-off. The 1.0.0 assessment and hash apply only to that older artifact.

## Candidate identity

- Minecraft 1.21.1, Java 21, NeoForge 21.1.250, AE2 19.2.17, GuideME 21.1.1.
- Optional AE2 Wireless Terminals 19.5.1 was tested as a separate runtime.
- Payload protocol version 4. Client and server must use the same candidate.
- Artifact: `build/libs/ae2security-1.1.0-dev.1.jar`.
- SHA-256: `7d6303fc3da403ca4c756cfac09b4f654c43038e88ff9dc21f1475d453bd2d4c`.

## Verified in this workspace

`bash tools/verify.sh --offline` passed both dependency configurations
sequentially. Each completed the unit/build tasks and all 16 dedicated-server
GameTests. The last unit run recorded 59 tests, zero failures, errors, or skips.
The resource validator parsed 13 JSON resources and checked the maintained
terminal layers and Secure Drive pickaxe tag. The candidate JAR contains the
new guide page and mining tag, and has no audit-probe or `.DS_Store` entries.

The corrected in-world regression checks reject a far-away menu and an old menu
after block replacement, conserve a carried cell on closure, resolve the drive's
AE2 name and icon, and match stock-drive pickaxe speed. The PIN GameTests now
wait for background derivation. Unit tests cover server-lifetime cooldown state,
request rates, directory searches beyond 8,192 entries, and stable paging.
In a local 100-request PIN burst, server-thread admission took 4–5 ms and only
two workers were active; a second authorized player could still unlock. A
synthetic 50,000-entry directory page took 14 ms in the last local run. These are
regression fixtures, not production load benchmarks.

The isolated GuideME preview client reached its world and compiled both addon
pages, navigation, and item links. The log contained the pinned GuideME dark-mode
sprite warning and a saved-world mod-version warning because that preview world
had been opened with 1.0.0. Neither warning was attributed to an addon page error.
The client was stopped after log inspection, so its Gradle task ended with an
intentional interruption rather than a successful normal exit.

## Implemented findings

- F01–F03: live menu context, bounded background PIN operations, server-owned
  attempts and cooldowns, and stale-completion rejection.
- F04–F06: one background render, acknowledged PIN states, and deliberate
  revision-checked priority commits with invalid-draft and concurrent-change UI.
- F07–F09: policy edit acknowledgements, visible selection alignment, and
  authenticated bounded pages searched across the complete known-player directory.
- F10–F12: AE2 representative item and icon, pickaxe tag, and full-width search
  editor with a Minecraft tooltip.
- F13–F14: distinct candidate version and protocol, corrected release documents,
  and a read-only validator for the maintained texture layers.

## Qualification still required

The computer-use app inventory did not expose the launched Java client, so fresh
screen captures and direct UI interaction could not be performed in this session.
Before a polished release, run the plan's two-client dedicated-server matrix at
normal and small GUI scales, including PIN errors/cooldowns, transfer confirmation,
long names, keyboard focus, priority conflicts, and item-count checks. Repeat the
saved multi-chunk stop/start and unload/reload scenarios with real clients.

The current tests do not establish every Q1–Q5 integration and performance case:
full crafting and wireless action flows, all drive orientations and cable tints,
memory-card behavior, topology scaling, and third-party movers still need targeted
qualification. A packaged-JAR client/server smoke run and old/new protocol
rejection-message capture remain open. No current dependency-vulnerability audit
was performed. Publish only after those checks are recorded against this exact
artifact, or rebuild and record the new hash if any source or resource changes.
