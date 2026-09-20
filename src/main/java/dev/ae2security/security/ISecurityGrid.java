package dev.ae2security.security;

import java.util.Set;
import java.util.UUID;
import appeng.api.networking.IGridService;

public interface ISecurityGrid extends IGridService {
    AccessDecision check(UUID player, Permission permission, boolean ownerOnly);
    Set<UUID> terminalIds();
}
