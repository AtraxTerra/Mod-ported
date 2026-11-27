// Adapted from chaevsfe / Create Fly: Steam 'n' Rails (https://github.com/chaevsfe/Create-Steam-n-Rails-Fly)
// Based on original work from Create: Steam 'n' Rails by The Railways Team (https://github.com/Layers-of-Railways/Railway)
// Licensed under LGPL-3.0-only — extracted/adapted with AI assistance.
package com.railwayteam.railways.content.minecarts;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.JukeboxSong;

/**
 * Port: the client half of {@link MinecartJukebox} (its record's sound), moved out of the entity
 * class unchanged so a dedicated server never has to verify client types there. Only called on the
 * client.
 */
final class MinecartJukeboxClient {
    private MinecartJukeboxClient() {
    }

    static void updateSound(MinecartJukebox cart) {
        JukeboxCartSoundInstance sound = cart.sound instanceof JukeboxCartSoundInstance playing ? playing : null;
        if (cart.getDisc().isEmpty()) {
            if (sound != null)
                sound.requestStop();
            return;
        }

        if (sound != null && !sound.isStopped())
            sound.requestStop();

        JukeboxSong.fromStack(cart.getDisc())
            .map(Holder::value)
            .ifPresent(song -> startPlaying(cart, song));
    }

    private static void startPlaying(MinecartJukebox cart, JukeboxSong song) {
        JukeboxCartSoundInstance sound = new JukeboxCartSoundInstance(cart, song);
        cart.sound = sound;
        Minecraft.getInstance().getSoundManager().play(sound);
    }

    static class JukeboxCartSoundInstance extends AbstractTickableSoundInstance {
        private final MinecartJukebox cart;

        JukeboxCartSoundInstance(MinecartJukebox cart, JukeboxSong song) {
            super(song.soundEvent().value(), SoundSource.RECORDS, SoundInstance.createUnseededRandom());
            this.cart = cart;
            this.x = cart.getX();
            this.y = cart.getY();
            this.z = cart.getZ();
        }

        @Override
        public void tick() {
            if (cart.isRemoved())
                requestStop();

            this.x = cart.getX();
            this.y = cart.getY();
            this.z = cart.getZ();
        }

        public void requestStop() {
            stop();
        }
    }
}
