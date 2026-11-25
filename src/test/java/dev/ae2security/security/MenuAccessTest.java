package dev.ae2security.security;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MenuAccessTest {
    @Test void ownerWinsOverEveryOtherClassification() {
        assertEquals(MenuAccess.OWNER, MenuAccess.classify(true, true, true, true, true));
    }

    @Test void hardwareWinsOverStorageAndCrafting() {
        assertEquals(MenuAccess.HARDWARE, MenuAccess.classify(false, true, true, true, true));
    }

    @Test void storageWinsOverCraftingScreens() {
        assertEquals(MenuAccess.STORAGE, MenuAccess.classify(false, false, true, true, true));
    }

    @Test void craftingRequestWinsOverStatus() {
        assertEquals(MenuAccess.CRAFTING_REQUEST, MenuAccess.classify(false, false, false, true, true));
        assertEquals(MenuAccess.STATUS, MenuAccess.classify(false, false, false, false, true));
    }

    @Test void unclassifiedMenusFallBackToBuild() {
        var fallback = MenuAccess.classify(false, false, false, false, false);
        assertEquals(MenuAccess.FALLBACK, fallback);
        assertTrue(fallback.needsBuild());
        assertTrue(MenuAccess.HARDWARE.needsBuild());
        for (var access : Set.of(MenuAccess.OWNER, MenuAccess.STORAGE, MenuAccess.CRAFTING_REQUEST, MenuAccess.STATUS)) {
            assertFalse(access.needsBuild(), access.name());
        }
    }

    @Test void shippedTagsAreDisjointAndOptionalForAe2wtlib() throws Exception {
        var seen = new HashSet<String>();
        int entries = 0;
        for (var name : new String[] {"owner_menus", "hardware_menus", "storage_menus"}) {
            var tag = read(name);
            assertFalse(tag.get("replace").getAsBoolean(), name + " must merge with pack additions");
            for (var value : tag.getAsJsonArray("values")) {
                String id;
                if (value.isJsonObject()) {
                    id = value.getAsJsonObject().get("id").getAsString();
                    assertFalse(value.getAsJsonObject().get("required").getAsBoolean(), id + " must be optional");
                } else {
                    id = value.getAsString();
                }
                assertTrue(id.startsWith("ae2:") || id.startsWith("ae2wtlib:"), id);
                // ae2 is a hard dependency; any other mod must be an optional entry or the whole tag fails to load.
                assertEquals(id.startsWith("ae2:"), value.isJsonPrimitive(), id + " optionality");
                assertTrue(seen.add(id), id + " is classified twice");
                entries++;
            }
        }
        // 36 of AE2's 40 menu types (the four crafting screens are matched in code) plus five ae2wtlib menus.
        assertEquals(41, entries);
        assertTrue(seen.containsAll(Set.of("ae2:pattern_provider", "ae2:patternterm", "ae2:patternaccessterminal",
                "ae2wtlib:wireless_pattern_encoding_terminal", "ae2wtlib:wireless_pattern_access_terminal")));
    }

    private static JsonObject read(String name) throws Exception {
        var path = "/data/ae2security/tags/menu/" + name + ".json";
        try (var stream = MenuAccessTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
