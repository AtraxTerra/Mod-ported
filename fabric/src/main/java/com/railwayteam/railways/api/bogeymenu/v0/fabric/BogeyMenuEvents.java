/*
 * Steam 'n' Rails
 * Copyright (c) 2022-2024 The Railways Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.railwayteam.railways.api.bogeymenu.v0.fabric;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

// Port: the same public API without Fabric API's event factory: listeners register on
// ENTRY_REGISTRATION and invoker() calls each of them in turn.
public class BogeyMenuEvents {
    public static final ListenerEvent ENTRY_REGISTRATION = new ListenerEvent();

    @FunctionalInterface
    public interface EntryRegistrationEvent {
        void onReadyForRegistration();
    }

    public static final class ListenerEvent {
        private final List<EntryRegistrationEvent> listeners = new CopyOnWriteArrayList<>();

        private ListenerEvent() {
        }

        public void register(EntryRegistrationEvent listener) {
            listeners.add(listener);
        }

        public EntryRegistrationEvent invoker() {
            return () -> listeners.forEach(EntryRegistrationEvent::onReadyForRegistration);
        }
    }
}
