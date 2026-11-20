package net.minecraft.client.gui.layouts;

import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Kiln's stand-in for {@code HeaderAndFooterLayout}: a screen in three bands, the header along the top, the footer
 * along the bottom and the contents between, each band's children centred in it.
 */
public class HeaderAndFooterLayout implements Layout {
    public static final int MAGIC_PADDING = 4;
    public static final int DEFAULT_HEADER_AND_FOOTER_HEIGHT = 33;
    public final FrameLayout headerFrame = new FrameLayout();
    public final FrameLayout footerFrame = new FrameLayout();
    public final FrameLayout contentsFrame = new FrameLayout();
    private final Screen screen;
    private int headerHeight;
    private int footerHeight;

    public HeaderAndFooterLayout(Screen screen) {
        this(screen, DEFAULT_HEADER_AND_FOOTER_HEIGHT);
    }

    public HeaderAndFooterLayout(Screen screen, int height) {
        this(screen, height, height);
    }

    public HeaderAndFooterLayout(Screen screen, int headerHeight, int footerHeight) {
        this.screen = screen;
        this.headerHeight = headerHeight;
        this.footerHeight = footerHeight;
        headerFrame.defaultChildLayoutSetting().align(0.5F, 0.5F);
        footerFrame.defaultChildLayoutSetting().align(0.5F, 0.5F);
    }

    @Override
    public void setX(int x) {}

    @Override
    public void setY(int y) {}

    @Override
    public int getX() {
        return 0;
    }

    @Override
    public int getY() {
        return 0;
    }

    @Override
    public int getWidth() {
        return screen.width;
    }

    @Override
    public int getHeight() {
        return screen.height;
    }

    public int getFooterHeight() {
        return footerHeight;
    }

    public void setFooterHeight(int height) {
        this.footerHeight = height;
    }

    public void setHeaderHeight(int height) {
        this.headerHeight = height;
    }

    public int getHeaderHeight() {
        return headerHeight;
    }

    public int getContentHeight() {
        return screen.height - headerHeight - footerHeight;
    }

    @Override
    public void visitChildren(Consumer<LayoutElement> visitor) {
        headerFrame.visitChildren(visitor);
        contentsFrame.visitChildren(visitor);
        footerFrame.visitChildren(visitor);
    }

    @Override
    public void removeChildren() {
        headerFrame.removeChildren();
        contentsFrame.removeChildren();
        footerFrame.removeChildren();
    }

    @Override
    public void arrangeElements() {
        int width = screen.width;
        headerFrame.setMinWidth(width).setMinHeight(headerHeight);
        headerFrame.setPosition(0, 0);
        headerFrame.arrangeElements();
        footerFrame.setMinWidth(width).setMinHeight(footerHeight);
        footerFrame.arrangeElements();
        footerFrame.setY(screen.height - footerHeight);
        contentsFrame.setMinWidth(width);
        contentsFrame.arrangeElements();
        // The contents in the middle of the room between header and footer, never over the header.
        contentsFrame.setY(headerHeight + Math.max(0, (getContentHeight() - contentsFrame.getHeight()) / 2));
    }

    public <T extends LayoutElement> T addToHeader(T child) {
        return headerFrame.addChild(child);
    }

    public <T extends LayoutElement> T addToHeader(T child, Consumer<LayoutSettings> settings) {
        return headerFrame.addChild(child, settings);
    }

    /** The screen's title in the header, centred. */
    public void addTitleHeader(Component title, Font font) {
        headerFrame.addChild(new StringWidget(title, font));
    }

    public <T extends LayoutElement> T addToFooter(T child) {
        return footerFrame.addChild(child);
    }

    public <T extends LayoutElement> T addToFooter(T child, Consumer<LayoutSettings> settings) {
        return footerFrame.addChild(child, settings);
    }

    public <T extends LayoutElement> T addToContents(T child) {
        return contentsFrame.addChild(child);
    }

    public <T extends LayoutElement> T addToContents(T child, Consumer<LayoutSettings> settings) {
        return contentsFrame.addChild(child, settings);
    }
}
