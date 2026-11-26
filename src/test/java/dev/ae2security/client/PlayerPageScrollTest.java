package dev.ae2security.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.client.Point;
import net.minecraft.client.renderer.Rect2i;
import org.junit.jupiter.api.Test;

class PlayerPageScrollTest {
    private static final Rect2i LIST = new Rect2i(7, 43, 124, 151);
    private static final Rect2i TRACK = new Rect2i(133, 43, 9, 151);
    private static final Point OVER_LIST = new Point(60, 100);

    @Test
    void wheelMovesOneNotchPerPageWithinTheKnownRange() {
        var scroll = new PlayerPageScroll(TRACK, LIST);
        scroll.show(0, 2, false);

        assertTrue(scroll.mouseScrolled(OVER_LIST, -1));
        assertEquals(1, scroll.page());
        assertTrue(scroll.mouseScrolled(OVER_LIST, -1));
        assertTrue(scroll.mouseScrolled(OVER_LIST, -1));
        assertEquals(2, scroll.page(), "the last page is the end of the range");
        assertTrue(scroll.mouseScrolled(OVER_LIST, 1));
        assertEquals(1, scroll.page());
    }

    @Test
    void wheelOutsideTheListOrWithOnePageIsLeftToTheScreen() {
        var scroll = new PlayerPageScroll(TRACK, LIST);
        scroll.show(0, 2, false);
        assertFalse(scroll.mouseScrolled(new Point(200, 100), -1));

        scroll.show(0, 0, false);
        assertFalse(scroll.mouseScrolled(OVER_LIST, -1));
        assertEquals(0, scroll.page());
    }

    @Test
    void loadingKeepsTheLastKnownPageCount() {
        var scroll = new PlayerPageScroll(TRACK, LIST);
        scroll.show(0, 2, false);

        // Turning a page clears the rows and the total until the answer arrives.
        scroll.show(1, 0, true);
        assertEquals(1, scroll.page());
        assertTrue(scroll.mouseScrolled(OVER_LIST, -1));
        assertEquals(2, scroll.page());
    }

    @Test
    void showingAPageBeyondTheKnownCountNeverMovesTheHandleAway() {
        // A reopened or resized screen knows no page count yet, but its list is already on page 2.
        var scroll = new PlayerPageScroll(TRACK, LIST);
        scroll.show(2, 0, true);
        assertEquals(2, scroll.page());

        scroll.show(2, 4, false);
        assertEquals(2, scroll.page());
    }
}
