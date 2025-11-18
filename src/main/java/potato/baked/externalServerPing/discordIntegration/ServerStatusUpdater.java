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

    public ServerStatusUpdater(JavaPlugin plugin, DiscordBotManager botManager, ExternalServerPing serverPing) {
        this.plugin = plugin;
        this.botManager = botManager;
        this.serverPing = serverPing;
    }

    /**
     * Start the updater. This waits until the bot is ready, then either loads the configured message
     * or creates a new one (when messageId == 0 or the configured message is missing).
     */
    public void startUpdater(int intervalSeconds) {
        stopUpdater(); // cancel any old task

        botManager.onReady(() -> {
            TextChannel channel = botManager.getChannel();
            if (channel == null) {
                plugin.getLogger().warning("[Discord] Channel not found. Discord integration disabled.");
                return;
            }

            long configuredId = botManager.getMessageId();
            if (configuredId > 0) {
                // Try to fetch the configured message; on failure create a new one (and persist its id)
                channel.retrieveMessageById(configuredId).queue(
                        msg -> {
                            statusMessage = msg;
                            plugin.getLogger().info("[Discord] Using configured status message (id=" + configuredId + ").");
                            startUpdaterTask(channel, intervalSeconds);
                        },
                        err -> {
                            plugin.getLogger().info("[Discord] Configured message not found or inaccessible; creating a new one.");
                            createNewStatusMessage(channel, intervalSeconds);
                        }
                );
            } else {
                // No configured message ID -> create and persist a new message
                plugin.getLogger().info("[Discord] No message ID configured; creating a new status message.");
                createNewStatusMessage(channel, intervalSeconds);
            }
        });
    }

    /**
     * Stop the updater if running.
     */
    public void stopUpdater() {
        if (updaterTask != null) {
            updaterTask.cancel();
            updaterTask = null;
        }
    }

    /**
     * The repeating task that edits the tracked message (or attempts to recreate it when missing).
     */
    private void startUpdaterTask(TextChannel channel, int intervalSeconds) {
        // cancel existing (safety)
        if (updaterTask != null) updaterTask.cancel();

        updaterTask = new BukkitRunnable() {
            @Override
            public void run() {
                // Safety: if no statusMessage object (e.g., deleted), try to recover
                if (statusMessage == null) {
                    long mid = botManager.getMessageId();
                    if (mid > 0) {
                        // try to re-fetch the configured message
                        try {
                            channel.retrieveMessageById(mid).queue(
                                    msg -> {
                                        statusMessage = msg;
                                    },
                                    err -> {
                                        // message doesn't exist or can't be fetched -> create a new one and update stored id
                                        plugin.getLogger().info("[Discord] Tracked message missing; creating a new message and updating stored ID.");
                                        createNewStatusMessage(channel, intervalSeconds);
                                    }
                            );
                        } catch (Exception e) {
                            // fallback: create a new message
                            plugin.getLogger().warning("[Discord] Error while retrieving message by ID: " + e.getMessage());
                            createNewStatusMessage(channel, intervalSeconds);
                        }
                        return; // wait until next tick or the async retrieve returns
                    } else {
                        // no configured ID -> create new message
                        createNewStatusMessage(channel, intervalSeconds);
                        return;
                    }
                }

                // At this point we have statusMessage (or a createNewStatusMessage call has been issued)
                if (!serverPing.isServerOnline()) {
                    // Server offline: if config had an ID (nonzero) we must overwrite that message.
                    if (botManager.getMessageId() == 0) {
                        // If messageId is still 0 we create a new message (and persist)
                        createNewStatusMessage(channel, intervalSeconds);
                    } else {
                        // Overwrite the tracked message
                        statusMessage.editMessageEmbeds(EmbedBuilderUtil.buildOfflineEmbed().build()).queue(
                                null,
                                err -> {
                                    // If edit fails (message deleted/forbidden), reset and recreate next tick
                                    plugin.getLogger().warning("[Discord] Failed to edit tracked message (maybe deleted). It will be recreated.");
                                    statusMessage = null;
                                }
                        );
                    }
                } else {
                    // Server online: update the tracked message with online embed
                    statusMessage.editMessageEmbeds(
                            EmbedBuilderUtil.buildOnlineEmbed(
                                    serverPing.getMotd(),
                                    serverPing.getOnlinePlayers(),
                                    serverPing.getMaxPlayers(),
                                    serverPing.getPing()
                            ).build()
                    ).queue(
                            null,
                            err -> {
                                plugin.getLogger().warning("[Discord] Failed to edit tracked message (maybe deleted). It will be recreated.");
                                statusMessage = null;
                            }
                    );
                }
            }
        }.runTaskTimerAsynchronously(plugin, 0L, 20L * intervalSeconds);
    }

    /**
     * Create a new message in the given channel, persist its ID via botManager.updateMessageId,
     * set statusMessage and (if updaterTask isn't already running) start the updater task.
     *
     * This method will not spawn duplicate updater tasks:
     * - If updaterTask is null we start it after creating the message.
     * - If updaterTask already exists we only set statusMessage.
     */
    private void createNewStatusMessage(TextChannel channel, int intervalSeconds) {
        // choose embed according to current server status
        var embed = serverPing.isServerOnline()
                ? EmbedBuilderUtil.buildOnlineEmbed(serverPing.getMotd(), serverPing.getOnlinePlayers(), serverPing.getMaxPlayers(), serverPing.getPing()).build()
                : EmbedBuilderUtil.buildOfflineEmbed().build();

        channel.sendMessageEmbeds(embed).queue(msg -> {
            statusMessage = msg;
            try {
                botManager.updateMessageId(msg.getIdLong()); // persist the new id
            } catch (Exception e) {
                plugin.getLogger().warning("[Discord] Failed to persist new message ID: " + e.getMessage());
            }

            // If updaterTask isn't running, start it now; otherwise the running task will pick up the new statusMessage.
            if (updaterTask == null) {
                startUpdaterTask(channel, intervalSeconds);
            }
        }, err -> {
            plugin.getLogger().warning("[Discord] Failed to send new status message: " + err.getMessage());
        });
    }
}
