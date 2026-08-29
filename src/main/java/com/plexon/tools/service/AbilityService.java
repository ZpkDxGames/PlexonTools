package com.plexon.tools.service;

import com.plexon.tools.config.ToolConfigRepository;
import com.plexon.tools.item.ToolItemService;
import com.plexon.tools.item.ToolState;
import com.plexon.tools.model.AbilityTarget;
import com.plexon.tools.model.ToolAbilitySettings;
import com.plexon.tools.model.ToolAbilityType;
import com.plexon.tools.model.ToolDefinition;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class AbilityService implements Listener {
    private static final Set<Material> UNBREAKABLE_AREA_BLOCKS = Set.of(
            Material.BEDROCK,
            Material.BARRIER,
            Material.END_PORTAL,
            Material.END_PORTAL_FRAME,
            Material.NETHER_PORTAL,
            Material.COMMAND_BLOCK,
            Material.CHAIN_COMMAND_BLOCK,
            Material.REPEATING_COMMAND_BLOCK,
            Material.STRUCTURE_BLOCK,
            Material.JIGSAW,
            Material.LIGHT
    );
    private static final Map<Material, Material> SMELTED_DROPS = Map.ofEntries(
            Map.entry(Material.RAW_IRON, Material.IRON_INGOT),
            Map.entry(Material.IRON_ORE, Material.IRON_INGOT),
            Map.entry(Material.DEEPSLATE_IRON_ORE, Material.IRON_INGOT),
            Map.entry(Material.RAW_IRON_BLOCK, Material.IRON_BLOCK),
            Map.entry(Material.RAW_GOLD, Material.GOLD_INGOT),
            Map.entry(Material.GOLD_ORE, Material.GOLD_INGOT),
            Map.entry(Material.DEEPSLATE_GOLD_ORE, Material.GOLD_INGOT),
            Map.entry(Material.NETHER_GOLD_ORE, Material.GOLD_INGOT),
            Map.entry(Material.RAW_GOLD_BLOCK, Material.GOLD_BLOCK),
            Map.entry(Material.RAW_COPPER, Material.COPPER_INGOT),
            Map.entry(Material.COPPER_ORE, Material.COPPER_INGOT),
            Map.entry(Material.DEEPSLATE_COPPER_ORE, Material.COPPER_INGOT),
            Map.entry(Material.RAW_COPPER_BLOCK, Material.COPPER_BLOCK),
            Map.entry(Material.ANCIENT_DEBRIS, Material.NETHERITE_SCRAP)
    );

    private final JavaPlugin plugin;
    private final ToolConfigRepository tools;
    private final ToolItemService itemService;
    private final ProgressionService progression;
    private final Set<UUID> areaMiningPlayers = new HashSet<>();
    private BukkitTask passiveTask;

    public AbilityService(
            JavaPlugin plugin,
            ToolConfigRepository tools,
            ToolItemService itemService,
            ProgressionService progression
    ) {
        this.plugin = plugin;
        this.tools = tools;
        this.itemService = itemService;
        this.progression = progression;
    }

    public void start() {
        stop();
        passiveTask = Bukkit.getScheduler().runTaskTimer(plugin,
                this::refreshPassiveEffects, 20L, 40L);
    }

    public void stop() {
        if (passiveTask != null) {
            passiveTask.cancel();
            passiveTask = null;
        }
        areaMiningPlayers.clear();
    }

    public boolean isAreaMining(Player player) {
        return areaMiningPlayers.contains(player.getUniqueId());
    }

    public void boostBlockExperience(BlockBreakEvent event, ToolDefinition definition, ToolState state) {
        event.setExpToDrop(boostedExperience(event.getExpToDrop(), definition, state));
    }

    public void handleDeath(EntityDeathEvent event, Player player, ToolDefinition definition, ToolState state) {
        event.setDroppedExp(boostedExperience(event.getDroppedExp(), definition, state));
        if (hasAbility(definition, state, ToolAbilityType.MAGNET)) {
            magnetDrops(player, event.getDrops());
        }
    }

    public void handleFishing(PlayerFishEvent event, ToolDefinition definition, ToolState state) {
        event.setExpToDrop(boostedExperience(event.getExpToDrop(), definition, state));
        if (hasAbility(definition, state, ToolAbilityType.MAGNET)
                && event.getCaught() instanceof Item caught) {
            magnetEntity(event.getPlayer(), caught);
        }
    }

    public void handleHarvest(
            PlayerHarvestBlockEvent event,
            ToolDefinition definition,
            ToolState state
    ) {
        if (!hasAbility(definition, state, ToolAbilityType.MAGNET)) {
            return;
        }
        List<ItemStack> harvested = event.getItemsHarvested();
        List<ItemStack> leftovers = new ArrayList<>();
        for (ItemStack item : harvested) {
            leftovers.addAll(event.getPlayer().getInventory().addItem(item).values());
        }
        harvested.clear();
        harvested.addAll(leftovers);
    }

    public void applyHitEffect(
            Player player,
            LivingEntity target,
            ToolDefinition definition,
            ToolState state
    ) {
        ability(definition, state, ToolAbilityType.MOB_POTION_EFFECT).ifPresent(settings -> {
            if (settings.potionTarget() == AbilityTarget.TARGET) {
                applyPotion(target, settings);
            } else {
                applyPotion(player, settings);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockDrops(BlockDropItemEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        ToolState state = itemService.read(item).orElse(null);
        ToolDefinition definition = state == null ? null : tools.find(state.toolId()).orElse(null);
        if (definition == null || !definition.enabled()
                || !progression.canUse(player, definition, state, false)) {
            return;
        }

        boolean autoSmelt = hasAbility(definition, state, ToolAbilityType.AUTO_SMELT);
        boolean magnet = hasAbility(definition, state, ToolAbilityType.MAGNET);
        for (Item drop : new ArrayList<>(event.getItems())) {
            if (autoSmelt) {
                drop.setItemStack(smelt(drop.getItemStack()));
            }
            if (magnet) {
                magnetEntity(player, drop);
            }
        }
    }

    public void mineArea(BlockBreakEvent original, ToolDefinition definition, ToolState state) {
        Player player = original.getPlayer();
        if (isAreaMining(player)
                || !hasAbility(definition, state, ToolAbilityType.AREA_MINE_3X3)
                || !isAreaTool(player.getInventory().getItemInMainHand().getType())) {
            return;
        }
        ItemStack tool = player.getInventory().getItemInMainHand();
        List<Block> targets = areaPlane(original.getBlock(), player.getEyeLocation().getDirection());
        areaMiningPlayers.add(player.getUniqueId());
        try {
            for (Block block : targets) {
                if (!canAreaBreak(block, tool)) {
                    continue;
                }
                BlockBreakEvent extra = new BlockBreakEvent(block, player);
                Bukkit.getPluginManager().callEvent(extra);
                if (extra.isCancelled() || block.getType().isAir()) {
                    continue;
                }

                ItemStack currentTool = player.getInventory().getItemInMainHand();
                ToolState currentState = itemService.read(currentTool).orElse(state);
                boolean autoSmelt = hasAbility(definition, currentState, ToolAbilityType.AUTO_SMELT);
                boolean magnet = hasAbility(definition, currentState, ToolAbilityType.MAGNET);
                List<ItemStack> drops = extra.isDropItems()
                        ? block.getDrops(currentTool, player).stream().map(ItemStack::clone).toList()
                        : List.of();
                block.setType(Material.AIR, true);
                for (ItemStack drop : drops) {
                    ItemStack result = autoSmelt ? smelt(drop) : drop;
                    if (magnet) {
                        deliver(player, result);
                    } else {
                        block.getWorld().dropItemNaturally(block.getLocation(), result);
                    }
                }
                if (extra.getExpToDrop() > 0) {
                    player.giveExp(extra.getExpToDrop());
                }
            }
        } finally {
            areaMiningPlayers.remove(player.getUniqueId());
        }
    }

    private void refreshPassiveEffects() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            ItemStack item = player.getInventory().getItemInMainHand();
            ToolState state = itemService.read(item).orElse(null);
            ToolDefinition definition = state == null ? null : tools.find(state.toolId()).orElse(null);
            if (definition == null || !definition.enabled()
                    || !progression.canUse(player, definition, state, false)) {
                continue;
            }
            ability(definition, state, ToolAbilityType.MOB_POTION_EFFECT)
                    .filter(settings -> settings.potionTarget() == AbilityTarget.HOLDER)
                    .ifPresent(settings -> applyPotion(player, settings));
        }
    }

    private static void applyPotion(LivingEntity entity, ToolAbilitySettings settings) {
        NamespacedKey key = NamespacedKey.fromString(settings.potionEffect());
        PotionEffectType effect = key == null ? null : Registry.MOB_EFFECT.get(key);
        if (effect == null) {
            return;
        }
        entity.addPotionEffect(new PotionEffect(effect, settings.durationTicks(),
                settings.potionLevel() - 1, true, false, true));
    }

    private static int boostedExperience(int base, ToolDefinition definition, ToolState state) {
        ToolAbilitySettings settings = ability(definition, state, ToolAbilityType.EXP_BOOSTER)
                .orElse(null);
        if (settings == null || base <= 0) {
            return Math.max(0, base);
        }
        long boosted = Math.round(base * settings.multiplier());
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, boosted));
    }

    private static boolean hasAbility(
            ToolDefinition definition,
            ToolState state,
            ToolAbilityType type
    ) {
        return ability(definition, state, type).isPresent();
    }

    private static Optional<ToolAbilitySettings> ability(
            ToolDefinition definition,
            ToolState state,
            ToolAbilityType type
    ) {
        return definition.level(state.level()).map(level -> level.abilities().get(type));
    }

    private static ItemStack smelt(ItemStack source) {
        Material material = SMELTED_DROPS.get(source.getType());
        if (material == null) {
            return source;
        }
        return new ItemStack(material, source.getAmount());
    }

    private static void magnetDrops(Player player, List<ItemStack> drops) {
        List<ItemStack> leftovers = new ArrayList<>();
        for (ItemStack drop : new ArrayList<>(drops)) {
            leftovers.addAll(player.getInventory().addItem(drop).values());
        }
        drops.clear();
        drops.addAll(leftovers);
    }

    private static void magnetEntity(Player player, Item item) {
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item.getItemStack());
        if (leftovers.isEmpty()) {
            item.remove();
            return;
        }
        item.setItemStack(leftovers.values().iterator().next());
    }

    private static void deliver(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        leftovers.values().forEach(leftover ->
                player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }

    private static boolean canAreaBreak(Block block, ItemStack tool) {
        Material type = block.getType();
        return !type.isAir()
                && type.isBlock()
                && !UNBREAKABLE_AREA_BLOCKS.contains(type)
                && !(block.getState() instanceof InventoryHolder)
                && block.getDestroySpeed(tool, true) > 0.0F;
    }

    private static boolean isAreaTool(Material material) {
        String name = material.name();
        return name.endsWith("_PICKAXE")
                || name.endsWith("_SHOVEL")
                || name.endsWith("_AXE");
    }

    private static List<Block> areaPlane(Block origin, Vector direction) {
        double x = Math.abs(direction.getX());
        double y = Math.abs(direction.getY());
        double z = Math.abs(direction.getZ());
        List<Block> blocks = new ArrayList<>(8);
        for (int first = -1; first <= 1; first++) {
            for (int second = -1; second <= 1; second++) {
                if (first == 0 && second == 0) {
                    continue;
                }
                if (y >= x && y >= z) {
                    blocks.add(origin.getRelative(first, 0, second));
                } else if (x >= z) {
                    blocks.add(origin.getRelative(0, first, second));
                } else {
                    blocks.add(origin.getRelative(first, second, 0));
                }
            }
        }
        return blocks;
    }
}
