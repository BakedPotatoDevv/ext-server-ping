package potato.baked.externalServerPing.discordIntegration;

import net.dv8tion.jda.api.EmbedBuilder;

import java.awt.Color;
import java.time.Instant;

public class EmbedBuilderUtil {

    public static EmbedBuilder buildOnlineEmbed(String motd, int online, int max, int ping) {
        return new EmbedBuilder()
                .setTitle("Server Online ✅")
                .setColor(Color.GREEN)
                .addField("MOTD", motd, false)
                .addField("Players", online + "/" + max, true)
                .addField("Ping", ping + "ms", true)
                .setTimestamp(Instant.now());
    }

    public static EmbedBuilder buildOfflineEmbed() {
        return new EmbedBuilder()
                .setTitle("Server Offline ❌")
                .setColor(Color.RED)
                .setDescription("The target server is currently unreachable.")
                .setTimestamp(Instant.now());
    }

    /**
     * Embed shown when the Minecraft server *hosting* ExternalServerPing
     * shuts down or reloads. Different from the target server being offline.
     */
    public static EmbedBuilder buildHostOfflineEmbed() {
        return new EmbedBuilder()
                .setTitle("⛔ Status Unavailable")
                .setColor(Color.YELLOW)
                .setDescription("The server where External Server Ping is hosted is **offline**.")
                .setFooter("Host server went offline")
                .setTimestamp(Instant.now());
    }
}
