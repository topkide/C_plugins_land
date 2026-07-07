package com.dotorimaru.land.listeners;

import com.dotorimaru.land.LandConfig;
import com.dotorimaru.land.manager.LandManager;
import com.dotorimaru.land.manager.MessageManager;
import com.dotorimaru.land.model.Estate;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 토지 보호. 보호가 켜진 부지에서 소유자(및 land.bypass) 외의 변경을 차단합니다.
 */
public class ProtectionListener implements Listener {

    private final LandConfig config;
    private final MessageManager messages;
    private final LandManager landManager;

    private final Map<UUID, Long> lastDenied = new HashMap<>();

    public ProtectionListener(LandConfig config, MessageManager messages, LandManager landManager) {
        this.config = config;
        this.messages = messages;
        this.landManager = landManager;
    }

    // ----- 블록 파괴 / 설치 -----

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent event) {
        if (!config.isProtectBlockBreak()) return;
        Block b = event.getBlock();
        if (!landManager.canBuild(event.getPlayer(), b.getWorld().getName(), b.getX(), b.getZ())) {
            event.setCancelled(true);
            denied(event.getPlayer(), b);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPlace(BlockPlaceEvent event) {
        if (!config.isProtectBlockPlace()) return;
        Block b = event.getBlock();
        if (!landManager.canBuild(event.getPlayer(), b.getWorld().getName(), b.getX(), b.getZ())) {
            event.setCancelled(true);
            denied(event.getPlayer(), b);
        }
    }

    // ----- 컨테이너 / 상호작용 -----

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block b = event.getClickedBlock();
        if (b == null) return;

        Player player = event.getPlayer();
        if (landManager.canBuild(player, b.getWorld().getName(), b.getX(), b.getZ())) return;

        boolean container = config.isProtectContainer() && (b.getState() instanceof InventoryHolder);
        boolean interact = config.isProtectInteract() && isInteractable(b.getType());
        if (container || interact) {
            event.setCancelled(true);
            denied(player, b);
        }
    }

    private boolean isInteractable(Material type) {
        String name = type.name();
        return name.endsWith("_DOOR") || name.endsWith("_TRAPDOOR") || name.endsWith("_FENCE_GATE")
                || name.endsWith("_BUTTON") || type == Material.LEVER
                || name.endsWith("_PRESSURE_PLATE") || name.contains("ANVIL");
    }

    // ----- 폭발 -----

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!config.isProtectExplosion()) return;
        String world = event.getEntity().getWorld().getName();
        removeProtected(event.blockList(), world);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (!config.isProtectExplosion()) return;
        String world = event.getBlock().getWorld().getName();
        removeProtected(event.blockList(), world);
    }

    private void removeProtected(List<Block> blocks, String world) {
        blocks.removeIf(b -> landManager.isProtectedArea(world, b.getX(), b.getZ()));
    }

    // ----- 몹 그리핑 -----

    @EventHandler(ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (!config.isProtectMobGriefing()) return;
        if (!(event.getEntity() instanceof LivingEntity)) return; // 낙하 블록 등 제외
        if (event.getEntity() instanceof Player) return;
        Block b = event.getBlock();
        if (landManager.isProtectedArea(b.getWorld().getName(), b.getX(), b.getZ())) {
            event.setCancelled(true);
        }
    }

    // ----- 유체 흐름 -----

    @EventHandler(ignoreCancelled = true)
    public void onFluidFlow(BlockFromToEvent event) {
        if (!config.isProtectFluidFlow()) return;
        Block to = event.getToBlock();
        String world = to.getWorld().getName();
        Estate toEstate = landManager.getEstateAt(world, to.getX(), to.getZ());
        if (toEstate == null || !toEstate.isProtection()) return;

        Block from = event.getBlock();
        Estate fromEstate = landManager.getEstateAt(world, from.getX(), from.getZ());
        if (fromEstate == null || !fromEstate.getId().equals(toEstate.getId())) {
            event.setCancelled(true);
        }
    }

    // ----- 피스톤 -----

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (!config.isProtectPiston()) return;
        if (pistonViolates(event.getBlock(), event.getBlocks(), event.getDirection())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (!config.isProtectPiston()) return;
        if (pistonViolates(event.getBlock(), event.getBlocks(), event.getDirection())) {
            event.setCancelled(true);
        }
    }

    private boolean pistonViolates(Block piston, List<Block> moved, BlockFace direction) {
        UUID pistonEstate = estateIdAt(piston);
        for (Block b : moved) {
            if (crossesProtection(b, pistonEstate)) return true;
            if (crossesProtection(b.getRelative(direction), pistonEstate)) return true;
        }
        return false;
    }

    private boolean crossesProtection(Block b, UUID pistonEstate) {
        Estate est = landManager.getEstateAt(b.getWorld().getName(), b.getX(), b.getZ());
        if (est == null || !est.isProtection()) return false;
        return !est.getId().equals(pistonEstate);
    }

    private UUID estateIdAt(Block b) {
        Estate est = landManager.getEstateAt(b.getWorld().getName(), b.getX(), b.getZ());
        return est == null ? null : est.getId();
    }

    // ----- 공통 -----

    private void denied(Player player, Block b) {
        long now = System.currentTimeMillis();
        Long last = lastDenied.get(player.getUniqueId());
        if (last != null && now - last < 1500) return;
        lastDenied.put(player.getUniqueId(), now);

        Estate est = landManager.getEstateAt(b.getWorld().getName(), b.getX(), b.getZ());
        String owner = "누군가";
        if (est != null) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(est.getOwner());
            if (op.getName() != null) owner = op.getName();
        }
        messages.send(player, "protection-denied", "%owner%", owner);
    }
}
