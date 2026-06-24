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
package com.serverauth.client;

import com.serverauth.network.AuthPacket;
import com.serverauth.network.DeviceFingerprintPacket;
import com.serverauth.network.NetworkHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.NetworkEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@OnlyIn(Dist.CLIENT)
public class ClientAuthHandler {
    private static final Logger LOGGER = LogManager.getLogger("ClientAuth");

    public static void handleAuthResponse(AuthPacket packet, NetworkEvent.Context ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (packet.getType() == 1) {
            LOGGER.info("Server auth passed: {}", packet.getMessage());
            mc.player.sendSystemMessage(Component.literal(
                ChatFormatting.GREEN + "[Auth] " + packet.getMessage()
            ));
        } else {
            LOGGER.warn("Server auth denied: {}", packet.getMessage());
            mc.player.sendSystemMessage(Component.literal(
                ChatFormatting.RED + "[Auth] " + packet.getMessage()
            ));
        }
    }

    @SubscribeEvent
    public void onClientJoinServer(ClientPlayerNetworkEvent.LoggingIn event) {
        new Thread(() -> {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ignored) {}
            
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            
            String fingerprint = ClientDeviceFingerprint.getOrCreateFingerprint();
            boolean deviceChanged = ClientDeviceFingerprint.hasDeviceChanged();
            
            String accountType = "UNKNOWN";
            try {
                if (mc.getUser() != null) {
                    accountType = mc.getUser().getType().name();
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to get account type: {}", e.getMessage());
            }
            
            LOGGER.info("Sending device fingerprint to server... Device changed: {}, Account: {}",
                deviceChanged, accountType);
            
            NetworkHandler.CHANNEL.sendToServer(
                new DeviceFingerprintPacket(0, fingerprint, accountType, deviceChanged)
            );
        }, "DeviceFingerprintSender").start();
    }
}
