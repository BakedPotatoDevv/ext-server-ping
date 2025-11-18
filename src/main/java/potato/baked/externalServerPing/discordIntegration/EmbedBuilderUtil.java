package potato.baked.externalServerPing.discordIntegration;

import net.dv8tion.jda.api.EmbedBuilder;

import java.awt.Color;

public class EmbedBuilderUtil {

    public static EmbedBuilder buildOnlineEmbed(String motd, int online, int max, int ping) {
        return new EmbedBuilder()
                .setTitle("Server Online ✅")
                .setColor(Color.GREEN)
                .addField("MOTD", motd, false)
                .addField("Players", online + "/" + max, true)
                .addField("Ping", ping + "ms", true);
    }

    public static EmbedBuilder buildOfflineEmbed() {
        return new EmbedBuilder()
                .setTitle("Server Offline ❌")
                .setColor(Color.RED)
                .setDescription("The server is currently unreachable.");
    }
}
