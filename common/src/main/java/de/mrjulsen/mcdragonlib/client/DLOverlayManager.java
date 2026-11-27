package de.mrjulsen.mcdragonlib.client;

import java.util.Optional;

import de.mrjulsen.mcdragonlib.client.gui.widgets.base.DLWindow;
import de.mrjulsen.mcdragonlib.client.gui.widgets.base.DLWindowManager;
import de.mrjulsen.mcdragonlib.client.gui.widgets.base.WindowBuilder;
import de.mrjulsen.mcdragonlib.client.util.DLGuiGraphics;
import de.mrjulsen.mcdragonlib.client.util.GuiUtils;
import dev.architectury.event.EventResult;
import dev.architectury.event.events.client.ClientScreenInputEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import net.minecraft.client.Minecraft;

public final class DLOverlayManager {
    private DLOverlayManager() {}

    private static DLWindowManager root;

    public static final boolean initialized() {
        return root != null;
    }

    public static void init() {        
        ClientTickEvent.CLIENT_POST.register((mc) -> {
            if (!initialized()) return;
            root.tick();
        });

        dev.architectury.event.events.client.ClientGuiEvent.INIT_POST.register((guiGraphics, screen) -> {
            if (!initialized()) return;
            root.updateLayout((int)GuiUtils.getScreenWidth(), (int)GuiUtils.getScreenHeight());
        });        

        dev.architectury.event.events.client.ClientPlayerEvent.CLIENT_PLAYER_JOIN.register((mc) -> {  
            if (!initialized()) return;
            root.close();
        });

        dev.architectury.event.events.client.ClientGuiEvent.RENDER_HUD.register((guiGraphics, deltaTracker) -> {
            if (!initialized()) return;
            DLGuiGraphics graphics = new DLGuiGraphics(guiGraphics, guiGraphics.pose(), Minecraft.getInstance().font, deltaTracker.getGameTimeDeltaPartialTick(true));
            root.render(graphics, (int)GuiUtils.mouseXOnScreen(), (int)GuiUtils.mouseYOnScreen());
        });

        // NeoForge 26.2 port: Architectury hands over the 26.2 input events; the window manager keeps its
        // 1.21 signatures, so they are unpacked here (a code point beyond the BMP as two UTF-16 units).
        dev.architectury.event.events.client.ClientScreenInputEvent.CHAR_TYPED_POST.register((mc, screen, event) -> {
            if (!initialized()) return EventResult.pass();
            boolean result = false;
            for (char c : Character.toChars(event.codepoint())) {
                result |= root.charTyped(c, 0);
            }
            return result ? EventResult.interruptTrue() : EventResult.pass();
        });
        dev.architectury.event.events.client.ClientScreenInputEvent.KEY_PRESSED_POST.register((mc, screen, event) -> {
            if (!initialized()) return EventResult.pass();
            boolean result = root.keyPressed(event.key(), event.scancode(), event.modifiers());
            return result ? EventResult.interruptTrue() : EventResult.pass();
        });
        dev.architectury.event.events.client.ClientScreenInputEvent.KEY_RELEASED_POST.register((mc, screen, event) -> {
            if (!initialized()) return EventResult.pass();
            boolean result = root.keyReleased(event.key(), event.scancode(), event.modifiers());
            return result ? EventResult.interruptTrue() : EventResult.pass();
        });
        dev.architectury.event.events.client.ClientScreenInputEvent.MOUSE_CLICKED_POST.register((mc, screen, event, doubleClick) -> {
            if (!initialized()) return EventResult.pass();
            boolean result = root.mouseClicked(event.x(), event.y(), event.button());
            return result ? EventResult.interruptTrue() : EventResult.pass();
        });
        dev.architectury.event.events.client.ClientScreenInputEvent.MOUSE_DRAGGED_POST.register((mc, screen, event, dragX, dragY) -> {
            if (!initialized()) return EventResult.pass();
            boolean result = root.mouseDragged(event.x(), event.y(), event.button(), dragX, dragY);
            return result ? EventResult.interruptTrue() : EventResult.pass();
        });
        ClientScreenInputEvent.MOUSE_SCROLLED_POST.register((mc, screen, mouseX, mouseY, scrollX, scrollY) -> {
            if (!initialized()) return EventResult.pass();
            boolean result = root.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
            return result ? EventResult.interruptTrue() : EventResult.pass();
        });
        dev.architectury.event.events.client.ClientScreenInputEvent.MOUSE_RELEASED_PRE.register((mc, screen, event) -> {
            if (!initialized()) return EventResult.pass();
            boolean result = root.mouseReleased(event.x(), event.y(), event.button());
            return result ? EventResult.interruptTrue() : EventResult.pass();
        });
        dev.architectury.event.events.client.ClientPlayerEvent.CLIENT_PLAYER_QUIT.register((mc) -> {
            if (!initialized()) return;
            root.close();
        });
    }

    public static void mouseMoved(double mouseX, double mouseY) {
        if (!initialized()) return;
        root.mouseMoved(mouseX, mouseY);
    }

    public static boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!initialized()) return false;
        return root.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    public static void resizeDisplay() {
        if (!initialized()) return;
        root.updateLayout((int)GuiUtils.getScreenWidth(), (int)GuiUtils.getScreenHeight());
    }

    public static <T extends DLWindow> void addOverlay(WindowBuilder<T> builder) {
        if (Minecraft.getInstance().level == null || Minecraft.getInstance().player == null) {
            throw new IllegalStateException("Player must be in game to use overlays.");
        }
        if (!initialized()) {
            root = new DLWindowManager(null, builder, GuiUtils.getScreenWidth(), GuiUtils.getScreenHeight(), (mgr) -> root = null);
            root.updateLayout((int)GuiUtils.getScreenWidth(), (int)GuiUtils.getScreenHeight());
        } else {
            root.createWindow(builder);
        }
    } 
    
    public static Optional<DLWindowManager> getWindowManager() {
        return Optional.ofNullable(root);
    }
}

