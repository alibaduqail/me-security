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
| Craft | Request autocrafting, pause or cancel jobs, and collect your own completed final products up to the requested quantity. |
| Build | Place, remove, recolor, and configure native AE2 network hardware, including physical storage cells. |

Trust always includes viewing access. Build does not grant terminal Insert, Extract, or Craft,
but it grants full hardware trust: a builder can remove storage cells and access their contents.
Pattern management and changing security settings remain owner-only.

Craft-only collection is available once your entire autocrafting job finishes
successfully. Withdraw the final item or fluid from storage as usual. You can take
up to the requested amount; collecting it spends that allowance. Allowances survive
server restarts and apply only to your own jobs on this protected network, while
you still have Craft permission. Canceled jobs, intermediate ingredients, and
byproducts do not grant an allowance. Jobs started before this feature was
installed have no recorded allowance. Manual crafting still needs Extract for
network ingredients.

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

Normal item and fluid automation continues. Physical disks in ME Drives, ME Chests,
ME IO Ports, and Spatial IO Ports must be inserted or removed manually with Build
permission. Hoppers and buses cannot move those disks on protected networks. IO Ports
still process disks placed in them manually. Autocrafting jobs already accepted by a CPU
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

Operators can recover a network whose owner is gone with `/mesecurity`: `info` shows a
terminal's owner and trusted players, `setowner` hands it to another player, `retire`
ends its policy, and `bypass` toggles a personal admin bypass. A terminal placed with no
owning player protects nothing.

## Hardware and explosion protection

Ordinary drives, controllers, crafting CPUs, quantum bridges, spatial devices, cables,
and cable parts on the network require Build permission for mining, wrench removal,
rotation, configuration, and upgrades. Device menus recheck permissions on every action
and close when their host is replaced or the player moves out of reach.

Protected network hardware and the Security Terminal's whole cable-bus block survive
explosions. Public devices remain destructible. Protection follows network membership;
it does not depend on power or channels.

## Protection limits

Coverage applies to native AE2 network hardware. Arbitrary third-party tools or movers,
world-editing commands such as `/setblock`, external inventories, and machine outputs
remain outside its protection scope.
