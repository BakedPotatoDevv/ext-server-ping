package potato.baked.externalServerPing.discordIntegration;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class DiscordBotManager {

    private final JavaPlugin plugin;
    private final DiscordConfig config;
    private JDA jda;
    private final List<Runnable> readyCallbacks = new CopyOnWriteArrayList<>();
    private long currentMessageId;

    public DiscordBotManager(JavaPlugin plugin, DiscordConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.currentMessageId = config.getMessageId();
    }

    public void startBot() {
        String token = config.getToken();
        if (token == null || token.isBlank()) {
            plugin.getLogger().warning("[Discord] No token provided! Discord integration is disabled.");
            return;
        }

        try {
            jda = JDABuilder.createLight(token, GatewayIntent.GUILD_MESSAGES).build();
            jda.addEventListener(new ListenerAdapter() {
                @Override
                public void onReady(@NotNull ReadyEvent event) {
                    plugin.getLogger().info("[Discord] Bot is fully ready!");
                    readyCallbacks.forEach(Runnable::run);
                    readyCallbacks.clear();
                }
            });
        } catch (IllegalArgumentException e) {
            plugin.getLogger().severe("[Discord] Failed to login! Check your token in config.yml");
            e.printStackTrace();
        }
    }

    public void onReady(Runnable callback) {
        if (jda != null && jda.getStatus() == JDA.Status.CONNECTED) {
            callback.run();
        } else {
            readyCallbacks.add(callback);
        }
    }

    public void shutdownBot() {
        if (jda != null) {
            jda.shutdown();
            plugin.getLogger().info("[Discord] Bot shut down.");
        }
    }

    public TextChannel getChannel() {
        if (jda == null) return null;
        if (config.hasGuild()) {
            Guild guild = jda.getGuildById(config.getGuildId());
            return guild != null ? guild.getTextChannelById(config.getChannelId()) : null;
        }
        return jda.getTextChannelById(config.getChannelId());
    }

    public long getMessageId() {
        return currentMessageId;
    }

    public void updateMessageId(long newId) {
        currentMessageId = newId;
        plugin.getConfig().set("discord-message-id", newId);
        plugin.saveConfig();
    }
}
