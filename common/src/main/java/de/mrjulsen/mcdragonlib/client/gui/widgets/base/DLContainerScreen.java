package de.mrjulsen.mcdragonlib.client.gui.widgets.base;

import java.nio.file.Path;
import java.util.List;

import de.mrjulsen.mcdragonlib.client.util.DLGuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

public class DLContainerScreen<M extends AbstractContainerMenu> extends AbstractContainerScreen<M> {

    private final DLWindowManager root;
    
    public <T extends DLWindow> DLContainerScreen(M menu, Inventory playerInventory, Component title, WindowBuilder<T> window) {
        super(menu, playerInventory, title);
        final Screen previousScreen = Minecraft.getInstance().gui.screen();
        this.root = new DLWindowManager(menu, window, width, height, (mgr) -> {
            Minecraft.getInstance().gui.setScreen(mgr.shouldShowPreviousScreenOnClose() ? previousScreen : null);
        });
    }

    public DLWindowManager getWindowManager() {
        return root;
    }

    @Override
    protected void init() {
        super.init();
        root.updateLayout(width, height);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 26.2 extracts the screen background (and renderBg, below) before this call.
        DLGuiGraphics graphics = new DLGuiGraphics(guiGraphics, guiGraphics.pose(), Minecraft.getInstance().font, partialTick);
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
        root.render(graphics, mouseX, mouseY);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
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

    @Override
    public final void extractBackground(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(guiGraphics, mouseX, mouseY, partialTick);
        DLGuiGraphics graphics = new DLGuiGraphics(guiGraphics, guiGraphics.pose(), Minecraft.getInstance().font, partialTick);
        renderBg(graphics, mouseX, mouseY);
    }

    protected void renderBg(DLGuiGraphics guiGraphics, int mouseX, int mouseY) {
    }
}
