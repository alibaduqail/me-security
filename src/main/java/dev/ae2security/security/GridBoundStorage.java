package dev.ae2security.security;

import appeng.api.networking.IGrid;

/** Implemented on AE2's network inventory, not on arbitrary cell/local inventories. */
public interface GridBoundStorage {
    void ae2security$bind(IGrid grid);
    IGrid ae2security$grid();
}
