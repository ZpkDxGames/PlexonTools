package com.plexon.tools.listener;

import com.plexon.tools.config.PluginSettings;
import com.plexon.tools.config.ToolConfigRepository;
import com.plexon.tools.item.ToolItemService;
import com.plexon.tools.item.ToolState;
import com.plexon.tools.model.ToolDefinition;
import com.plexon.tools.model.TrackingType;
import com.plexon.tools.service.AbilityService;
import com.plexon.tools.service.ProgressionService;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

public final class ToolProgressListener implements Listener {
    private static final Set<Material> FARM_TARGETS = Set.of(
            Material.WHEAT,
            Material.CARROTS,
            Material.POTATOES,
            Material.BEETROOTS,
            Material.NETHER_WART,
            Material.COCOA,
            Material.SWEET_BERRY_BUSH,
            Material.MELON,
            Material.PUMPKIN,
            Material.SUGAR_CANE,
            Material.CACTUS,
            Material.BAMBOO,
            Material.KELP
    );
    private static final Set<Material> FISH_TARGETS = Set.of(
            Material.COD,
            Material.SALMON,
            Material.TROPICAL_FISH,
            Material.PUFFERFISH
    );
    private final ToolConfigRepository tools;
    private final ToolItemService itemService;
    private final ProgressionService progression;
    private final AbilityService abilities;
    private final PluginSettings settings;

    public ToolProgressListener(
            ToolConfigRepository tools,
            ToolItemService itemService,
            ProgressionService progression,
            AbilityService abilities,
            PluginSettings settings
    ) {
        this.tools = tools;
        this.itemService = itemService;
        this.progression = progression;
        this.abilities = abilities;
        this.settings = settings;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        ToolContext context = usable(player, item, true);
        if (context == null) {
            if (itemService.isTagged(item)) {
                event.setCancelled(settings.cancelBlockBreaks());
            }
            return;
        }

        abilities.boostBlockExperience(event, context.definition(), context.state());
        String target = blockTrackingTarget(context.definition().trackingType(), event.getBlock());
        if (target != null && context.definition().tracks(target, context.state().level())) {
            progression.addProgress(player, item, EquipmentSlot.HAND, context.definition(),
                    context.state(), target, 1L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreakAbilities(BlockBreakEvent event) {
        if (abilities.isAreaMining(event.getPlayer())) {
            return;
        }
        ItemStack item = event.getPlayer().getInventory().getItemInMainHand();
        ToolContext context = usable(event.getPlayer(), item, false);
        if (context != null) {
            abilities.mineArea(event, context.definition(), context.state());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        Player player = event.getEntity().getKiller();
        if (player == null) {
            return;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        ToolContext context = usable(player, item, false);
        if (context == null) {
            return;
        }

        abilities.handleDeath(event, player, context.definition(), context.state());
        if (context.definition().trackingType() == TrackingType.MOBS_KILLED) {
            String target = event.getEntityType().name();
            if (context.definition().tracks(target, context.state().level())) {
                progression.addProgress(player, item, EquipmentSlot.HAND,
                        context.definition(), context.state(), target, 1L);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (usable(player, item, true) == null
                && itemService.isTagged(item)
                && settings.cancelAttacks()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageResolved(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)
                || !(event.getEntity() instanceof LivingEntity targetEntity)) {
            return;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        ToolContext context = usable(player, item, false);
        if (context == null) {
            return;
        }

        abilities.applyHitEffect(player, targetEntity, context.definition(), context.state());
        if (context.definition().trackingType() != TrackingType.DAMAGE_DEALT
                || event.getFinalDamage() <= 0.0D) {
            return;
        }
        String target = event.getEntityType().name();
        if (context.definition().tracks(target, context.state().level())) {
            long amount = Math.max(1L, Math.round(event.getFinalDamage()));
            progression.addProgress(player, item, EquipmentSlot.HAND,
                    context.definition(), context.state(), target, amount);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!event.canBuild()) {
            return;
        }
        EquipmentSlot hand = event.getHand();
        ItemStack item = held(event.getPlayer(), hand);
        ToolContext context = usable(event.getPlayer(), item, false);
        if (context == null || context.definition().trackingType() != TrackingType.BLOCKS_PLACED) {
            return;
        }
        String target = event.getBlockPlaced().getType().name();
        if (context.definition().tracks(target, context.state().level())) {
            progression.addProgress(event.getPlayer(), item, hand,
                    context.definition(), context.state(), target, 1L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH
                || !(event.getCaught() instanceof Item caught)) {
            return;
        }
        EquipmentSlot hand = event.getHand() == null ? EquipmentSlot.HAND : event.getHand();
        ItemStack item = held(event.getPlayer(), hand);
        ToolContext context = usable(event.getPlayer(), item, false);
        if (context == null) {
            return;
        }

        abilities.handleFishing(event, context.definition(), context.state());
        if (context.definition().trackingType() != TrackingType.FISH_CAUGHT) {
            return;
        }
        Material caughtType = caught.getItemStack().getType();
        if (!FISH_TARGETS.contains(caughtType)) {
            return;
        }
        String target = caughtType.name();
        if (context.definition().tracks(target, context.state().level())) {
            progression.addProgress(event.getPlayer(), item, hand,
                    context.definition(), context.state(), target, 1L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHarvest(PlayerHarvestBlockEvent event) {
        EquipmentSlot hand = event.getHand();
        ItemStack item = held(event.getPlayer(), hand);
        ToolContext context = usable(event.getPlayer(), item, false);
        if (context == null) {
            return;
        }
        abilities.handleHarvest(event, context.definition(), context.state());
        if (context.definition().trackingType() != TrackingType.ITEMS_FARMED) {
            return;
        }
        Material harvestedType = event.getHarvestedBlock().getType();
        if (!FARM_TARGETS.contains(harvestedType)) {
            return;
        }
        String target = harvestedType.name();
        if (context.definition().tracks(target, context.state().level())) {
            progression.addProgress(event.getPlayer(), item, hand,
                    context.definition(), context.state(), target, 1L);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        ItemStack item = event.getItem();
        if (usable(event.getPlayer(), item, true) == null
                && itemService.isTagged(item)
                && settings.cancelInteractions()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        ItemStack item = event.getItem();
        if (usable(event.getPlayer(), item, true) == null && itemService.isTagged(item)) {
            event.setCancelled(true);
        }
    }

    private ToolContext usable(Player player, ItemStack item, boolean notify) {
        ToolState state = progression.resolveState(item).orElse(null);
        if (state == null) {
            if (notify && itemService.isTagged(item)) {
                progression.warnInvalid(player);
            }
            return null;
        }
        ToolDefinition definition = tools.find(state.toolId()).orElse(null);
        if (definition == null || !definition.enabled()) {
            if (notify) {
                progression.warnInvalid(player);
            }
            return null;
        }
        if (!progression.canUse(player, definition, state, notify)) {
            return null;
        }
        return new ToolContext(state, definition);
    }

    private static ItemStack held(Player player, EquipmentSlot hand) {
        return hand == EquipmentSlot.OFF_HAND
                ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();
    }

    private static String blockTrackingTarget(TrackingType type, Block block) {
        if (type == TrackingType.BLOCKS_BROKEN) {
            return block.getType().name();
        }
        if (type == TrackingType.ITEMS_FARMED && isHarvestable(block)) {
            return block.getType().name();
        }
        return null;
    }

    private static boolean isHarvestable(Block block) {
        if (!FARM_TARGETS.contains(block.getType())) {
            return false;
        }
        if (block.getBlockData() instanceof Ageable ageable) {
            return ageable.getAge() >= ageable.getMaximumAge();
        }
        return switch (block.getType()) {
            case MELON, PUMPKIN, SUGAR_CANE, CACTUS, BAMBOO, KELP -> true;
            default -> false;
        };
    }

    private record ToolContext(ToolState state, ToolDefinition definition) {
    }
}
