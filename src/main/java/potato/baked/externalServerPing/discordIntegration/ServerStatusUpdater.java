package potato.baked.externalServerPing.discordIntegration;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import potato.baked.externalServerPing.ExternalServerPing;

public class ServerStatusUpdater {

    private final JavaPlugin plugin;
    private final DiscordBotManager botManager;
    private final ExternalServerPing serverPing;

    private Message statusMessage;
    private BukkitTask updaterTask;

    // Fixed safe Discord interval (Discord won't complain at this rate)
    private static final int DISCORD_UPDATE_SECONDS = 12;

    // Cache for comparison — prevents unnecessary edits
    private boolean lastOnline = false;
    private int lastOnlinePlayers = -1;
    private int lastMaxPlayers = -1;
    private String lastMotd = "";
    private int lastPing = -1;

    public ServerStatusUpdater(JavaPlugin plugin, DiscordBotManager botManager, ExternalServerPing serverPing) {
        this.plugin = plugin;
        this.botManager = botManager;
        this.serverPing = serverPing;
    }

    public void startUpdater(int ignored) {
        stopUpdater(); // cancel old tasks

        botManager.onReady(() -> {
            TextChannel channel = botManager.getChannel();
            if (channel == null) {
                plugin.getLogger().warning("[Discord] Channel not found. Discord integration disabled.");
                return;
            }

            long configuredId = botManager.getMessageId();
            if (configuredId > 0) {
                channel.retrieveMessageById(configuredId).queue(
                        msg -> {
                            statusMessage = msg;
                            plugin.getLogger().info("[Discord] Using configured status message (id=" + configuredId + ").");
                            startUpdaterTask(channel);
                        },
                        err -> {
                            plugin.getLogger().info("[Discord] Configured status message missing. Creating new.");
                            createNewStatusMessage(channel);
                        }
                );
            } else {
                plugin.getLogger().info("[Discord] No message ID configured. Creating status message.");
                createNewStatusMessage(channel);
            }
        });
    }

    public void stopUpdater() {
        if (updaterTask != null) {
            updaterTask.cancel();
            updaterTask = null;
        }
    }

    private void startUpdaterTask(TextChannel channel) {
        if (updaterTask != null) updaterTask.cancel();

        updaterTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (statusMessage == null) {
                    attemptRefetchOrRecreate(channel);
                    return;
                }

                // Read current server state
                boolean curOnline = serverPing.isServerOnline();
                int curOnlinePlayers = serverPing.getOnlinePlayers();
                int curMaxPlayers = serverPing.getMaxPlayers();
                String curMotd = serverPing.getMotd();
                int curPing = serverPing.getPing();

                // Detect if embed needs updating
                boolean changed =
                        curOnline != lastOnline ||
                                curOnlinePlayers != lastOnlinePlayers ||
                                curMaxPlayers != lastMaxPlayers ||
                                !curMotd.equals(lastMotd) ||
                                curPing != lastPing;

                if (!changed) {
                    // No changes → skip editing Discord
                    return;
                }

                // Build appropriate embed
                var embed = curOnline
                        ? EmbedBuilderUtil.buildOnlineEmbed(curMotd, curOnlinePlayers, curMaxPlayers, curPing).build()
                        : EmbedBuilderUtil.buildOfflineEmbed().build();

                statusMessage.editMessageEmbeds(embed).queue(
                        unused -> {
                            // Update cache
                            lastOnline = curOnline;
                            lastOnlinePlayers = curOnlinePlayers;
                            lastMaxPlayers = curMaxPlayers;
                            lastMotd = curMotd;
                            lastPing = curPing;
                        },
                        err -> {
                            plugin.getLogger().warning("[Discord] Failed to edit message (maybe deleted). Recreating.");
                            statusMessage = null;
                        }
                );
            }
        }.runTaskTimerAsynchronously(plugin, 0L, 20L * DISCORD_UPDATE_SECONDS);
    }

    private void attemptRefetchOrRecreate(TextChannel channel) {
        long mid = botManager.getMessageId();

        if (mid > 0) {
            channel.retrieveMessageById(mid).queue(
                    msg -> statusMessage = msg,
                    err -> {
                        plugin.getLogger().info("[Discord] Tracked message vanished. Recreating.");
                        createNewStatusMessage(channel);
                    }
            );
        } else {
            createNewStatusMessage(channel);
        }
    }

    private void createNewStatusMessage(TextChannel channel) {
        var embed = serverPing.isServerOnline()
                ? EmbedBuilderUtil.buildOnlineEmbed(
                serverPing.getMotd(),
                serverPing.getOnlinePlayers(),
                serverPing.getMaxPlayers(),
                serverPing.getPing()
        ).build()
                : EmbedBuilderUtil.buildOfflineEmbed().build();

        channel.sendMessageEmbeds(embed).queue(msg -> {
            statusMessage = msg;
            botManager.updateMessageId(msg.getIdLong());

            // Seed cache so next update only changes if needed
            lastOnline = serverPing.isServerOnline();
            lastOnlinePlayers = serverPing.getOnlinePlayers();
            lastMaxPlayers = serverPing.getMaxPlayers();
            lastMotd = serverPing.getMotd();
            lastPing = serverPing.getPing();

            if (updaterTask == null) startUpdaterTask(channel);
        }, err -> plugin.getLogger().warning("[Discord] Failed to send new status message: " + err.getMessage()));
    }
}
