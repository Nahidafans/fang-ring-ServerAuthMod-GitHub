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

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class DeviceFingerprintPacket {
    private final int playerId;
    private final String deviceFingerprint;
    private final String accountType;
    private final boolean deviceChanged;

    public DeviceFingerprintPacket(int playerId, String deviceFingerprint, 
                                    String accountType, boolean deviceChanged) {
        this.playerId = playerId;
        this.deviceFingerprint = deviceFingerprint;
        this.accountType = accountType;
        this.deviceChanged = deviceChanged;
    }

    public DeviceFingerprintPacket(FriendlyByteBuf buf) {
        this.playerId = buf.readInt();
        this.deviceFingerprint = buf.readUtf(256);
        this.accountType = buf.readUtf(32);
        this.deviceChanged = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(playerId);
        buf.writeUtf(deviceFingerprint);
        buf.writeUtf(accountType);
        buf.writeBoolean(deviceChanged);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            com.serverauth.server.ServerAuthHandler.handleDeviceFingerprint(this, ctx.get());
        });
        ctx.get().setPacketHandled(true);
    }

    public int getPlayerId() { return playerId; }
    public String getDeviceFingerprint() { return deviceFingerprint; }
    public String getAccountType() { return accountType; }
    public boolean isDeviceChanged() { return deviceChanged; }
}
