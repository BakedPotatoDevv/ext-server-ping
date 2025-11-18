package potato.baked.externalServerPing.discordIntegration;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import potato.baked.externalServerPing.ExternalServerPing;

public class ServerStatusUpdater {

    private final JavaPlugin plugin;
    private final DiscordBotManager botManager;
    private final ExternalServerPing serverPing;
    private Message statusMessage;

    public ServerStatusUpdater(JavaPlugin plugin, DiscordBotManager botManager, ExternalServerPing serverPing) {
        this.plugin = plugin;
        this.botManager = botManager;
        this.serverPing = serverPing;
    }

    public void startUpdater(int intervalSeconds) {
        TextChannel channel = botManager.getChannel();
        if (channel == null) {
            plugin.getLogger().warning("[Discord] Channel not found. Discord integration disabled.");
            return;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!serverPing.isServerOnline()) {
                    if (statusMessage != null) {
                        statusMessage.editMessageEmbeds(EmbedBuilderUtil.buildOfflineEmbed().build()).queue();
                    } else {
                        channel.sendMessageEmbeds(EmbedBuilderUtil.buildOfflineEmbed().build())
                                .queue(msg -> statusMessage = msg);
                    }
                } else {
                    if (statusMessage != null) {
                        statusMessage.editMessageEmbeds(
                                EmbedBuilderUtil.buildOnlineEmbed(
                                        serverPing.getMotd(),
                                        serverPing.getOnlinePlayers(),
                                        serverPing.getMaxPlayers(),
                                        serverPing.getPing()
                                ).build()
                        ).queue();
                    } else {
                        channel.sendMessageEmbeds(
                                EmbedBuilderUtil.buildOnlineEmbed(
                                        serverPing.getMotd(),
                                        serverPing.getOnlinePlayers(),
                                        serverPing.getMaxPlayers(),
                                        serverPing.getPing()
                                ).build()
                        ).queue(msg -> statusMessage = msg);
                    }
                }
            }
        }.runTaskTimerAsynchronously(plugin, 0L, 20L * intervalSeconds);
    }
}
