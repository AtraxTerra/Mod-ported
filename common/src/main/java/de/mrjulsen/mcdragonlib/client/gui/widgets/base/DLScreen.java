package de.mrjulsen.mcdragonlib.client.gui.widgets.base;

import java.nio.file.Path;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import de.mrjulsen.mcdragonlib.client.util.DLGuiGraphics;
import de.mrjulsen.mcdragonlib.util.TextUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.world.inventory.AbstractContainerMenu;

public class DLScreen<M extends AbstractContainerMenu> extends Screen {

    private final DLWindowManager root;

    @Deprecated(forRemoval = true)
    public <T extends DLWindow> DLScreen(@Nullable M menu, WindowBuilder<T> window) {
        super(TextUtils.empty());
        final Screen previousScreen = Minecraft.getInstance().gui.screen();
        this.root = new DLWindowManager(null, window, width, height, (mgr) -> {
            Minecraft.getInstance().gui.setScreen(mgr.shouldShowPreviousScreenOnClose() ? previousScreen : null);
        });
    }

    public <T extends DLWindow> DLScreen(WindowBuilder<T> window) {
        super(TextUtils.empty());
        final Screen previousScreen = Minecraft.getInstance().gui.screen();
        this.root = new DLWindowManager(null, window, width, height, (mgr) -> {
            Minecraft.getInstance().gui.setScreen(mgr.shouldShowPreviousScreenOnClose() ? previousScreen : null);
        });
    }

    public DLWindowManager getWindowManager() {
        return root;
    }

    @Deprecated(forRemoval = true)
    public boolean supportsMenus() {
        return false;
    }

    @Override
    protected void init() {
        super.init();
        root.updateLayout(width, height);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 26.2 extracts the screen background in its own stratum before this call, so the explicit
        // renderBackground of 1.21 is gone (a second blur in one frame is an error now).
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
        DLGuiGraphics graphics = new DLGuiGraphics(guiGraphics, guiGraphics.pose(), Minecraft.getInstance().font, partialTick);
        root.render(graphics, mouseX, mouseY);
    }

    @Override
    public void tick() {
        super.tick();
        root.tick();
    }

    // NeoForge 26.2 port: input arrives as event records. DragonLib's window manager keeps its 1.21
    // signatures, so the events are unpacked here.
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return root.mouseClicked(event.x(), event.y(), event.button()) || super.mouseClicked(event, doubleClick);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        boolean b = root.mouseMoved(mouseX, mouseY);
        if (!b) {
            super.mouseMoved(mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return root.mouseReleased(event.x(), event.y(), event.button()) || super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return root.mouseScrolled(mouseX, mouseY, scrollX, scrollY) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        return root.mouseDragged(event.x(), event.y(), event.button(), dragX, dragY) || super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return root.keyPressed(event.key(), event.scancode(), event.modifiers()) || super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        return root.keyReleased(event.key(), event.scancode(), event.modifiers()) || super.keyReleased(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        // 1.21 delivered UTF-16 units (a surrogate pair as two calls) with the modifiers; 26.2 sends the
        // code point alone.
        boolean handled = false;
        for (char c : Character.toChars(event.codepoint())) {
            handled |= root.charTyped(c, 0);
        }
        return handled || super.charTyped(event);
    }

    @Override
    public void onFilesDrop(List<Path> paths) {
        boolean b = root.onFilesDrop(paths);
        if (!b) {
            super.onFilesDrop(paths);
        }
    }

    @Override
    public void onClose() {
        root.onClose();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return root.isPauseScreen();
    }

    @Deprecated(forRemoval = true)
    public M getMenu() {
        return null;
    }
}
