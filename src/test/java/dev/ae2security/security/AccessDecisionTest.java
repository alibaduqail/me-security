package dev.ae2security.security;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.ae2security.security.SecurityPolicyTest.*;

class AccessDecisionTest {
    @Test void anUnsecuredNetworkIsPublic() {
        assertEquals(AccessDecision.ALLOW, AccessDecision.evaluate(List.of(), false, OUTSIDER, Permission.EXTRACT, false));
    }
    @Test void missingPolicyDataNeverBecomesPublic() {
        assertEquals(AccessDecision.UNRESOLVED, AccessDecision.evaluate(List.of(), true, OWNER, Permission.VIEW, false));
    }
    @Test void twoTerminalsConflictEvenWithTheSameOwner() {
        assertEquals(AccessDecision.CONFLICT, AccessDecision.evaluate(List.of(SecurityPolicy.create(OWNER), SecurityPolicy.create(OWNER)),
                false, OWNER, Permission.VIEW, false));
    }
    @Test void trustedPlayersCannotManagePatternsOrSettings() {
        var policy = SecurityPolicy.create(OWNER).trust(OWNER, TRUSTED, Permission.ALL);
        assertEquals(AccessDecision.DENIED, AccessDecision.evaluate(List.of(policy), false, TRUSTED, Permission.VIEW, true));
        assertEquals(AccessDecision.ALLOW, AccessDecision.evaluate(List.of(policy), false, OWNER, Permission.VIEW, true));
    }
}
