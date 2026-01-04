package jfbdev.jgmlogger;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.query.QueryOptions;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.minecart.HopperMinecart;
import org.bukkit.entity.minecart.StorageMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import javax.annotation.Nonnull;
import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class JGMLogger extends JavaPlugin implements Listener, CommandExecutor {

    private NamespacedKey creativeKey;
    private final Map<UUID, DropInfo> pendingCreativeDrops = new ConcurrentHashMap<>();
    private final Map<UUID, String> primaryGroups = new ConcurrentHashMap<>();
    private LuckPerms lpApi;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    @Override
    public void onEnable() {
        saveDefaultConfig();
        creativeKey = new NamespacedKey(this, "creative_origin");
        getServer().getPluginManager().registerEvents(this, this);

        var cmd = getCommand("gmlreload");
        if (cmd != null) {
            cmd.setExecutor(this);
        }

        var lpPlugin = getServer().getPluginManager().getPlugin("LuckPerms");
        if (lpPlugin != null && lpPlugin.isEnabled()) {
            lpApi = LuckPermsProvider.get();
            getLogger().info("LuckPerms API успешно подключен!");
        } else {
            getLogger().warning("LuckPerms не найден! Логирование по группам отключено.");
        }

        getLogger().info("JGMLogger загружен! (Paper 1.16.5 - 1.21.11, Folia supported)");
    }

    @Override
    public void onDisable() {
        primaryGroups.clear();
    }

    @Override
    public boolean onCommand(@Nonnull CommandSender sender, @Nonnull Command command, @Nonnull String label, @Nonnull String[] args) {
        if (!command.getName().equalsIgnoreCase("gmlreload")) return false;

        if (!sender.hasPermission("jgmlogger.admin")) {
            sender.sendMessage(getConfig().getString("messages.no-permission", "&cНедостаточно прав").replace("&", "§"));
            return true;
        }

        primaryGroups.clear();
        reloadConfig();
        sender.sendMessage(getConfig().getString("messages.reload", "&fПлагин успешно &aперезагружен").replace("&", "§"));
        getLogger().info("JGMLogger конфиг перезагружен!");
        return true;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (lpApi == null) return;

        lpApi.getUserManager().loadUser(player.getUniqueId()).thenAccept(user -> {
            if (user != null) {
                primaryGroups.put(player.getUniqueId(), user.getPrimaryGroup());
            }
        });
    }

    private boolean shouldLog(Player player) {
        List<String> logPlayers = getConfig().getStringList("log-settings.log-players");
        if (logPlayers.stream().anyMatch(name -> name.equalsIgnoreCase(player.getName()))) {
            return true;
        }

        List<String> logGroups = getConfig().getStringList("log-settings.log-groups");
        if (logGroups.isEmpty() || lpApi == null) {
            return getConfig().getBoolean("log-settings.log-all-players", false);
        }

        String cachedGroup = primaryGroups.get(player.getUniqueId());
        if (cachedGroup != null) {
            String lowerCached = cachedGroup.toLowerCase(java.util.Locale.ROOT);
            if (logGroups.stream().map(g -> g.trim().toLowerCase(java.util.Locale.ROOT)).anyMatch(lowerCached::equals)) {
                return true;
            }
        }

        User user = lpApi.getUserManager().loadUser(player.getUniqueId()).join();
        if (user != null) {
            QueryOptions queryOptions = lpApi.getContextManager()
                    .getQueryOptions(user)
                    .orElse(lpApi.getContextManager().getStaticQueryOptions());

            for (Group group : user.getInheritedGroups(queryOptions)) {
                String groupName = group.getName().toLowerCase(java.util.Locale.ROOT);
                if (logGroups.stream()
                        .map(g -> g.trim().toLowerCase(java.util.Locale.ROOT))
                        .anyMatch(groupName::equals)) {
                    return true;
                }
            }
        }

        return getConfig().getBoolean("log-settings.log-all-players", false);
    }

    @EventHandler
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        if (shouldLog(player)) {
            logAction(player, "gamemode-change", Map.of("gamemode", event.getNewGameMode().name()));
        }
    }

    @EventHandler
    public void onCreativeTake(InventoryCreativeEvent event) {
        Player player = (Player) event.getWhoClicked();
        if (!shouldLog(player)) return;

        ItemStack cursor = event.getCursor();
        if (cursor.getType() == Material.AIR) return;

        ItemMeta meta = cursor.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(creativeKey, PersistentDataType.BYTE, (byte) 1);
            cursor.setItemMeta(meta);
        }

        logAction(player, "creative-take",
                Map.of("amount", String.valueOf(cursor.getAmount()), "material", cursor.getType().name()));
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (!shouldLog(player)) return;

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (event.isCancelled() || !hasCreativeOrigin(hand)) return;

        Location loc = event.getBlockPlaced().getLocation();
        logAction(player, "block-place",
                Map.of("material", event.getBlockPlaced().getType().name(),
                        "x", String.valueOf(loc.getBlockX()),
                        "y", String.valueOf(loc.getBlockY()),
                        "z", String.valueOf(loc.getBlockZ())));
    }

    @EventHandler
    public void onInvClick(InventoryClickEvent event) {
        if (event.isCancelled() || event.getSlotType() != InventoryType.SlotType.CONTAINER) return;

        Player player = (Player) event.getWhoClicked();
        ItemStack cursor = event.getCursor();
        if (!hasCreativeOrigin(cursor)) return;

        var clickedInv = event.getClickedInventory();
        if (clickedInv == null) return;

        if (clickedInv.getType() == InventoryType.PLAYER) return;

        InventoryHolder holder = clickedInv.getHolder();
        String containerType = clickedInv.getType().name();

        if (holder instanceof HopperMinecart) {
            containerType = "HOPPER_MINECART";
        } else if (holder instanceof StorageMinecart) {
            containerType = "CHEST_MINECART";
        }

        Location loc = null;
        if (holder instanceof BlockState bs) {
            loc = bs.getLocation();
        } else if (holder instanceof Entity entity) {
            loc = entity.getLocation();
        }

        String coordsFull = loc != null ? loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ() : "";

        int placedAmt = cursor.getAmount();
        if (event.getAction() == InventoryAction.PLACE_ONE) {
            placedAmt = 1;
        }

        logAction(player, "container-place",
                Map.of("amount", String.valueOf(placedAmt),
                        "material", cursor.getType().name(),
                        "container", containerType,
                        "coords", coordsFull));
    }

    @EventHandler
    public void onPlayerDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItemDrop().getItemStack();
        if (hasCreativeOrigin(item) && shouldLog(player)) {
            UUID dropUid = event.getItemDrop().getUniqueId();
            pendingCreativeDrops.put(dropUid, new DropInfo(player.getUniqueId(), item.getType(), item.getAmount()));
        }
    }

    @EventHandler
    public void onItemPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        var itemEntity = event.getItem();
        UUID uid = itemEntity.getUniqueId();
        DropInfo info = pendingCreativeDrops.remove(uid);
        if (info == null) return;

        ItemStack picked = itemEntity.getItemStack();
        String amtStr = String.valueOf(picked.getAmount());
        String matStr = info.mat.name();
        String dropperName = Bukkit.getOfflinePlayer(info.dropperUuid).getName();

        Player dropperPlayer = Bukkit.getPlayer(info.dropperUuid);
        if (dropperPlayer != null && shouldLog(dropperPlayer)) {
            logAction(dropperPlayer, "dropper-sent",
                    Map.of("receiver", player.getName(), "amount", amtStr, "material", matStr));
        }

        if (shouldLog(player)) {
            logAction(player, "receiver-got",
                    Map.of("sender", dropperName != null ? dropperName : "Неизвестный",
                            "amount", amtStr, "material", matStr));
        }
    }

    private boolean hasCreativeOrigin(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(creativeKey, PersistentDataType.BYTE);
    }

    private void logAction(Player player, String logKey, Map<String, String> placeholders) {
        if (!getConfig().getBoolean("log." + logKey, true)) return;

        String msgTemplate = getConfig().getString("log-messages-templates." + logKey);
        if (msgTemplate == null || msgTemplate.isEmpty()) return;

        String msg = msgTemplate;
        for (var entry : placeholders.entrySet()) {
            msg = msg.replace("%" + entry.getKey() + "%", entry.getValue());
        }

        String dateStr = LocalDate.now(ZoneId.systemDefault()).format(DATE_FORMAT);
        File logsDir = new File(getDataFolder(), "logs");

        if (!logsDir.exists() && !logsDir.mkdirs()) {
            getLogger().warning("Не удалось создать папку логов: " + logsDir.getPath());
        }

        File logFile = new File(logsDir, dateStr + "-" + player.getName() + ".log");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(logFile);

        String timeStr = LocalDateTime.now(ZoneId.systemDefault()).format(TIME_FORMAT);
        List<String> actions = config.getStringList(timeStr);
        actions.add(msg);
        config.set(timeStr, actions);

        try {
            config.save(logFile);
        } catch (Exception e) {
            getLogger().warning("Ошибка сохранения лога для " + player.getName() + ": " + e.getMessage());
        }
    }

    private record DropInfo(UUID dropperUuid, Material mat, int amount) {}
}