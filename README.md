# MorningStarBridge

Paper plugin connecting Morning Star SMP to its Discord status channel.

## Features
- Live server status and online player names/count
- All-time unique player history from installation onward
- Online staff list (operators or configured permission)
- Owner mention and support ticket link
- Discord member count (requires Server Members Intent)
- One status embed edited every 30 seconds
- Player history stored in plugins/MorningStarBridge/players.yml

## Build
GitHub Actions builds on pushes to main/master. Open the repository's Actions tab, select the latest successful Build MorningStarBridge run, and download the MorningStarBridge artifact ZIP. Extract it to obtain MorningStarBridge-1.0.0.jar.

## Install
1. Back up the server and stop it.
2. Upload the JAR into the server's plugins folder.
3. Start the server once to generate plugins/MorningStarBridge/config.yml.
4. Stop the server and edit config.yml. Set discord.token and discord.guild-id. Never share or commit your real bot token.
5. Confirm the configured status channel, owner ID and ticket channel ID.
6. Enable Server Members Intent in the Discord Developer Portal.
7. Give the bot View Channel, Send Messages, Embed Links and Read Message History permissions in the status channel.
8. Start the server.

The plugin creates its status embed on first successful connection and saves its message ID to config.yml.

## Command
- /msbridge reload — reload config.yml and refresh the embed.

## Important notes
- Player history begins when this plugin is installed; it cannot reconstruct earlier joins.
- The all-time list shows the most recently seen unique names, up to the configured limit.
- Hosting text is informational only. Live Pterodactyl CPU/RAM/disk usage needs a separately configured panel API integration.
- Requires Java 25 and Paper 26.3.
