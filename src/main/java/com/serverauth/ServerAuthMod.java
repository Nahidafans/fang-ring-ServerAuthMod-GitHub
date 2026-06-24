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
package com.serverauth;

import com.serverauth.client.ClientAuthHandler;
import com.serverauth.config.AuthConfig;
import com.serverauth.network.NetworkHandler;
import com.serverauth.server.ServerAuthHandler;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * ServerAuthMod - Main class
 * 
 * Features:
 * 1. Each player gets a unique digital ID on first join
 * 2. Server maintains whitelist/blacklist by ID
 * 3. Blacklisted IDs are rejected from connecting
 * 4. Client device fingerprinting (hardware + OS bound)
 * 5. Premium/offline account detection
 * 6. Device change tracking and admin alerts
 */
@Mod(ServerAuthMod.MODID)
public class ServerAuthMod {
    public static final String MODID = "serverauth";
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    public ServerAuthMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::commonSetup);

        // Register server-side handler
        MinecraftForge.EVENT_BUS.register(new ServerAuthHandler());
        
        // Register client-side handler (only on client)
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            MinecraftForge.EVENT_BUS.register(new ClientAuthHandler());
        });
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        NetworkHandler.register();
        AuthConfig.load();
        LOGGER.info("ServerAuthMod loaded!");
        LOGGER.info("Whitelist: {}, Blacklist: {}",
                AuthConfig.getWhitelist().size(), AuthConfig.getBlacklist().size());
    }
}
