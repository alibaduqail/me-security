# ME Security 1.1.0-dev.2

The Security Terminal now protects ordinary AE2 network hardware. The unreleased Secure Drive and PIN system have been removed.

- Build controls native device placement, mining, wrench removal, rotation, configuration, upgrades, and manual disk management. Grant it only to players trusted with removable cells and their contents.
- Protected devices and the terminal's whole cable-bus block survive explosions.
- Disks in drives, ME Chests, ME IO Ports, and Spatial IO Ports are managed manually. Normal network item/fluid automation and internal IO Port processing continue.
- Block and part menus validate permissions, live host identity, and an eight-block range on server actions.
- Craft-only players retain collection of their own completed final products, limited by item/fluid and quantity; allowances survive restarts.
- Existing player search, policy acknowledgements, and ownership-transfer UI improvements remain.

Minecraft 1.21.1, Java 21, NeoForge 21.1.250, AE2 19.2.17, GuideME 21.1.1, and optional AE2 Wireless Terminals 19.5.1 remain pinned. Install the same candidate on clients and server: payload protocol is 5. Existing terminal policies and crafting allowances are retained. Secure Drive development worlds require prior manual recovery; this version provides no Secure Drive migration.

Coverage is limited to native AE2 network hardware. External inventories, machine outputs, arbitrary third-party movers, and administrative commands are outside the protection contract.
