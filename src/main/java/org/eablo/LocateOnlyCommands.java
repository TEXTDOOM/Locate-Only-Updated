package org.eablo;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static net.minecraft.commands.Commands.literal;

public class LocateOnlyCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(buildCommand("locateo"));
        dispatcher.register(buildCommand("locateonly"));
        dispatcher.register(buildCommand("lo"));
        registerConfigCommand(dispatcher);

        LocateOnly.LOGGER.info("Commands registered: /locateo, /locateonly, /lo, /configlocateo");
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildCommand(String name) {
        return literal(name)
                .requires(source -> ModConfig.isCommandEnabled(name))
                .then(literal("structure")
                        // ✅ 使用 greedyString() 允许包含 : 字符
                        .then(Commands.argument("id", StringArgumentType.greedyString())
                                .suggests(structureSuggestions())
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    String id = StringArgumentType.getString(context, "id");
                                    return executeLocate(source, "structure", id);
                                })
                        )
                )
                .then(literal("biome")
                        .then(Commands.argument("id", StringArgumentType.greedyString())
                                .suggests(biomeSuggestions())
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    String id = StringArgumentType.getString(context, "id");
                                    return executeLocate(source, "biome", id);
                                })
                        )
                )
                .then(literal("poi")
                        .then(Commands.argument("id", StringArgumentType.greedyString())
                                .suggests(poiSuggestions())
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    String id = StringArgumentType.getString(context, "id");
                                    return executeLocate(source, "poi", id);
                                })
                        )
                );
    }

    private static SuggestionProvider<CommandSourceStack> structureSuggestions() {
        return (context, builder) -> {
            var registry = context.getSource().getServer().registryAccess().lookupOrThrow(Registries.STRUCTURE);
            String remaining = builder.getRemaining().trim();
            for (var entry : registry.entrySet()) {
                String fullId = entry.getKey().identifier().toString();
                String shortId = fullId.startsWith("minecraft:") ? fullId.substring(10) : fullId;
                // ✅ 匹配完整 ID 或短名称，补全显示完整 ID
                if (remaining.isEmpty() || fullId.startsWith(remaining) || shortId.startsWith(remaining)) {
                    builder.suggest(fullId);
                }
            }
            return builder.buildFuture();
        };
    }

    private static SuggestionProvider<CommandSourceStack> biomeSuggestions() {
        return (context, builder) -> {
            var registry = context.getSource().getServer().registryAccess().lookupOrThrow(Registries.BIOME);
            String remaining = builder.getRemaining().trim();
            for (var entry : registry.entrySet()) {
                String fullId = entry.getKey().identifier().toString();
                String shortId = fullId.startsWith("minecraft:") ? fullId.substring(10) : fullId;
                if (remaining.isEmpty() || fullId.startsWith(remaining) || shortId.startsWith(remaining)) {
                    builder.suggest(fullId);
                }
            }
            return builder.buildFuture();
        };
    }

    private static SuggestionProvider<CommandSourceStack> poiSuggestions() {
        return (context, builder) -> {
            var registry = context.getSource().getServer().registryAccess().lookupOrThrow(Registries.POINT_OF_INTEREST_TYPE);
            String remaining = builder.getRemaining().trim();
            for (var entry : registry.entrySet()) {
                String fullId = entry.getKey().identifier().toString();
                String shortId = fullId.startsWith("minecraft:") ? fullId.substring(10) : fullId;
                if (remaining.isEmpty() || fullId.startsWith(remaining) || shortId.startsWith(remaining)) {
                    builder.suggest(fullId);
                }
            }
            return builder.buildFuture();
        };
    }

    private static int executeLocate(CommandSourceStack source, String type, String id) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.translatable("locateo.command.only_player"));
            return 0;
        }

        // 如果用户输入的不包含 :，自动补全 minecraft:
        String fullId = id.contains(":") ? id : "minecraft:" + id;
        String command = String.format("locate %s %s", type, fullId);

        LocateOnly.LOGGER.info("Executing: {}", command);

        try {
            try {
                Method withMaxPerm = CommandSourceStack.class.getMethod("withMaximumPermission", LevelBasedPermissionSet.class);
                CommandSourceStack adminSource = (CommandSourceStack) withMaxPerm.invoke(source, LevelBasedPermissionSet.OWNER);
                adminSource.getServer().getCommands().performPrefixedCommand(adminSource, command);
                return 1;
            } catch (NoSuchMethodException e1) {
                try {
                    Field permissionsField = CommandSourceStack.class.getDeclaredField("permissions");
                    permissionsField.setAccessible(true);
                    LevelBasedPermissionSet oldPerms = (LevelBasedPermissionSet) permissionsField.get(source);
                    permissionsField.set(source, LevelBasedPermissionSet.OWNER);
                    source.getServer().getCommands().performPrefixedCommand(source, command);
                    permissionsField.set(source, oldPerms);
                    return 1;
                } catch (Exception e2) {
                    var consoleSource = source.getServer().createCommandSourceStack();
                    consoleSource.getServer().getCommands().performPrefixedCommand(consoleSource, command);
                    source.sendSuccess(() -> Component.translatable("locateo.command.executed"), false);
                    return 1;
                }
            }
        } catch (Exception e) {
            source.sendFailure(Component.translatable("locateo.command.failed", e.getMessage()));
            LocateOnly.LOGGER.error("Locate command failed: {}", e.getMessage());
            return 0;
        }
    }

    private static void registerConfigCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("configlocateo")
                .requires(source -> true)
                .then(literal("gui")
                        .executes(context -> {
                            CommandSourceStack source = context.getSource();
                            if (!(source.getEntity() instanceof ServerPlayer player)) {
                                source.sendFailure(Component.translatable("locateo.command.only_player"));
                                return 0;
                            }
                            ModConfig.openGui(player);
                            return 1;
                        })
                )
                .then(literal("cmd")
                        .then(literal("locateo")
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(context -> {
                                            boolean enabled = BoolArgumentType.getBool(context, "enabled");
                                            ModConfig.setCommandEnabled("locateo", enabled);
                                            context.getSource().sendSuccess(
                                                    () -> Component.translatable("locateo.config.updated", "locateo", enabled),
                                                    true
                                            );
                                            return 1;
                                        })
                                )
                        )
                        .then(literal("locateonly")
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(context -> {
                                            boolean enabled = BoolArgumentType.getBool(context, "enabled");
                                            ModConfig.setCommandEnabled("locateonly", enabled);
                                            context.getSource().sendSuccess(
                                                    () -> Component.translatable("locateo.config.updated", "locateonly", enabled),
                                                    true
                                            );
                                            return 1;
                                        })
                                )
                        )
                        .then(literal("lo")
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(context -> {
                                            boolean enabled = BoolArgumentType.getBool(context, "enabled");
                                            ModConfig.setCommandEnabled("lo", enabled);
                                            context.getSource().sendSuccess(
                                                    () -> Component.translatable("locateo.config.updated", "lo", enabled),
                                                    true
                                            );
                                            return 1;
                                        })
                                )
                        )
                )
        );
    }
}