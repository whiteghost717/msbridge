package net.morningstar.bridge;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public final class MorningStarBridge extends JavaPlugin implements Listener {
    private JDA jda;
    private BukkitTask updater;
    private File playersFile;
    private YamlConfiguration playersData;
    private final AtomicBoolean updating = new AtomicBoolean(false);

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadPlayerData();
        getServer().getPluginManager().registerEvents(this, this);
        startDiscord();
        startUpdater();
        getLogger().info("MorningStarBridge enabled.");
    }

    @Override
    public void onDisable() {
        if (updater != null) updater.cancel();
        if (jda != null) {
            jda.shutdown();
            jda = null;
        }
        savePlayerData();
    }

    private void loadPlayerData() {
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        playersFile = new File(getDataFolder(), "players.yml");
        playersData = YamlConfiguration.loadConfiguration(playersFile);
        if (!playersData.isConfigurationSection("players")) {
            playersData.createSection("players");
            savePlayerData();
        }
    }

    private void savePlayerData() {
        if (playersData == null || playersFile == null) return;
        try {
            playersData.save(playersFile);
        } catch (IOException ex) {
            getLogger().severe("Could not save players.yml: " + ex.getMessage());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String path = "players." + player.getUniqueId();
        playersData.set(path + ".name", player.getName());
        playersData.set(path + ".last-seen", System.currentTimeMillis());
        savePlayerData();
        refreshSoon();
    }

    private void startDiscord() {
        String token = getConfig().getString("discord.token", "");
        if (token.isBlank() || token.equals("PUT_BOT_TOKEN_HERE")) {
            getLogger().warning("Set discord.token in plugins/MorningStarBridge/config.yml.");
            return;
        }
        try {
            jda = JDABuilder.createDefault(token)
                    .enableIntents(GatewayIntent.GUILD_MEMBERS)
                    .setMemberCachePolicy(MemberCachePolicy.ALL)
                    .addEventListeners(new ListenerAdapter() {
                        @Override
                        public void onReady(ReadyEvent event) {
                            getLogger().info("Discord bot connected as " + event.getJDA().getSelfUser().getName());
                            refreshSoon();
                        }
                    })
                    .build();
        } catch (Exception ex) {
            getLogger().severe("Could not start Discord bot: " + ex.getMessage());
        }
    }

    private void startUpdater() {
        long seconds = Math.max(15L, getConfig().getLong("discord.update-seconds", 30L));
        updater = Bukkit.getScheduler().runTaskTimer(this, this::updateStatus, 40L, seconds * 20L);
    }

    private void refreshSoon() {
        Bukkit.getScheduler().runTask(this, this::updateStatus);
    }

    private void updateStatus() {
        if (jda == null || !updating.compareAndSet(false, true)) return;
        String guildId = getConfig().getString("discord.guild-id", "");
        String channelId = getConfig().getString("discord.status-channel-id", "");
        Guild guild = jda.getGuildById(guildId);
        TextChannel channel = jda.getTextChannelById(channelId);
        if (guild == null || channel == null) {
            updating.set(false);
            return;
        }

        List<String> online = Bukkit.getOnlinePlayers().stream()
                .map(Player::getName).sorted(String.CASE_INSENSITIVE_ORDER).toList();
        List<String> staff = Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.isOp() || p.hasPermission(getConfig().getString("server.staff-permission", "morningstarbridge.admin")))
                .map(Player::getName).sorted(String.CASE_INSENSITIVE_ORDER).toList();

        List<Map<?, ?>> records = new ArrayList<>();
        if (playersData.getConfigurationSection("players") != null) {
            for (String key : playersData.getConfigurationSection("players").getKeys(false)) {
                String name = playersData.getString("players." + key + ".name", "Unknown");
                long seen = playersData.getLong("players." + key + ".last-seen", 0L);
                records.add(Map.of("name", name, "seen", seen));
            }
        }
        records.sort(Comparator.comparingLong((Map<?, ?> m) -> (long) m.get("seen")).reversed());
        int limit = Math.max(1, getConfig().getInt("players.all-time-display-limit", 10));
        String allTime = records.stream().limit(limit).map(m -> (String) m.get("name")).collect(Collectors.joining(", "));
        if (allTime.isBlank()) allTime = "No players recorded yet";

        int maxPlayers = getConfig().getInt("server.max-players-override", Bukkit.getMaxPlayers());
        String address = getConfig().getString("server.address", "Not configured");
        String ownerId = getConfig().getString("server.owner-user-id", "");
        String ticketId = getConfig().getString("server.ticket-channel-id", "");
        String resources = getConfig().getString("server.resources-display", "Not configured");
        int memberCount = guild.getMemberCount();

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("✦ MORNING STAR SMP — LIVE STATUS")
                .setColor(0xB78AFF)
                .setDescription("**" + getConfig().getString("server.display-name", "MORNING STAR SMP") + "**")
                .addField("Server", "🟢 Online", true)
                .addField("Players", online.size() + "/" + maxPlayers, true)
                .addField("Discord Members", String.valueOf(memberCount), true)
                .addField("Online Now", online.isEmpty() ? "Nobody online" : String.join(", ", online), false)
                .addField("Staff Online", staff.isEmpty() ? "No staff online" : String.join(", ", staff), false)
                .addField("All-time Unique Players (" + records.size() + ")", allTime, false)
                .addField("Server Address", "`" + address + "`", false)
                .addField("Hosting", resources + " (allocation label; not live usage)", false)
                .setFooter("Updates every " + getConfig().getLong("discord.update-seconds", 30L) + " seconds")
                .setTimestamp(Instant.now());

        if (!ownerId.isBlank()) embed.addField("Owner", "<@" + ownerId + ">", true);
        if (!ticketId.isBlank()) embed.addField("Support", "[Open a ticket](https://discord.com/channels/" + guild.getId() + "/" + ticketId + ")", true);
        MessageEmbed messageEmbed = embed.build();

        String messageId = getConfig().getString("discord.status-message-id", "");
        if (messageId == null || messageId.isBlank()) {
            channel.sendMessageEmbeds(messageEmbed).queue(message -> {
                Bukkit.getScheduler().runTask(this, () -> {
                    getConfig().set("discord.status-message-id", message.getId());
                    saveConfig();
                    updating.set(false);
                });
            }, error -> {
                getLogger().warning("Could not send status embed: " + error.getMessage());
                updating.set(false);
            });
        } else {
            channel.editMessageEmbedsById(messageId, messageEmbed).queue(
                    success -> updating.set(false),
                    error -> {
                        getLogger().warning("Could not edit status embed; check message ID and channel permissions: " + error.getMessage());
                        updating.set(false);
                    });
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("msbridge")) return false;
        if (!sender.hasPermission("morningstarbridge.admin")) {
            sender.sendMessage("You do not have permission.");
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            sender.sendMessage("MorningStarBridge config reloaded.");
            updateStatus();
            return true;
        }
        sender.sendMessage("Usage: /msbridge reload");
        return true;
    }
}
