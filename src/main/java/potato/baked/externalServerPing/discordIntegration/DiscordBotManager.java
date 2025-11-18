package potato.baked.externalServerPing.discordIntegration;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.bukkit.plugin.java.JavaPlugin;

import javax.security.auth.login.LoginException;

public class DiscordBotManager {

    private final JavaPlugin plugin;
    private final DiscordConfig config;
    private JDA jda;

    public DiscordBotManager(JavaPlugin plugin, DiscordConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void startBot() throws LoginException {
        jda = JDABuilder.createLight(config.getToken(), GatewayIntent.GUILD_MESSAGES)
                .build();
        plugin.getLogger().info("[Discord] Bot started!");
    }

    public void shutdownBot() {
        if (jda != null) jda.shutdown();
        plugin.getLogger().info("[Discord] Bot shut down.");
    }

    public TextChannel getChannel() {
        if (jda == null) return null;
        return jda.getTextChannelById(config.getChannelId());
    }

    public long getMessageId() {
        return config.getMessageId();
    }
}
