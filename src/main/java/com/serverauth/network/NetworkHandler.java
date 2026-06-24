/*
 * ServerAuthMod - Minecraft Forge Server Authentication Mod
 * Copyright (C) 2024
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.serverauth.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Network handler for all mod packet communication
 */
public class NetworkHandler {
    private static final String PROTOCOL_VERSION = "2.0";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        new ResourceLocation("serverauth", "main"),
        () -> PROTOCOL_VERSION,
        PROTOCOL_VERSION::equals,
        PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    public static void register() {
        // Auth verification packet
        CHANNEL.registerMessage(
            packetId++,
            AuthPacket.class,
            AuthPacket::encode,
            AuthPacket::new,
            AuthPacket::handle
        );
        
        // Device fingerprint packet
        CHANNEL.registerMessage(
            packetId++,
            DeviceFingerprintPacket.class,
            DeviceFingerprintPacket::encode,
            DeviceFingerprintPacket::new,
            DeviceFingerprintPacket::handle
        );
    }
}
