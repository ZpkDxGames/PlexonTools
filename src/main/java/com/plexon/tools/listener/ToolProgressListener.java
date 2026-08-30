package com.plexon.tools.listener;

import com.plexon.tools.config.PluginSettings;
import com.plexon.tools.config.ToolConfigRepository;
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

import java.util.IdentityHashMap;
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
    private final ProgressionService progression;
    private final AbilityService abilities;
    private final PluginSettings settings;
    private final IdentityHashMap<BlockBreakEvent, ToolUse> blockBreakContexts =
            new IdentityHashMap<>();
    private final IdentityHashMap<EntityDamageByEntityEvent, ToolUse> damageContexts =
            new IdentityHashMap<>();

    public ToolProgressListener(
            ToolConfigRepository tools,
            ProgressionService progression,
            AbilityService abilities,
            PluginSettings settings
    ) {
        this.tools = tools;
        this.progression = progression;
        this.abilities = abilities;
        this.settings = settings;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        ToolUse context = inspect(player, item, true);
        if (!context.usable()) {
            if (context.tagged()) {
                event.setCancelled(settings.cancelBlockBreaks());
            }
            return;
        }
        blockBreakContexts.put(event, context);

        abilities.boostBlockExperience(event, context.definition(), context.state());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockBreakAbilities(BlockBreakEvent event) {
        ToolUse context = blockBreakContexts.remove(event);
        if (event.isCancelled() || context == null) {
            return;
        }
        Player player = event.getPlayer();
        ToolState latest = progression.latestState(context.state());
        String target = blockTrackingTarget(
                context.definition().trackingType(), event.getBlock());
        if (target != null && context.definition().tracks(target, latest.level())) {
            latest = progression.addResolvedProgress(
                    player, EquipmentSlot.HAND, context.definition(), latest, target, 1L);
        }
        if (abilities.isAreaMining(player)) {
            return;
        }
        abilities.mineArea(event, context.definition(), latest);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        Player player = event.getEntity().getKiller();
        if (player == null) {
            return;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        ToolUse context = inspect(player, item, false);
        if (!context.usable()) {
            return;
        }

        abilities.handleDeath(event, player, context.definition(), context.state());
        if (context.definition().trackingType() == TrackingType.MOBS_KILLED) {
            String target = event.getEntityType().name();
            if (context.definition().tracks(target, context.state().level())) {
                progression.addResolvedProgress(player, EquipmentSlot.HAND,
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
        ToolUse context = inspect(player, item, true);
        if (context.usable()) {
            damageContexts.put(event, context);
        } else if (context.tagged() && settings.cancelAttacks()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamageResolved(EntityDamageByEntityEvent event) {
        ToolUse context = damageContexts.remove(event);
        if (event.isCancelled() || context == null
                || !(event.getDamager() instanceof Player player)
                || !(event.getEntity() instanceof LivingEntity targetEntity)) {
            return;
        }
        ToolState latest = progression.latestState(context.state());

        abilities.applyHitEffect(player, targetEntity, context.definition(), latest);
        if (context.definition().trackingType() != TrackingType.DAMAGE_DEALT
                || event.getFinalDamage() <= 0.0D) {
            return;
        }
        String target = event.getEntityType().name();
        if (context.definition().tracks(target, latest.level())) {
            long amount = Math.max(1L, Math.round(event.getFinalDamage()));
            progression.addResolvedProgress(player, EquipmentSlot.HAND,
                    context.definition(), latest, target, amount);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!event.canBuild()) {
            return;
        }
        EquipmentSlot hand = event.getHand();
        ItemStack item = held(event.getPlayer(), hand);
        ToolUse context = inspect(event.getPlayer(), item, false);
        if (!context.usable()
                || context.definition().trackingType() != TrackingType.BLOCKS_PLACED) {
            return;
        }
        String target = event.getBlockPlaced().getType().name();
        if (context.definition().tracks(target, context.state().level())) {
            progression.addResolvedProgress(event.getPlayer(), hand,
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
        ToolUse context = inspect(event.getPlayer(), item, false);
        if (!context.usable()) {
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
            progression.addResolvedProgress(event.getPlayer(), hand,
                    context.definition(), context.state(), target, 1L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHarvest(PlayerHarvestBlockEvent event) {
        EquipmentSlot hand = event.getHand();
        ItemStack item = held(event.getPlayer(), hand);
        ToolUse context = inspect(event.getPlayer(), item, false);
        if (!context.usable()) {
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
            progression.addResolvedProgress(event.getPlayer(), hand,
                    context.definition(), context.state(), target, 1L);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        ItemStack item = event.getItem();
        ToolUse context = inspect(event.getPlayer(), item, true);
        if (!context.usable() && context.tagged() && settings.cancelInteractions()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        ItemStack item = event.getItem();
        ToolUse context = inspect(event.getPlayer(), item, true);
        if (!context.usable() && context.tagged()) {
            event.setCancelled(true);
        }
    }

    private ToolUse inspect(Player player, ItemStack item, boolean notify) {
        ProgressionService.ToolResolution resolution = progression.resolve(item);
        ToolState state = resolution.state();
        if (state == null) {
            if (notify && resolution.tagged()) {
                progression.warnInvalid(player);
            }
            return resolution.tagged() ? ToolUse.invalid() : ToolUse.untagged();
        }
        ToolDefinition definition = tools.findCached(state.toolId());
        if (definition == null || !definition.enabled()) {
            if (notify) {
                progression.warnInvalid(player);
            }
            return ToolUse.invalid();
        }
        if (!progression.canUse(player, definition, state, notify)) {
            return ToolUse.invalid();
        }
        return ToolUse.usable(state, definition);
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

    private record ToolUse(boolean tagged, ToolState state, ToolDefinition definition) {
        private static final ToolUse UNTAGGED = new ToolUse(false, null, null);
        private static final ToolUse INVALID = new ToolUse(true, null, null);

        private static ToolUse untagged() {
            return UNTAGGED;
        }

        private static ToolUse invalid() {
            return INVALID;
        }

        private static ToolUse usable(ToolState state, ToolDefinition definition) {
            return new ToolUse(true, state, definition);
        }

        private boolean usable() {
            return state != null && definition != null;
        }
    }
}
