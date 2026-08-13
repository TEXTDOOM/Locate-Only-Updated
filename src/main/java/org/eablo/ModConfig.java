package org.eablo;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class ModConfig {
    private static final Path CONFIG_PATH = Path.of("config/locateo.properties");
    private static final Properties properties = new Properties();
    private static boolean locateoEnabled = true;
    private static boolean locateonlyEnabled = true;
    private static boolean loEnabled = true;
    
    private static boolean originalLocateoEnabled = true;
    private static boolean originalLocateonlyEnabled = true;
    private static boolean originalLoEnabled = true;
    
    private static MinecraftServer serverInstance = null;

    public static void load() {
        if (Files.exists(CONFIG_PATH)) {
            try (InputStream in = Files.newInputStream(CONFIG_PATH)) {
                properties.load(in);
                locateoEnabled = Boolean.parseBoolean(properties.getProperty("locateo.enabled", "true"));
                locateonlyEnabled = Boolean.parseBoolean(properties.getProperty("locateonly.enabled", "true"));
                loEnabled = Boolean.parseBoolean(properties.getProperty("lo.enabled", "true"));
            } catch (IOException e) {
                LocateOnly.LOGGER.error("Failed to load config", e);
            }
        } else {
            save();
        }
        saveOriginalValues();
    }

    private static void saveOriginalValues() {
        originalLocateoEnabled = locateoEnabled;
        originalLocateonlyEnabled = locateonlyEnabled;
        originalLoEnabled = loEnabled;
    }

    public static void save() {
        properties.setProperty("locateo.enabled", String.valueOf(locateoEnabled));
        properties.setProperty("locateonly.enabled", String.valueOf(locateonlyEnabled));
        properties.setProperty("lo.enabled", String.valueOf(loEnabled));
        try (OutputStream out = Files.newOutputStream(CONFIG_PATH)) {
            properties.store(out, "LocateOnly Mod Configuration");
        } catch (IOException e) {
            LocateOnly.LOGGER.error("Failed to save config", e);
        }
    }

    public static boolean isCommandEnabled(String commandName) {
        return switch (commandName) {
            case "locateo" -> locateoEnabled;
            case "locateonly" -> locateonlyEnabled;
            case "lo" -> loEnabled;
            default -> true;
        };
    }

    public static void setCommandEnabled(String commandName, boolean enabled) {
        boolean currentValue = switch (commandName) {
            case "locateo" -> locateoEnabled;
            case "locateonly" -> locateonlyEnabled;
            case "lo" -> loEnabled;
            default -> true;
        };
        
        if (currentValue == enabled) {
            LocateOnly.LOGGER.info("Command {} is already {}", commandName, enabled);
            return;
        }
        
        switch (commandName) {
            case "locateo" -> locateoEnabled = enabled;
            case "locateonly" -> locateonlyEnabled = enabled;
            case "lo" -> loEnabled = enabled;
        }
        save();
        saveOriginalValues();
        reloadCommands();
    }

    public static void openGui(ServerPlayer player) {
        boolean initialLocateo = locateoEnabled;
        boolean initialLocateonly = locateonlyEnabled;
        boolean initialLo = loEnabled;
        
        Minecraft.getInstance().execute(() -> {
            ConfigBuilder builder = ConfigBuilder.create()
                    .setTitle(Component.translatable("locateo.config.title"))
                    .setSavingRunnable(() -> {
                        boolean changed = locateoEnabled != initialLocateo ||
                                          locateonlyEnabled != initialLocateonly ||
                                          loEnabled != initialLo;
                        
                        if (changed) {
                            save();
                            saveOriginalValues();
                            reloadCommands();
                            if (player != null) {
                                player.sendSystemMessage(Component.translatable("locateo.config.saved_and_reloaded"));
                            }
                        } else {
                            if (player != null) {
                                player.sendSystemMessage(Component.translatable("locateo.config.no_changes"));
                            }
                            LocateOnly.LOGGER.info("GUI config saved with no changes, reload skipped");
                        }
                    });

            ConfigCategory general = builder.getOrCreateCategory(Component.translatable("locateo.config.category.general"));
            ConfigEntryBuilder entryBuilder = builder.entryBuilder();

            general.addEntry(entryBuilder.startBooleanToggle(Component.translatable("locateo.config.command.locateo"), locateoEnabled)
                    .setDefaultValue(true)
                    .setSaveConsumer(newValue -> locateoEnabled = newValue)
                    .build());

            general.addEntry(entryBuilder.startBooleanToggle(Component.translatable("locateo.config.command.locateonly"), locateonlyEnabled)
                    .setDefaultValue(true)
                    .setSaveConsumer(newValue -> locateonlyEnabled = newValue)
                    .build());

            general.addEntry(entryBuilder.startBooleanToggle(Component.translatable("locateo.config.command.lo"), loEnabled)
                    .setDefaultValue(true)
                    .setSaveConsumer(newValue -> loEnabled = newValue)
                    .build());

            Minecraft.getInstance().setScreen(builder.build());
        });
    }

    // ✅ 静默执行 /reload，不输出到控制台
    private static void reloadCommands() {
        if (serverInstance == null) {
            serverInstance = LocateOnly.getServer();
        }
        
        if (serverInstance == null) {
            LocateOnly.LOGGER.warn("No server instance available, cannot reload");
            return;
        }
        
        LocateOnly.LOGGER.info("Configuration changed, reloading commands...");
        try {
            // ✅ 使用无输出的命令源（静默执行）
            var consoleSource = serverInstance.createCommandSourceStack()
                    .withSuppressedOutput();  // 🔑 关键：静默输出
            
            consoleSource.getServer().getCommands().performPrefixedCommand(consoleSource, "reload");
            LocateOnly.LOGGER.debug("Reload command executed silently");
        } catch (Exception e) {
            LocateOnly.LOGGER.error("Failed to reload: {}", e.getMessage());
        }
    }

    public static void setServerInstance(MinecraftServer server) {
        serverInstance = server;
        LocateOnly.LOGGER.info("Server instance cached for reload");
    }
}