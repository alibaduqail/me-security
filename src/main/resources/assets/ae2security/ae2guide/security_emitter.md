---
navigation:
  parent: ae2:items-blocks-machines/items-blocks-machines-index.md
  title: ME Security Emitter
  icon: ae2security:security_emitter
  position: 216
categories:
- devices
item_ids:
- ae2security:security_emitter
---

# ME Security Emitter

<ItemImage id="ae2security:security_emitter" scale="2" float="left" />

The Security Emitter works like a [level emitter](ae2:items-blocks-machines/level_emitter.md),
but it reacts to security instead of stock. When someone is denied an action on a network
protected by a [Security Terminal](security_terminal.md), it emits a short redstone pulse,
two ticks long like an observer's. Every denied action gives its own pulse. What the signal drives is up
to you: a lamp, a note block, a door, or a latch that stays on until you reset it.

<br clear="all" />

## Recipe and placement

<Recipe id="ae2security:security_emitter" />

Attach it to a cable on the protected network. Like a level emitter, it powers the block
in front of it and needs the network to be powered, but it uses no channel. You can place
as many as you like.

## Settings

Only the network's owner can open its screen. The two buttons on the left choose:

| Setting | Options |
| --- | --- |
| Triggered by | **Strangers**: only players who are neither the owner nor trusted. **Everyone**: anyone who is denied, including a trusted player using a permission they do not have. |
| Redstone output | **Pulse when denied**: off until a denial, then a two-tick pulse. **Inverted pulse**: emits all the time and stops for two ticks on a denial. |

An emitter fires whether or not anyone carries an Intrusion Alert Card. Checks that run by
themselves, such as an open screen being rechecked, never trigger it. An operator using
the admin bypass is never denied, and nothing fires while conflicting terminals block the
network for everyone.

Anyone who can touch your network can trigger the signal, so connect only what you are
happy for a stranger to set off. Players with Build permission can still remove the
emitter like any other network hardware.
