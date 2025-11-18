package potato.baked.externalServerPing.discordIntegration;

import org.bukkit.configuration.file.FileConfiguration;

public class DiscordConfig {
    private final String token;
    private final long channelId;
    private final long messageId; // optional; 0 = create new message

    public DiscordConfig(FileConfiguration config) {
        this.token = config.getString("discord-token", "");
        this.channelId = config.getLong("discord-channel-id", 0);
        this.messageId = config.getLong("discord-message-id", 0);
    }

    public String getToken() { return token; }
    public long getChannelId() { return channelId; }
    public long getMessageId() { return messageId; }
}
