package potato.baked.externalServerPing;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import potato.baked.externalServerPing.discordIntegration.DiscordBotManager;
import potato.baked.externalServerPing.discordIntegration.DiscordConfig;
import potato.baked.externalServerPing.discordIntegration.EmbedBuilderUtil;
import potato.baked.externalServerPing.discordIntegration.ServerStatusUpdater;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;

public final class ExternalServerPing extends JavaPlugin implements TabExecutor {

    private BukkitTask pingTask;
    private String serverIp;
    private int serverPort;
    private int updateInterval;
    private boolean debug;

    private int onlinePlayers = 0;
    private int maxPlayers = 0;
    private String motd = "Unavailable";
    private int ping = -1;
    private boolean serverOnline = false;

    // Discord
    private DiscordBotManager discordBot;
    private ServerStatusUpdater discordUpdater;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfigValues();

        getLogger().info("Pinging " + serverIp + ":" + serverPort + " every " + updateInterval + " seconds.");
        startPinging(serverIp, serverPort, updateInterval);

        Objects.requireNonNull(getCommand("externalserver")).setExecutor(this);
        Objects.requireNonNull(getCommand("externalserver")).setTabCompleter(this);

        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new ExternalServerPingPlaceholder().register();
            getLogger().info("Hooked into PlaceholderAPI successfully!");
        } else {
            getLogger().warning("PlaceholderAPI not found — placeholders will be unavailable.");
        }

        // Discord setup
        DiscordConfig discordConfig = new DiscordConfig(getConfig());
        discordBot = new DiscordBotManager(this, discordConfig);
        try {
            discordBot.startBot();
            discordUpdater = new ServerStatusUpdater(this, discordBot, this);
            discordUpdater.startUpdater(updateInterval);
        } catch (IllegalArgumentException e) {
            getLogger().log(Level.WARNING, "[Discord] Failed to start bot", e);
        }

        getLogger().info("External Server Ping Plugin enabled!");
    }

    @Override
    public void onDisable() {
        if (pingTask != null) pingTask.cancel();

        if (discordUpdater != null && discordBot != null) {
            var channel = discordBot.getChannel();
            if (channel != null) {
                long mid = discordBot.getMessageId();
                if (mid > 0) {
                    // try to edit the configured message
                    channel.retrieveMessageById(mid).queue(
                            msg -> msg.editMessageEmbeds(potato.baked.externalServerPing.discordIntegration.EmbedBuilderUtil.buildOfflineEmbed().build()).queue(),
                            err -> {
                                // couldn't fetch or edit -> fallback to sending a new message (less noisy)
                                channel.sendMessageEmbeds(potato.baked.externalServerPing.discordIntegration.EmbedBuilderUtil.buildOfflineEmbed().build()).queue();
                            }
                    );
                } else {
                    // no configured id -> send a new message
                    channel.sendMessageEmbeds(potato.baked.externalServerPing.discordIntegration.EmbedBuilderUtil.buildOfflineEmbed().build()).queue();
                }
            }
        }

        if (discordBot != null) discordBot.shutdownBot();
        getLogger().info("External Server Ping Plugin disabled!");
    }

    private void loadConfigValues() {
        serverIp = getConfig().getString("server-ip", "example.com");
        serverPort = getConfig().getInt("server-port", 25565);
        updateInterval = getConfig().getInt("update-interval", 30);
        debug = getConfig().getBoolean("debug", false);
    }

    public void startPinging(String ip, int port, int interval) {
        if (pingTask != null) pingTask.cancel();

        pingTask = new BukkitRunnable() {
            @Override
            public void run() {
                long start = System.currentTimeMillis();
                try (Socket socket = new Socket()) {
                    if (debug) getLogger().info("[ExternalServerPing] Connecting to " + ip + ":" + port);
                    socket.connect(new InetSocketAddress(ip, port), 7000);

                    OutputStream out = socket.getOutputStream();
                    InputStream in = socket.getInputStream();

                    ByteArrayOutputStream handshake_bytes = new ByteArrayOutputStream();
                    DataOutputStream handshake = new DataOutputStream(handshake_bytes);

                    handshake.writeByte(0x00); // packet id
                    writeVarInt(handshake, 754); // protocol version
                    writeVarInt(handshake, ip.length());
                    handshake.writeBytes(ip);
                    handshake.writeShort(port);
                    writeVarInt(handshake, 1); // next state: status

                    writeVarInt(out, handshake_bytes.size());
                    out.write(handshake_bytes.toByteArray());

                    out.write(0x01); // size of packet
                    out.write(0x00); // packet id for request

                    readVarInt(in);
                    int packetId = readVarInt(in);
                    if (packetId != 0x00) throw new IOException("Invalid packet ID");

                    int stringLength = readVarInt(in);
                    byte[] data = new byte[stringLength];
                    int bytesRead = 0;
                    while (bytesRead < data.length) {
                        int result = in.read(data, bytesRead, data.length - bytesRead);
                        if (result == -1) throw new IOException("Unexpected end of stream while reading data.");
                        bytesRead += result;
                    }

                    String json = new String(data);
                    JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();

                    onlinePlayers = jsonObject.getAsJsonObject("players").get("online").getAsInt();
                    maxPlayers = jsonObject.getAsJsonObject("players").get("max").getAsInt();
                    motd = jsonObject.get("description").isJsonPrimitive() ?
                            jsonObject.get("description").getAsString() :
                            flattenMotd(jsonObject.getAsJsonObject("description"));

                    ping = (int) (System.currentTimeMillis() - start);
                    serverOnline = true;

                    if (debug) getLogger().info("[ExternalServerPing] Success: online=" + onlinePlayers + ", max=" + maxPlayers + ", motd=" + motd + ", ping=" + ping + "ms");

                } catch (Exception e) {
                    onlinePlayers = 0;
                    motd = "Unavailable";
                    ping = -1;
                    serverOnline = false;
                    if (debug) getLogger().log(Level.WARNING, "[ExternalServerPing] Ping failed", e);
                }
            }
        }.runTaskTimerAsynchronously(this, 0L, 20L * interval);
    }

    private String flattenMotd(JsonObject obj) {
        StringBuilder sb = new StringBuilder();
        if (obj.has("text")) sb.append(obj.get("text").getAsString());
        if (obj.has("extra")) {
            for (JsonElement element : obj.getAsJsonArray("extra")) {
                if (element.isJsonObject()) sb.append(flattenMotd(element.getAsJsonObject()));
                else if (element.isJsonPrimitive()) sb.append(element.getAsString());
            }
        }
        return sb.toString();
    }

    private static void writeVarInt(OutputStream out, int value) throws IOException {
        while ((value & 0xFFFFFF80) != 0L) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value & 0x7F);
    }

    private static int readVarInt(InputStream in) throws IOException {
        int numRead = 0, result = 0;
        byte read;
        do {
            read = (byte) in.read();
            int value = read & 0b01111111;
            result |= (value << (7 * numRead));
            numRead++;
            if (numRead > 5) throw new IOException("VarInt too big");
        } while ((read & 0b10000000) != 0);
        return result;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            loadConfigValues();
            startPinging(serverIp, serverPort, updateInterval);

            if (discordBot != null) discordBot.shutdownBot();
            DiscordConfig discordConfig = new DiscordConfig(getConfig());
            discordBot = new DiscordBotManager(this, discordConfig);
            try {
                discordBot.startBot();
                if (discordUpdater != null) discordUpdater.stopUpdater();
                discordUpdater = new ServerStatusUpdater(this, discordBot, this);
                discordUpdater.startUpdater(updateInterval);
                sender.sendMessage("§a[ExternalServerPing] Configuration reloaded and Discord bot restarted successfully!");
            } catch (IllegalArgumentException e) {
                sender.sendMessage("§c[ExternalServerPing] Failed to restart Discord bot: " + e.getMessage());
                getLogger().log(Level.WARNING, "[Discord] Failed to restart bot", e);
            }
            return true;
        }
        sender.sendMessage("§eUsage: /externalserver reload");
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        return args.length == 1 ? Collections.singletonList("reload") : Collections.emptyList();
    }

    public class ExternalServerPingPlaceholder extends PlaceholderExpansion {
        @Override public boolean canRegister() { return true; }
        @Override public boolean persist() { return true; }
        @Override public @NotNull String getIdentifier() { return "externalserver"; }
        @Override public @NotNull String getAuthor() { return "BakedPotato"; }
        @Override public @NotNull String getVersion() { return "2.0-BETA"; }
        @Override
        public String onPlaceholderRequest(Player player, String identifier) {
            return switch (identifier.toLowerCase()) {
                case "online" -> String.valueOf(onlinePlayers);
                case "max" -> String.valueOf(maxPlayers);
                case "motd" -> motd;
                case "ping" -> ping >= 0 ? String.valueOf(ping) : "Unavailable";
                case "status" -> serverOnline ? "Online" : "Offline";
                default -> null;
            };
        }
    }

    // Getters
    public int getOnlinePlayers() { return onlinePlayers; }
    public int getMaxPlayers() { return maxPlayers; }
    public String getMotd() { return motd; }
    public int getPing() { return ping; }
    public boolean isServerOnline() { return serverOnline; }
}
