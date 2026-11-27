---
navigation:
  parent: ae2:items-blocks-machines/items-blocks-machines-index.md
  title: Intrusion Alert Card
  icon: ae2security:intrusion_alert_card
  position: 411
categories:
- tools
item_ids:
- ae2security:intrusion_alert_card
---

# Intrusion Alert Card

<ItemImage id="ae2security:intrusion_alert_card" scale="2" float="left" />

An upgrade card for a [wireless terminal](ae2:items-blocks-machines/wireless_terminals.md).
With it installed, the terminal tells you when a player you have not trusted is denied on
the network it is linked to. That covers trying to open a terminal or device, taking
items, building, breaking something, or tampering with the
[Security Terminal](security_terminal.md).

<br clear="all" />

## Recipe

<Recipe id="ae2security:intrusion_alert_card" />

## How it works

Put the card in the upgrade slots of a wireless terminal that is linked to a wireless
access point on your network. One card per terminal. It works in AE2's wireless and
wireless crafting terminals, and in AE2 Wireless Terminals' terminals if that mod is installed.

While the terminal is anywhere in your inventory or offhand, every intrusion shows up
above your hotbar, naming the player, what they were denied, and where the network's
Security Terminal is, with a short ping. Range does not matter, and neither does the
dimension: you hear about it wherever you are. The terminal needs no power for this.

Only the network's owner and trusted players get alerts. A trusted player who only lacks
one permission is never reported. You are told about each intruder at most once a minute,
and alerts are not saved: if you are offline or not carrying the terminal, you miss them.
Every report is also written to the server log.

Take the card out to stop the alerts. For a redstone signal instead, use a
[Security Emitter](security_emitter.md).
