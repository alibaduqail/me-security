package dev.ae2security.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.UUID;

import dev.ae2security.integration.SecurityTerminalTooltip.Shown;
import dev.ae2security.integration.SecurityTerminalTooltip.Status;
import dev.ae2security.security.AccessDecision;
import dev.ae2security.security.Permission;
import dev.ae2security.security.SecurityPolicy;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

class SecurityTerminalTooltipTest {
    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID TRUSTED = UUID.randomUUID();
    private static final UUID STRANGER = UUID.randomUUID();
    private static final SecurityPolicy POLICY = SecurityPolicy.create(OWNER)
            .trust(OWNER, TRUSTED, Permission.EXTRACT.bit() | Permission.CRAFT.bit());

    private static Shown evaluate(UUID player, AccessDecision decision) {
        return SecurityTerminalTooltip.evaluate(POLICY, false, decision, player, false, "Ali");
    }

    @Test void strangersSeeTheOwnerAndNoAccess() {
        assertEquals(new Shown(Status.NONE, "Ali", 0), evaluate(STRANGER, AccessDecision.DENIED));
    }

    @Test void trustedPlayersSeeTheirOwnPermissions() {
        var shown = evaluate(TRUSTED, AccessDecision.ALLOW);
        assertEquals(Status.TRUSTED, shown.status());
        assertEquals(Permission.VIEW.bit() | Permission.EXTRACT.bit() | Permission.CRAFT.bit(), shown.access());
    }

    @Test void theOwnerSeesOwnerAccess() {
        assertEquals(Status.OWNER, evaluate(OWNER, AccessDecision.ALLOW).status());
    }

    @Test void aConflictBlocksEvenTheOwner() {
        assertEquals(new Shown(Status.CONFLICT, "Ali", 0), evaluate(OWNER, AccessDecision.CONFLICT));
    }

    @Test void anUnresolvedNetworkIsNeverShownAsAccess() {
        assertEquals(Status.UNRESOLVED, evaluate(TRUSTED, AccessDecision.UNRESOLVED).status());
    }

    @Test void aDisconnectedTerminalFallsBackToItsPolicy() {
        assertEquals(Status.TRUSTED, evaluate(TRUSTED, null).status());
    }

    @Test void aBypassingOperatorIsToldSo() {
        var shown = SecurityTerminalTooltip.evaluate(POLICY, false, AccessDecision.DENIED, STRANGER, true, "Ali");
        assertEquals(Status.BYPASS, shown.status());
    }

    @Test void aTerminalWithoutPolicyHasNoOwnerLine() {
        assertEquals(new Shown(Status.RETIRED, null, 0),
                SecurityTerminalTooltip.evaluate(null, true, null, STRANGER, false, null));
        assertEquals(new Shown(Status.UNRESOLVED, null, 0),
                SecurityTerminalTooltip.evaluate(null, false, null, STRANGER, false, null));
        assertEquals(List.of("tooltip.ae2security.retired"),
                keys(SecurityTerminalTooltip.lines(new Shown(Status.RETIRED, null, 0))));
    }

    @Test void dataSurvivesTheTripToTheClient() {
        var shown = evaluate(TRUSTED, AccessDecision.ALLOW);
        assertEquals(shown, Shown.fromTag(shown.toTag()));
    }

    @Test void unknownOrMissingDataShowsNothing() {
        assertNull(Shown.fromTag(new CompoundTag()));
        var tag = new CompoundTag();
        tag.putString("status", "SOMETHING_NEWER");
        assertNull(Shown.fromTag(tag));
    }

    @Test void linesNameTheOwnerThenTheAccess() {
        var lines = SecurityTerminalTooltip.lines(evaluate(TRUSTED, AccessDecision.ALLOW));
        assertEquals(List.of("tooltip.ae2security.owner", "tooltip.ae2security.access"), keys(lines));
        var owner = (TranslatableContents) lines.get(0).getContents();
        assertEquals("Ali", owner.getArgs()[0]);
        var permissions = ((Component) ((TranslatableContents) lines.get(1).getContents()).getArgs()[0]).getString();
        // Without a client language loaded, each permission renders as its translation key, in screen order.
        assertTrue(permissions.indexOf("permission.ae2security.view") < permissions.indexOf("permission.ae2security.extract"));
        assertTrue(permissions.indexOf("permission.ae2security.extract") < permissions.indexOf("permission.ae2security.craft"));
        assertFalse(permissions.contains("permission.ae2security.build"));
    }

    private static List<String> keys(List<Component> lines) {
        return lines.stream().map(line -> ((TranslatableContents) line.getContents()).getKey()).toList();
    }
}
