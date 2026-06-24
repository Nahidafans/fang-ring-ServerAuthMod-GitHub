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

public class AuthPacket {
    private final int type;
    private final int playerId;
    private final String message;

    public AuthPacket(int playerId) {
        this.type = 0;
        this.playerId = playerId;
        this.message = "";
    }

    public AuthPacket(boolean allowed, String message) {
        this.type = allowed ? 1 : 2;
        this.playerId = 0;
        this.message = message;
    }

    public AuthPacket(FriendlyByteBuf buf) {
        this.type = buf.readInt();
        this.playerId = buf.readInt();
        this.message = buf.readUtf(32767);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(type);
        buf.writeInt(playerId);
        buf.writeUtf(message);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (type == 0) {
                com.serverauth.server.ServerAuthHandler.handleAuthRequest(this, ctx.get());
            } else {
                com.serverauth.client.ClientAuthHandler.handleAuthResponse(this, ctx.get());
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public int getType() { return type; }
    public int getPlayerId() { return playerId; }
    public String getMessage() { return message; }
}
