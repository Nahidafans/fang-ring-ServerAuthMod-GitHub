package com.serverauth.server;

import com.serverauth.config.AuthConfig;
import com.serverauth.network.AuthPacket;
import com.serverauth.network.DeviceFingerprintPacket;
import com.serverauth.network.NetworkHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.function.Supplier;

public class ServerAuthHandler {
    private static final Logger LOGGER = LogManager.getLogger("ServerAuth");

    // ========== Handle auth request from client ==========
    public static void handleAuthRequest(AuthPacket packet, NetworkEvent.Context ctx) {
        ServerPlayer player = ctx.getSender();
        if (player == null) return;

        int playerId = packet.getPlayerId();
        AuthConfig.CheckResult result = AuthConfig.checkPlayerAllowed(playerId);

        LOGGER.info("Player {} (ID: {}) auth: {} - {}",
                player.getName().getString(), playerId,
                result.isAllowed() ? "PASS" : "DENY", result.getReason());

        NetworkHandler.CHANNEL.send(
            PacketDistributor.PLAYER.with(() -> player),
            new AuthPacket(result.isAllowed(), result.getReason())
        );

        if (!result.isAllowed()) {
            player.connection.disconnect(Component.literal(
                ChatFormatting.RED + "================================\n" +
                ChatFormatting.RED + "  Connection Refused!\n" +
                ChatFormatting.RED + "================================\n" +
                ChatFormatting.RED + "  " + result.getReason() + "\n" +
                ChatFormatting.RED + "================================"
            ));
        }
    }

    // ========== Handle device fingerprint from client ==========
    public static void handleDeviceFingerprint(DeviceFingerprintPacket packet, NetworkEvent.Context ctx) {
        ServerPlayer player = ctx.getSender();
        if (player == null) return;

        String uuid = player.getUUID().toString();
        int playerId = AuthConfig.getPlayerId(uuid);
        if (playerId == -1) playerId = AuthConfig.getOrAssignPlayerId(uuid);

        String fingerprint = packet.getDeviceFingerprint();
        String accountType = packet.getAccountType();
        boolean deviceChanged = packet.isDeviceChanged();

        AuthConfig.DeviceRecord record = AuthConfig.recordDeviceFingerprint(
            uuid, fingerprint, accountType, deviceChanged
        );

        boolean isPremium = AuthConfig.isPlayerPremium(uuid);
        String premiumTag = isPremium ? ChatFormatting.GOLD + "[PREMIUM]" : ChatFormatting.GRAY + "[OFFLINE]";

        LOGGER.info("Device fingerprint for {} (ID: #{}) | Type: {} | Device changed: {} | Premium: {}",
            player.getName().getString(), playerId, accountType, deviceChanged, isPremium);

        // Notify admins about device changes
        if (deviceChanged && record.deviceChangeCount > 0) {
            String alert = ChatFormatting.RED + "[!] Device changed for " + 
                player.getName().getString() + " (ID: #" + playerId + ") " +
                "Changes: " + record.deviceChangeCount + " | " + premiumTag;
            
            MinecraftServer server = player.getServer();
            if (server != null) {
                server.getPlayerList().getPlayers().stream()
                    .filter(p -> p.hasPermissions(2))
                    .forEach(admin -> admin.sendSystemMessage(Component.literal(alert)));
            }
            LOGGER.warn(alert);
        }

        // Show device info to the player
        player.sendSystemMessage(Component.literal(
            ChatFormatting.GREEN + "[Auth] Device fingerprint registered: " + 
            ChatFormatting.GRAY + fingerprint.substring(0, Math.min(12, fingerprint.length())) + "..." + 
            ChatFormatting.WHITE + " " + premiumTag
        ));
    }

    // ========== On player login ==========
    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        String uuid = player.getUUID().toString();
        int playerId = AuthConfig.getOrAssignPlayerId(uuid);

        LOGGER.info("Player {} logged in, ID: #{}", player.getName().getString(), playerId);

        AuthConfig.CheckResult result = AuthConfig.checkPlayerAllowed(playerId);

        if (!result.isAllowed()) {
            player.connection.disconnect(Component.literal(
                ChatFormatting.RED + "================================\n" +
                ChatFormatting.RED + "  Connection Refused!\n" +
                ChatFormatting.RED + "================================\n" +
                ChatFormatting.RED + "  " + result.getReason() + "\n" +
                ChatFormatting.RED + "================================"
            ));
        } else {
            boolean isPremium = AuthConfig.isPlayerPremium(uuid);
            String premiumTag = isPremium ? ChatFormatting.GOLD + "[PREMIUM]" : ChatFormatting.GRAY + "[OFFLINE]";
            int deviceChanges = AuthConfig.getDeviceChangeCount(uuid);

            player.sendSystemMessage(Component.literal(
                ChatFormatting.GREEN + "================================\n" +
                ChatFormatting.GREEN + "  Auth Passed! ID: #" + playerId + "\n" +
                ChatFormatting.GREEN + "================================\n" +
                (deviceChanges > 0 ? ChatFormatting.YELLOW + "  Device changes: " + deviceChanges + "\n" : "") +
                ChatFormatting.WHITE + "  " + premiumTag + "\n" +
                ChatFormatting.GREEN + "================================"
            ));
        }
    }

    // ========== Helper ==========
    private static Supplier<Component> msg(String text) {
        return () -> Component.literal(text);
    }

    // ========== Register commands ==========
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        // === Login command (available to ALL players) ===
        event.getDispatcher().register(Commands.literal("serverauth")
            .then(Commands.literal("login")
                .then(Commands.argument("password", StringArgumentType.word())
                    .executes(context -> {
                        String inputPwd = StringArgumentType.getString(context, "password");
                        ServerPlayer player = context.getSource().getPlayer();
                        if (player == null) {
                            context.getSource().sendFailure(
                                Component.literal(ChatFormatting.RED + "This command can only be used by players"));
                            return 0;
                        }
                        
                        if (AuthConfig.verifyAdminPassword(inputPwd)) {
                            player.getServer().getPlayerList().op(player.getGameProfile());

                            LOGGER.info("Player {} authenticated as admin via password", player.getName().getString());
                            context.getSource().sendSuccess(
                                msg(ChatFormatting.GREEN + "========================================"), true);
                            context.getSource().sendSuccess(
                                msg(ChatFormatting.GREEN + "  Admin authentication successful!"), true);
                            context.getSource().sendSuccess(
                                msg(ChatFormatting.GREEN + "  You have been granted OP level 4"), true);
                            context.getSource().sendSuccess(
                                msg(ChatFormatting.GREEN + "========================================"), true);
                        } else {
                            context.getSource().sendFailure(
                                Component.literal(ChatFormatting.RED + "Invalid admin password!"));
                        }
                        return 1;
                    })
                )
            )
        );

        // === Admin commands (require OP level 2) ===
        event.getDispatcher().register(Commands.literal("serverauth")
            .requires(source -> source.hasPermission(2))

            // --- /serverauth id <player> ---
            .then(Commands.literal("id")
                .then(Commands.argument("player", EntityArgument.player())
                    .executes(context -> {
                        ServerPlayer target = EntityArgument.getPlayer(context, "player");
                        String uuid = target.getUUID().toString();
                        int id = AuthConfig.getPlayerId(uuid);
                        if (id != -1) {
                            context.getSource().sendSuccess(
                                msg(ChatFormatting.GREEN + "Player " + target.getName().getString()
                                    + " ID: #" + id), true);
                        } else {
                            context.getSource().sendSuccess(
                                msg(ChatFormatting.RED + "Player " + target.getName().getString()
                                    + " has no ID yet"), true);
                        }
                        return 1;
                    })
                )
            )

            // --- /serverauth device <player> ---
            .then(Commands.literal("device")
                .then(Commands.argument("player", EntityArgument.player())
                    .executes(context -> {
                        ServerPlayer target = EntityArgument.getPlayer(context, "player");
                        String uuid = target.getUUID().toString();
                        AuthConfig.DeviceRecord record = AuthConfig.getDeviceRecord(uuid);
                        
                        if (record == null) {
                            context.getSource().sendSuccess(
                                msg(ChatFormatting.YELLOW + "No device fingerprint recorded for " + target.getName().getString()), true);
                        } else {
                            boolean isPremium = AuthConfig.isPlayerPremium(uuid);
                            String type = isPremium ? ChatFormatting.GOLD + "PREMIUM (" + record.accountType + ")" : 
                                                      ChatFormatting.GRAY + "OFFLINE (" + record.accountType + ")";
                            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                            
                            context.getSource().sendSuccess(
                                msg(ChatFormatting.GOLD + "=== Device Info: " + target.getName().getString() + " ==="), true);
                            context.getSource().sendSuccess(
                                msg(ChatFormatting.WHITE + "Account: " + type), true);
                            context.getSource().sendSuccess(
                                msg(ChatFormatting.WHITE + "First seen: " + sdf.format(new Date(record.firstSeen))), true);
                            context.getSource().sendSuccess(
                                msg(ChatFormatting.WHITE + "Last seen: " + sdf.format(new Date(record.lastSeen))), true);
                            context.getSource().sendSuccess(
                                msg(ChatFormatting.WHITE + "Device changes: " + ChatFormatting.RED + record.deviceChangeCount), true);
                            context.getSource().sendSuccess(
                                msg(ChatFormatting.GRAY + "Current fingerprint: " + 
                                    (record.deviceFingerprints.isEmpty() ? "N/A" : 
                                     record.deviceFingerprints.get(record.deviceFingerprints.size()-1).substring(0, 12) + "...")), true);
                            context.getSource().sendSuccess(
                                msg(ChatFormatting.GRAY + "History (" + record.deviceFingerprints.size() + " entries):"), true);
                            for (int i = 0; i < record.deviceFingerprints.size(); i++) {
                                String fp = record.deviceFingerprints.get(i);
                                context.getSource().sendSuccess(
                                    msg(ChatFormatting.DARK_GRAY + "  [" + (i+1) + "] " + 
                                        fp.substring(0, Math.min(16, fp.length())) + "..."), true);
                            }
                        }
                        return 1;
                    })
                )
            )

            // --- /serverauth whitelist add <id> ---
            .then(Commands.literal("whitelist")
                .then(Commands.literal("add")
                    .then(Commands.argument("id", IntegerArgumentType.integer(1))
                        .executes(context -> {
                            int id = IntegerArgumentType.getInteger(context, "id");
                            if (AuthConfig.addToWhitelist(id)) {
                                context.getSource().sendSuccess(
                                    msg(ChatFormatting.GREEN + "ID #" + id + " added to whitelist"), true);
                                LOGGER.info("Admin added ID #{} to whitelist", id);
                            } else {
                                context.getSource().sendSuccess(
                                    msg(ChatFormatting.YELLOW + "ID #" + id + " already in whitelist"), true);
                            }
                            return 1;
                        })
                    )
                )
                .then(Commands.literal("remove")
                    .then(Commands.argument("id", IntegerArgumentType.integer(1))
                        .executes(context -> {
                            int id = IntegerArgumentType.getInteger(context, "id");
                            if (AuthConfig.removeFromWhitelist(id)) {
                                context.getSource().sendSuccess(
                                    msg(ChatFormatting.GREEN + "ID #" + id + " removed from whitelist"), true);
                                LOGGER.info("Admin removed ID #{} from whitelist", id);
                            } else {
                                context.getSource().sendSuccess(
                                    msg(ChatFormatting.YELLOW + "ID #" + id + " not in whitelist"), true);
                            }
                            return 1;
                        })
                    )
                )
                .then(Commands.literal("list")
                    .executes(context -> {
                        var list = AuthConfig.getWhitelist();
                        if (list.isEmpty()) {
                            context.getSource().sendSuccess(
                                msg(ChatFormatting.YELLOW + "Whitelist is empty"), true);
                        } else {
                            context.getSource().sendSuccess(
                                msg(ChatFormatting.GREEN + "=== Whitelist (" + list.size() + ") ==="), true);
                            for (int id : list) {
                                context.getSource().sendSuccess(
                                    msg(ChatFormatting.WHITE + "  #" + id), true);
                            }
                        }
                        return 1;
                    })
                )
            )

            // --- /serverauth blacklist add <id> ---
            .then(Commands.literal("blacklist")
                .then(Commands.literal("add")
                    .then(Commands.argument("id", IntegerArgumentType.integer(1))
                        .executes(context -> {
                            int id = IntegerArgumentType.getInteger(context, "id");
                            if (AuthConfig.addToBlacklist(id)) {
                                context.getSource().sendSuccess(
                                    msg(ChatFormatting.RED + "ID #" + id + " added to blacklist"), true);
                                LOGGER.info("Admin added ID #{} to blacklist", id);
                            } else {
                                context.getSource().sendSuccess(
                                    msg(ChatFormatting.YELLOW + "ID #" + id + " already in blacklist"), true);
                            }
                            return 1;
                        })
                    )
                )
                .then(Commands.literal("remove")
                    .then(Commands.argument("id", IntegerArgumentType.integer(1))
                        .executes(context -> {
                            int id = IntegerArgumentType.getInteger(context, "id");
                            if (AuthConfig.removeFromBlacklist(id)) {
                                context.getSource().sendSuccess(
                                    msg(ChatFormatting.GREEN + "ID #" + id + " removed from blacklist"), true);
                                LOGGER.info("Admin removed ID #{} from blacklist", id);
                            } else {
                                context.getSource().sendSuccess(
                                    msg(ChatFormatting.YELLOW + "ID #" + id + " not in blacklist"), true);
                            }
                            return 1;
                        })
                    )
                )
                .then(Commands.literal("list")
                    .executes(context -> {
                        var list = AuthConfig.getBlacklist();
                        if (list.isEmpty()) {
                            context.getSource().sendSuccess(
                                msg(ChatFormatting.GREEN + "Blacklist is empty"), true);
                        } else {
                            context.getSource().sendSuccess(
                                msg(ChatFormatting.RED + "=== Blacklist (" + list.size() + ") ==="), true);
                            for (int id : list) {
                                context.getSource().sendSuccess(
                                    msg(ChatFormatting.RED + "  #" + id), true);
                            }
                        }
                        return 1;
                    })
                )
            )

            // --- /serverauth reload ---
            .then(Commands.literal("reload")
                .executes(context -> {
                    AuthConfig.load();
                    context.getSource().sendSuccess(
                        msg(ChatFormatting.GREEN + "Config reloaded!"), true);
                    return 1;
                })
            )

            // --- /serverauth resetpwd ---
            .then(Commands.literal("resetpwd")
                .executes(context -> {
                    String newPwd = AuthConfig.regenerateAdminPassword();
                    if (newPwd != null) {
                        LOGGER.info("Admin password reset by admin! New password: {}", newPwd);
                        context.getSource().sendSuccess(
                            msg(ChatFormatting.GREEN + "Admin password has been reset!"), true);
                        context.getSource().sendSuccess(
                            msg(ChatFormatting.GREEN + "New password: " + ChatFormatting.YELLOW + newPwd), true);
                        context.getSource().sendSuccess(
                            msg(ChatFormatting.GRAY + "Check server console for the new password"), true);
                    } else {
                        context.getSource().sendFailure(
                            Component.literal(ChatFormatting.RED + "Failed to reset password!"));
                    }
                    return 1;
                })
            )

            // --- /serverauth status ---
            .then(Commands.literal("status")
                .executes(context -> {
                    context.getSource().sendSuccess(
                        msg(ChatFormatting.GOLD + "=== ServerAuth Status ==="), true);
                    context.getSource().sendSuccess(
                        msg(ChatFormatting.WHITE + "Whitelist: " + ChatFormatting.GREEN
                            + AuthConfig.getWhitelist().size()), true);
                    context.getSource().sendSuccess(
                        msg(ChatFormatting.WHITE + "Blacklist: " + ChatFormatting.RED
                            + AuthConfig.getBlacklist().size()), true);
                    context.getSource().sendSuccess(
                        msg(ChatFormatting.WHITE + "Registered: " + ChatFormatting.AQUA
                            + AuthConfig.getWhitelist().size()), true);
                    return 1;
                })
            )
        );
    }
}
