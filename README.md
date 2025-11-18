# Please read before you proceed
This is the official *ESP* GitHub Repository. Any other Creators pretending to be me or the original creator are **NOT TRUSTED**. The official "Webpage" is on Modrinth: https://modrinth.com/plugin/ext-server-ping

---

## External Server Ping
Easily check the status and the amount of players of external Minecraft servers directly from your server using PlaceholderAPI!

**External Server Ping** allows you to ping _any_ server address and port to display its online status dynamically in your server using placeholders. Perfect for network communities, event servers, or small setups that want to show whether another server is online, without requiring your players to guess or ask.

**🔨 Features**
- Ping any external server (IP + port) and get live status
- Integrates fully with PlaceholderAPI
- Provides a global placeholder:
1. %externalserver_online% – displays the amount of online players
2. %externalserver_max% - displays the maximum amount of players that can join
3. %externalserver_status% - displays the server status (Online and Offline, these explain themselves, don't they?)
4. %externalserver_motd% - the motd aka. the server's _description_
5. %externalserver_ping% - tries to ping the server... from the server that is using the plugin (don't know how that could help, but oh well, it's something)
- Lightweight and efficient ping system with minimal impact on your server
- Useful for displaying external hub, event, or partner server status on signs, scoreboards, tab, or chat

**⚠️ Requirements**
- PlaceholderAPI (required)
- Minecraft Paper/Purpur/Bukkit/Spigot server (1.21+) - note that Spigot and Bukkit have not been tested yet!

**❓ Example Use Cases**
- Show in your scoreboard how many players the event server has (in total)
- Use in Discord integration bots to forward live server status ***(IN EXPERIMENTAL BRANCH)***
- Automate event announcements when the server hits a new maximum player count

**🔧 Configuration**
Setup is straightforward:
- Install PlaceholderAPI
- Install External Server Ping
- Use %externalserver_online% anywhere PlaceholderAPI is supported
- Configure the target server address, port, discord bot token, bot channel- and bot message-ID in config.yml

**👉 Why use External Server Ping?**
This plugin keeps your players informed and enhances your community’s network experience without complex setups or heavy server polling.

**📍 Download & License**
External Server Ping is free and open-source, licensed under CC BY 4.0.
You are free to modify, adapt, and share, credit required. | Download (latest): https://modrinth.com/plugin/ext-server-ping/version/CNFriB4u