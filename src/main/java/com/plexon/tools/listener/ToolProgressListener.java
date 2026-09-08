package com.plexon.tools.listener;

import com.plexon.tools.config.PluginSettings;
import com.plexon.tools.config.ToolConfigRepository;
import com.plexon.tools.item.ToolState;
import com.plexon.tools.model.ToolDefinition;
import com.plexon.tools.model.TrackingType;
import com.plexon.tools.service.AbilityService;
import com.plexon.tools.service.NaturalBlockTracker;
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
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Gameplay listener for PlexonTools progression.
 *
 * <p>The ordinary main-hand block break path is intentionally different from
 * the lower-frequency handlers below: once a tool has been resolved and
 * validated, its parsed identity, definition, latest state and derived ability
 * flags are retained in an {@link ActiveToolContext}. Steady-state mining does
 * not rediscover the same PDC identity and UUIDs for every block.</p>
 */
public final class ToolProgressListener implements Listener {
    private static final long ACTIVE_IDENTITY_REVALIDATE_TICKS = 10L;
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
    private final NaturalBlockTracker naturalBlocks;
    private final PluginSettings settings;
    private final Map<UUID, ActiveToolContext> activeTools = new HashMap<>();
    private final IdentityHashMap<EntityDamageByEntityEvent, ToolUse> damageContexts =
            new IdentityHashMap<>();
    private long validationEpoch = 1L;

    public ToolProgressListener(
            ToolConfigRepository tools,
            ProgressionService progression,
            AbilityService abilities,
            NaturalBlockTracker naturalBlocks,
            PluginSettings settings
    ) {
        this.tools = tools;
        this.progression = progression;
        this.abilities = abilities;
        this.naturalBlocks = naturalBlocks;
        this.settings = settings;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ActiveResolution resolution = resolveActive(player, true);
        ActiveToolContext context = resolution.context();
        if (context == null) {
            if (resolution.tagged()) {
                event.setCancelled(settings.cancelBlockBreaks());
            }
            return;
        }
        abilities.boostBlockExperience(event, context.abilityProfile);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockBreakAbilities(BlockBreakEvent event) {
        if (event.isCancelled()) {
            return;
        }
        Player player = event.getPlayer();
        ActiveToolContext context = activeTools.get(player.getUniqueId());
        if (context == null || !context.quickIdentityMatches(
                player.getInventory().getHeldItemSlot(),
                player.getInventory().getItemInMainHand().getType(), validationEpoch)) {
            return;
        }
        if (!fastCanUse(player, context)) {
            invalidate(player);
            return;
        }

        ToolState latest = progression.latestState(context.state);
        context.refreshState(latest, abilities);
        String target = blockTrackingTarget(
                context.definition.trackingType(), event.getBlock());
        if (target != null && context.definition.tracks(target, latest.level())
                && naturalBlocks.allowsProgress(event)) {
            latest = progression.addResolvedProgress(
                    player, EquipmentSlot.HAND, context.definition, latest, target, 1L);
            context.refreshState(latest, abilities);
        }
        if (abilities.isAreaMining(player)) {
            return;
        }
        abilities.prepareBlockDrops(event, context.abilityProfile);
        if (context.abilityProfile.areaMine()) {
            abilities.mineArea(event, context.definition, latest);
        }
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
                ToolState updated = progression.addResolvedProgress(player, EquipmentSlot.HAND,
                        context.definition(), context.state(), target, 1L);
                refreshActiveState(player, updated);
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
            ToolState updated = progression.addResolvedProgress(player, EquipmentSlot.HAND,
                    context.definition(), latest, target, amount);
            refreshActiveState(player, updated);
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
            ToolState updated = progression.addResolvedProgress(event.getPlayer(), hand,
                    context.definition(), context.state(), target, 1L);
            refreshActiveState(event.getPlayer(), updated);
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
            ToolState updated = progression.addResolvedProgress(event.getPlayer(), hand,
                    context.definition(), context.state(), target, 1L);
            refreshActiveState(event.getPlayer(), updated);
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
        if (context.definition().tracks(target, context.state().level())
                && naturalBlocks.isNatural(event.getHarvestedBlock())) {
            ToolState updated = progression.addResolvedProgress(event.getPlayer(), hand,
                    context.definition(), context.state(), target, 1L);
            refreshActiveState(event.getPlayer(), updated);
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

    @EventHandler(priority = EventPriority.MONITOR)
    public void onHeldSlotChanged(PlayerItemHeldEvent event) {
        invalidate(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        invalidate(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            invalidate(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            invalidate(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            invalidate(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDrop(PlayerDropItemEvent event) {
        invalidate(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        invalidate(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        invalidate(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        invalidate(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        invalidate(event.getPlayer());
    }

    /** Invalidates all parsed active identities after a definition/config reload. */
    public void invalidateAllActiveContexts() {
        activeTools.clear();
        validationEpoch = validationEpoch == Long.MAX_VALUE ? 1L : validationEpoch + 1L;
    }

    int activeContextCount() {
        return activeTools.size();
    }

    private ActiveResolution resolveActive(Player player, boolean notify) {
        UUID playerId = player.getUniqueId();
        ItemStack item = player.getInventory().getItemInMainHand();
        int heldSlot = player.getInventory().getHeldItemSlot();
        long currentTick = player.getWorld().getGameTime();
        ActiveToolContext cached = activeTools.get(playerId);

        if (cached != null && cached.quickIdentityMatches(
                heldSlot, item.getType(), validationEpoch)) {
            ToolState latest = progression.latestState(cached.state);
            cached.refreshState(latest, abilities);
            if (fastCanUse(player, cached)) {
                if (currentTick < cached.nextIdentityValidationTick) {
                    return ActiveResolution.usable(cached);
                }
                ProgressionService.ToolResolution revalidated = progression.resolve(item);
                ToolState state = revalidated.state();
                if (state != null
                        && state.instanceId().equals(cached.instanceId)
                        && state.toolId().equalsIgnoreCase(cached.toolId)
                        && progression.canUse(player, cached.definition, state, false)) {
                    cached.refreshState(state, abilities);
                    cached.nextIdentityValidationTick = currentTick
                            + ACTIVE_IDENTITY_REVALIDATE_TICKS;
                    return ActiveResolution.usable(cached);
                }
            }
            activeTools.remove(playerId);
        }

        ProgressionService.ToolResolution resolution = progression.resolve(item);
        ToolState state = resolution.state();
        if (state == null) {
            if (notify && resolution.tagged()) {
                progression.warnInvalid(player);
            }
            return resolution.tagged() ? ActiveResolution.invalid() : ActiveResolution.untagged();
        }
        ToolDefinition definition = tools.findCached(state.toolId());
        if (definition == null || !definition.enabled()) {
            if (notify) {
                progression.warnInvalid(player);
            }
            return ActiveResolution.invalid();
        }
        if (!progression.canUse(player, definition, state, notify)) {
            return ActiveResolution.invalid();
        }

        ActiveToolContext created = new ActiveToolContext(
                state.instanceId(),
                state.toolId(),
                state.ownerId(),
                state.boundWorld(),
                definition,
                state,
                abilities.blockProfile(definition, state),
                heldSlot,
                item.getType(),
                validationEpoch,
                currentTick + ACTIVE_IDENTITY_REVALIDATE_TICKS);
        activeTools.put(playerId, created);
        return ActiveResolution.usable(created);
    }

    private boolean fastCanUse(Player player, ActiveToolContext context) {
        if (!context.ownerId.equals(player.getUniqueId())
                || !context.definition.levels().containsKey(context.state.level())) {
            return false;
        }
        String world = player.getWorld().getName();
        boolean validWorld = context.definition.isAllowedWorld(world);
        if (settings.enforceBoundWorld() && !context.definition.sharesProgressAcrossWorlds()) {
            validWorld = validWorld && context.boundWorld.equalsIgnoreCase(world);
        }
        return validWorld || player.hasPermission("plexontools.bypass.world");
    }

    private void refreshActiveState(Player player, ToolState state) {
        ActiveToolContext active = activeTools.get(player.getUniqueId());
        if (active != null && active.instanceId.equals(state.instanceId())) {
            active.refreshState(state, abilities);
        }
    }

    private void invalidate(Player player) {
        activeTools.remove(player.getUniqueId());
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

    private static final class ActiveToolContext {
        private final UUID instanceId;
        private final String toolId;
        private final UUID ownerId;
        private final String boundWorld;
        private final ToolDefinition definition;
        private final int heldSlot;
        private final Material material;
        private final long validationEpoch;
        private ToolState state;
        private AbilityService.BlockAbilityProfile abilityProfile;
        private long nextIdentityValidationTick;

        private ActiveToolContext(
                UUID instanceId,
                String toolId,
                UUID ownerId,
                String boundWorld,
                ToolDefinition definition,
                ToolState state,
                AbilityService.BlockAbilityProfile abilityProfile,
                int heldSlot,
                Material material,
                long validationEpoch,
                long nextIdentityValidationTick
        ) {
            this.instanceId = instanceId;
            this.toolId = toolId;
            this.ownerId = ownerId;
            this.boundWorld = boundWorld;
            this.definition = definition;
            this.state = state;
            this.abilityProfile = abilityProfile;
            this.heldSlot = heldSlot;
            this.material = material;
            this.validationEpoch = validationEpoch;
            this.nextIdentityValidationTick = nextIdentityValidationTick;
        }

        private boolean quickIdentityMatches(int slot, Material currentMaterial, long epoch) {
            return heldSlot == slot && material == currentMaterial && validationEpoch == epoch;
        }

        private void refreshState(ToolState latest, AbilityService abilities) {
            if (latest == null
                    || !latest.instanceId().equals(instanceId)
                    || !latest.ownerId().equals(ownerId)
                    || !latest.toolId().equalsIgnoreCase(toolId)) {
                return;
            }
            int previousLevel = state.level();
            state = latest;
            if (latest.level() != previousLevel) {
                abilityProfile = abilities.blockProfile(definition, latest);
            }
        }
    }

    private record ActiveResolution(boolean tagged, ActiveToolContext context) {
        private static final ActiveResolution UNTAGGED = new ActiveResolution(false, null);
        private static final ActiveResolution INVALID = new ActiveResolution(true, null);

        private static ActiveResolution untagged() {
            return UNTAGGED;
        }

        private static ActiveResolution invalid() {
            return INVALID;
        }

        private static ActiveResolution usable(ActiveToolContext context) {
            return new ActiveResolution(true, context);
        }
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
