package dev.ae2security.security;

import java.util.Collection;
import java.util.UUID;

/** A missing policy is distinct from a public network: missing data must fail closed. */
public enum AccessDecision {
    ALLOW, DENIED, CONFLICT, UNRESOLVED;

    public static AccessDecision evaluate(Collection<SecurityPolicy> policies, boolean unresolved,
            UUID player, Permission permission, boolean ownerOnly) {
        if (unresolved) return UNRESOLVED;
        if (policies.size() > 1) return CONFLICT;
        if (policies.isEmpty()) return ALLOW;
        var policy = policies.iterator().next();
        return (ownerOnly ? policy.isOwner(player) : policy.allows(player, permission)) ? ALLOW : DENIED;
    }

    public String translationKey() { return "message.ae2security." + name().toLowerCase(java.util.Locale.ROOT); }
}
