package dev.ae2security.client;

import appeng.client.Point;
import appeng.client.gui.widgets.Scrollbar;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;

/**
 * AE2's scrollbar driving the server-side player pages: one notch per page. It lives outside
 * AE2's widget container, so the screen forwards mouse input in frame-relative coordinates and
 * asks {@link #page()} where the owner has moved it.
 */
final class PlayerPageScroll {
    private final Scrollbar scrollbar = new Scrollbar(Scrollbar.SMALL);
    /** Where the wheel pages: the list and its track. */
    private final Rect2i wheelArea;
    private int lastKnownPage;

    PlayerPageScroll(Rect2i track, Rect2i list) {
        scrollbar.setPosition(new Point(track.getX() + 1, track.getY() + 1));
        scrollbar.setSize(track.getWidth() - 2, track.getHeight() - 2);
        scrollbar.setCaptureMouseWheel(false);
        int right = Math.max(track.getX() + track.getWidth(), list.getX() + list.getWidth());
        int left = Math.min(track.getX(), list.getX());
        wheelArea = new Rect2i(left, list.getY(), right - left, list.getHeight());
    }

    /**
     * Shows the current page. While a page is loading its rows are cleared and the page count
     * is unknown, so the last known count is kept until the answer arrives. The range always
     * includes the page, so showing it never moves the handle somewhere the screen would then
     * read as the owner's request for another page.
     */
    void show(int page, int lastPage, boolean loading) {
        if (!loading) lastKnownPage = lastPage;
        scrollbar.setRange(0, Math.max(lastKnownPage, page), 1);
        scrollbar.setCurrentScroll(page);
    }

    /** The page the scrollbar points at. */
    int page() {
        return scrollbar.getCurrentScroll();
    }

    boolean mouseClicked(Point mouse, int button) {
        return mouse.isIn(scrollbar.getBounds()) && scrollbar.onMouseDown(mouse, button);
    }

    boolean mouseDragged(Point mouse, int button) {
        return scrollbar.onMouseDrag(mouse, button);
    }

    void mouseReleased(Point mouse, int button) {
        scrollbar.onMouseUp(mouse, button);
    }

    boolean mouseScrolled(Point mouse, double delta) {
        return delta != 0 && mouse.isIn(wheelArea) && scrollbar.onMouseWheel(mouse, delta);
    }

    /** Keeps paging while the mouse is held on the track, like any AE2 list. */
    void tick() {
        scrollbar.tick();
    }

    /** Draws the handle; call with the pose translated to the frame's top-left corner. */
    void draw(GuiGraphics graphics, Rect2i frame, Point mouse) {
        scrollbar.drawForegroundLayer(graphics, frame, mouse);
    }
}
