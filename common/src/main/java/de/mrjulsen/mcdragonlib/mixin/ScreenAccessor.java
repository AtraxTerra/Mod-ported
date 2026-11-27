package de.mrjulsen.mcdragonlib.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.gui.navigation.ScreenDirection;
import net.minecraft.client.gui.screens.Screen;

@Mixin(Screen.class)
public interface ScreenAccessor {
    
    // 26.2: the tab direction is a parameter (Screen.keyPressed passes !shift).
    @Invoker("createTabEvent")
    FocusNavigationEvent.TabNavigation dragonlib$createTabEvent(boolean forward);

    @Invoker("createArrowEvent")
    FocusNavigationEvent.ArrowNavigation dragonlib$createArrowEvent(ScreenDirection direction);

    @Invoker("clearFocus")
    void dragonlib$clearFocus();
}