package org.eablo;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LocateOnly implements ModInitializer {
    public static final String MOD_ID = "locateo";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    
    // ✅ 静态服务器实例
    private static MinecraftServer serverInstance = null;

    @Override
    public void onInitialize() {
        LOGGER.info("LocateOnly mod initializing for Minecraft 26.1...");
        ModConfig.load();

        // ✅ 服务器启动时缓存实例
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            serverInstance = server;
            ModConfig.setServerInstance(server);
            LOGGER.info("Server instance cached for reload");
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            LocateOnlyCommands.register(dispatcher);
        });

        LOGGER.info("LocateOnly mod initialization complete.");
    }
    
    // ✅ 供其他类获取服务器实例
    public static MinecraftServer getServer() {
        return serverInstance;
    }
}