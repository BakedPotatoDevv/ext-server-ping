package potato.baked.externalServerPing.discordIntegration;

import org.bukkit.configuration.file.FileConfiguration;

public class DiscordConfig {

    private final String token;
    private final long guildId;
    private final long channelId;
    private final long messageId;

    public DiscordConfig(FileConfiguration config) {
        this.token = config.getString("discord-token", "").trim();
        this.guildId = config.getLong("discord-guild-id", 0L);
        this.channelId = config.getLong("discord-channel-id", 0L);
        this.messageId = config.getLong("discord-message-id", 0L);
    }

    public String getToken() { return token; }
    public long getGuildId() { return guildId; }
    public long getChannelId() { return channelId; }
    public long getMessageId() { return messageId; }

    public boolean isValid() {
        return token != null && !token.isBlank() && channelId > 0;
    }

    public boolean hasGuild() {
        return guildId > 0;
    }
}
