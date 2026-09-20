---
navigation:
  parent: ae2:items-blocks-machines/items-blocks-machines-index.md
  title: ME Security Terminal
  icon: ae2security:security_terminal
  position: 215
categories:
- devices
item_ids:
- ae2security:security_terminal
---

# ME Security Terminal

<ItemImage id="ae2security:security_terminal" scale="2" float="left" />

The Security Terminal makes an ME network private. The player who places it becomes
the owner and chooses who can use the network. Place one terminal per connected
network; the owner always has every permission.

<br clear="all" />

## Recipe and placement

<Recipe id="ae2security:security_terminal" />

Attach it to an ME cable like other [terminals](ae2:items-blocks-machines/terminals.md).
It is a [cable subpart](ae2:ae2-mechanics/cable-subparts.md), uses one channel, and
matches the color of its cable. Without a central cable, parts sharing a block may
belong to separate networks. Connect the Security Terminal to the network you want
to protect.

Right-click its face to manage access. Only its owner can open this screen.

## Trusting a player

1. Search for a player who has joined the server, using their name or UUID.
2. Select their row in the player list. The outline marks the player being edited,
   and the access panel shows their name.
3. Click **Trust player**. Trust allows viewing storage and crafting information.
4. Enable any additional permissions using the buttons below their name. A
   highlighted, pressed button means the permission is on.

New trusted players start with viewing access only. **Remove trust** revokes all
their access. Reducing permissions closes that player's affected open ME screen;
subsequent actions use the new permissions immediately.

## Permissions

| Permission | What it allows |
| --- | --- |
| Insert | Deposit items and fluids, including shift-clicking and fluid containers. |
| Extract | Withdraw items and fluids, and take network ingredients for manual crafting. |
| Craft | Request autocrafting and pause or cancel jobs. Collecting finished products still requires Extract. |
| Build | Place, remove, and recolor AE2 cables and cable parts attached to this network. |

Trust always includes viewing access. Build does not grant Insert, Extract, or Craft.
Pattern management and changing security settings remain owner-only.

## How Build works

Build checks the networks affected by an action. A differently colored cable that
does not connect to your network does not need your permission. An ordinary terminal
or storage bus does not join another network just because it faces that network.

A new central cable can connect the parts already in its block. A toggle bus may
touch a network on either side, even while switched off. Those connections need Build
permission for the affected protected networks. Recoloring also needs permission
when it would connect to a protected neighbor.

Use an AE2 wrench to remove an individual part. Breaking the whole cable-bus block
checks every part it contains. **Only the owner may remove the Security Terminal or
break its containing block**, even if another player has Build or is in Creative mode.

## Transferring ownership

Select a trusted player and click **Transfer ownership**. Type that player's name in
the confirmation field, then click **Confirm transfer**. Other trusted entries stay
in place. The previous owner loses access unless the new owner trusts them afterward.

## Wireless access and automation

Wireless terminals still use AE2's normal pairing, range, and quantum-link rules.
Holding a paired terminal does not grant access. The same permissions apply to
supported AE2 Wireless Terminals devices.

Normal machine automation continues. Autocrafting jobs already accepted by a CPU
continue even if their initiating player later loses permission.

## Disconnections and conflicts

Power loss or running out of channels does not disable protection. Policies survive
chunk unloading and server restarts. After a cable split, a detached network becomes
public once disconnection can be verified. If needed chunks are unloaded, protection
is retained until the topology can be checked.

Normal placement and recoloring cannot merge two Security Terminals into one network.
If another connection path creates a conflict, player access is denied until an extra
terminal is removed. Each owner can still open their own terminal's management screen.

Removing the Security Terminal ends its policy. Its dropped item is fresh and binds
to the player who next places it.

## Protection limits

This addon controls supported player access paths and cable parts. It is not a land
claim: standalone network blocks such as drives and controllers, removable storage
cells, external inventories, machine outputs, explosions, and other mods' tools are
outside its physical protection scope. Protect your base separately if you need those
covered.
