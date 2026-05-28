package com.dbdgenerators;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.data.type.Light;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class DbDGeneratorPlugin extends JavaPlugin implements Listener {

    private final Map<UUID, RepairSession> activeSessions = new HashMap<>();
    private final List<GeneratorInstance> generators = new ArrayList<>();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        registerGeneratorRecipe();
    }

    @Override
    public void onDisable() {
        for (GeneratorInstance gen : generators) {
            gen.removeEntities();
        }
        generators.clear();
        activeSessions.clear();
    }

    public ItemStack getGeneratorItem() {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta != null) {
            PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
            profile.setProperty(new ProfileProperty("textures", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzkzZDUxNmU3YzFjZGI1MjA0ZTYyOGFkOWFlYzRhOGY5NWQ4ZWU4Y2QzNzEzOWQwNmUzYTk2OWU5OTliYjNiMCJ9fX0="));
            meta.setPlayerProfile(profile);
            
            meta.setDisplayName("§e§lГенератор из DbD");
            meta.setLore(Arrays.asList(
                    "§7Установите этот блок, чтобы создать",
                    "§7высокий работающий генератор.",
                    "",
                    "§cShift + ЛКМ — демонтировать объект"
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private void registerGeneratorRecipe() {
        NamespacedKey key = new NamespacedKey(this, "dbd_generator");
        ShapedRecipe recipe = new ShapedRecipe(key, getGeneratorItem());
        recipe.shape("I I", "RLR", " D ");
        recipe.setIngredient('R', Material.REDSTONE);
        recipe.setIngredient('D', Material.DISPENSER);
        recipe.setIngredient('L', Material.LAVA_BUCKET);
        recipe.setIngredient('I', Material.IRON_INGOT);
        Bukkit.addRecipe(recipe);
    }

    @EventHandler
    public void onGeneratorPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (item.hasItemMeta() && Objects.requireNonNull(item.getItemMeta()).getDisplayName().contains("Генератор")) {
            event.setCancelled(true);
            item.setAmount(item.getAmount() - 1);

            Location spawnLoc = event.getBlockPlaced().getLocation();
            spawnGenerator(spawnLoc);
            event.getPlayer().sendMessage("§aГенератор установлен! ПКМ — ремонт, Shift+ЛКМ — убрать.");
        }
    }

    public void spawnGenerator(Location loc) {
        Location centerLoc = loc.getBlock().getLocation().add(0.5, 0.0, 0.5);

        Interaction interaction = centerLoc.getWorld().spawn(centerLoc, Interaction.class, ent -> {
            ent.setInteractionWidth(1.6f);
            ent.setInteractionHeight(3.2f); 
        });

        List<ItemDisplay> parts = new ArrayList<>();
        List<ItemDisplay> leftPistons = new ArrayList<>();
        List<ItemDisplay> rightPistons = new ArrayList<>();

        parts.add(spawnPart(centerLoc, Material.BLAST_FURNACE, 0, 0.5, 0, 1.2f, 1.0f, 1.2f, 0, 0));
        parts.add(spawnPart(centerLoc, Material.OBSERVER, 0, 1.0, 0.42, 0.5f, 0.4f, 0.25f, 0, 0));
        parts.add(spawnPart(centerLoc, Material.HOPPER, 0, 1.3, -0.3, 0.7f, 0.6f, 0.7f, 0, 0));
        parts.add(spawnPart(centerLoc, Material.ANVIL, 0, 1.35, 0.3, 0.5f, 0.4f, 0.5f, 0, 0));
        parts.add(spawnPart(centerLoc, Material.LIGHTNING_ROD, 0, 2.0, 0.3, 0.5f, 1.8f, 0.5f, 0, 0));

        ItemDisplay lamp = spawnPart(centerLoc, Material.REDSTONE_LAMP, 0, 2.9, 0.3, 0.45f, 0.45f, 0.45f, 0, 0);
        parts.add(lamp);

        double[] zOffsets = {-0.4, -0.2, 0.0, 0.2, 0.4};
        for (double zOffset : zOffsets) {
            ItemDisplay lp = spawnPart(centerLoc, Material.PISTON_HEAD, -0.38, 1.1, zOffset, 0.25f, 0.25f, 0.25f, -90, -30);
            leftPistons.add(lp);
            parts.add(lp);

            ItemDisplay rp = spawnPart(centerLoc, Material.PISTON_HEAD, 0.38, 1.1, zOffset, 0.25f, 0.25f, 0.25f, 90, -30);
            rightPistons.add(rp);
            parts.add(rp);
        }

        generators.add(new GeneratorInstance(this, centerLoc, parts, leftPistons, rightPistons, lamp, interaction));
    }

    private ItemDisplay spawnPart(Location center, Material mat, double dx, double dy, double dz, float sx, float sy, float sz, float yaw, float pitch) {
        Location partLoc = center.clone().add(dx, dy, dz);
        partLoc.setYaw(yaw);
        partLoc.setPitch(pitch);
        return partLoc.getWorld().spawn(partLoc, ItemDisplay.class, ent -> {
            ent.setItemStack(new ItemStack(mat));
            Transformation t = ent.getTransformation();
            t.getScale().set(sx, sy, sz);
            ent.setTransformation(t);
        });
    }

    // --- УМНЫЙ ПОИСК ГЕНЕРАТОРА (RayTrace + Proximity) ---
    private GeneratorInstance getTargetGenerator(Player player, double maxDist) {
        Location eye = player.getEyeLocation();
        RayTraceResult result = player.getWorld().rayTraceEntities(eye, eye.getDirection(), maxDist, ent -> ent instanceof Interaction);
        
        if (result != null && result.getHitEntity() instanceof Interaction interaction) {
            GeneratorInstance gen = findGeneratorByInteraction(interaction);
            if (gen != null) return gen;
        }
        
        // Фолбэк: если RayTrace промазал, но игрок стоит совсем рядом (в радиусе 3 блоков)
        GeneratorInstance closest = null;
        double minDist = 3.0; 
        for (GeneratorInstance gen : generators) {
            double dist = player.getLocation().distance(gen.getLocation());
            if (dist < minDist) {
                minDist = dist;
                closest = gen;
            }
        }
        return closest;
    }

    private void tryStartRepair(Player player, GeneratorInstance gen) {
        if (gen == null || gen.isCompleted()) return;

        RepairSession currentSession = activeSessions.get(player.getUniqueId());
        if (currentSession != null) {
            if (currentSession.getGenerator().equals(gen)) {
                return; // Игрок уже чинит этот генератор
            } else {
                stopRepairing(player); // Чинил другой? Обрываем старую сессию
            }
        }

        RepairSession session = new RepairSession(player, gen);
        activeSessions.put(player.getUniqueId(), session);
        session.runTaskTimer(this, 0L, 2L);
        player.sendMessage("§aРемонт начат! (Нажми Shift, чтобы отпустить)");
    }

    private void breakGenerator(Player player, GeneratorInstance gen) {
        if (!generators.contains(gen)) return; // Защита от дюпов
        
        stopRepairing(player);
        gen.removeEntities();
        generators.remove(gen);
        
        player.getInventory().addItem(getGeneratorItem());
        player.sendMessage("§cГенератор демонтирован.");
        player.playSound(player.getLocation(), Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 1.0f, 0.8f);
    }

    // --- ГЛАВНЫЙ ПЕРЕХВАТЧИК КЛИКОВ (100% срабатывание) ---
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() == EquipmentSlot.OFF_HAND) return;
        Player player = event.getPlayer();
        Action action = event.getAction();

        // ЛКМ (Воздух или Блок) - Скиллчек или Демонтаж
        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            RepairSession session = activeSessions.get(player.getUniqueId());
            
            if (session != null && session.isSkillCheckActive()) {
                event.setCancelled(true);
                session.handleSkillCheckInput();
                return;
            }
            
            if (player.isSneaking()) {
                GeneratorInstance target = getTargetGenerator(player, 5.0);
                if (target != null) {
                    event.setCancelled(true);
                    breakGenerator(player, target);
                }
            }
        }
        
        // ПКМ (Воздух или Блок) - Ремонт
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            GeneratorInstance target = getTargetGenerator(player, 5.0);
            if (target != null) {
                event.setCancelled(true);
                tryStartRepair(player, target);
            }
        }
    }

    // Дублирующий перехватчик на случай, если клиент прислал пакет прямого удара по сущности
    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Interaction interaction) || !(event.getDamager() instanceof Player player)) return;
        
        GeneratorInstance gen = findGeneratorByInteraction(interaction);
        if (gen == null) return;
        event.setCancelled(true);

        RepairSession session = activeSessions.get(player.getUniqueId());
        if (session != null && session.isSkillCheckActive()) {
            session.handleSkillCheckInput();
            return;
        }
        if (player.isSneaking()) {
            breakGenerator(player, gen);
        }
    }

    // Дублирующий перехватчик прямого ПКМ по сущности
    @EventHandler
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() == EquipmentSlot.OFF_HAND) return;
        if (!(event.getRightClicked() instanceof Interaction interaction)) return;
        
        GeneratorInstance gen = findGeneratorByInteraction(interaction);
        if (gen != null) {
            event.setCancelled(true);
            tryStartRepair(event.getPlayer(), gen);
        }
    }

    @EventHandler
    public void onPlayerSneak(PlayerToggleSneakEvent event) {
        // ИСПРАВЛЕНО: Теперь Shift гарантированно обрывает сессию, без бага с `!`
        if (event.isSneaking()) {
            stopRepairing(event.getPlayer());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        stopRepairing(event.getPlayer());
    }

    private void stopRepairing(Player player) {
        RepairSession session = activeSessions.remove(player.getUniqueId());
        if (session != null) {
            session.cancel();
            player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize("§cРемонт прекращен."));
        }
    }

    private GeneratorInstance findGeneratorByInteraction(Interaction interaction) {
        return generators.stream()
                .filter(g -> g.getInteraction().getUniqueId().equals(interaction.getUniqueId()))
                .findFirst()
                .orElse(null);
    }

    private static class GeneratorInstance {
        private final JavaPlugin plugin;
        private final Location location;
        private final List<ItemDisplay> parts;
        private final List<ItemDisplay> leftPistons;
        private final List<ItemDisplay> rightPistons;
        private final ItemDisplay lampDisplay;
        private final Interaction interaction;
        private double progress = 0.0;
        private boolean completed = false;

        public GeneratorInstance(JavaPlugin plugin, Location location, List<ItemDisplay> parts, List<ItemDisplay> leftPistons, List<ItemDisplay> rightPistons, ItemDisplay lampDisplay, Interaction interaction) {
            this.plugin = plugin;
            this.location = location;
            this.parts = parts;
            this.leftPistons = leftPistons;
            this.rightPistons = rightPistons;
            this.lampDisplay = lampDisplay;
            this.interaction = interaction;
        }

        public Interaction getInteraction() { return interaction; }
        public Location getLocation() { return location; }
        public boolean isCompleted() { return completed; }
        public double getProgress() { return progress; }

        public void addProgress(double amount) {
            if (completed) return;
            this.progress = Math.max(0.0, Math.min(100.0, this.progress + amount));
            if (this.progress >= 100.0) {
                complete();
            }
        }

        public void animatePistons(long ticks) {
            for (int i = 0; i < 5; i++) {
                double activationThreshold = (i + 1) * 20.0; 
                ItemDisplay left = leftPistons.get(i);
                ItemDisplay right = rightPistons.get(i);

                if (this.progress >= activationThreshold || this.completed) {
                    float shift = (float) (Math.sin((ticks + i * 4) * 0.6) * 0.05);
                    
                    Transformation tLeft = left.getTransformation();
                    tLeft.getTranslation().set(-shift * 0.86f, shift * 0.5f, 0);
                    left.setTransformation(tLeft);

                    Transformation tRight = right.getTransformation();
                    tRight.getTranslation().set(shift * 0.86f, shift * 0.5f, 0);
                    right.setTransformation(tRight);
                } else {
                    Transformation tLeft = left.getTransformation();
                    tLeft.getTranslation().set(0, 0, 0);
                    left.setTransformation(tLeft);

                    Transformation tRight = right.getTransformation();
                    tRight.getTranslation().set(0, 0, 0);
                    right.setTransformation(tRight);
                }
            }
        }

        private void complete() {
            this.completed = true;
            lampDisplay.setItemStack(new ItemStack(Material.SEA_LANTERN));

            Location lampLoc = location.clone().add(0, 2.9, 0.3);
            lampLoc.getBlock().setType(Material.LIGHT);
            if (lampLoc.getBlock().getBlockData() instanceof Light lightData) {
                lightData.setLevel(15);
                lampLoc.getBlock().setBlockData(lightData);
            }

            Objects.requireNonNull(location.getWorld()).playSound(location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            location.getWorld().playSound(location, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f, 1.5f);
            location.getWorld().spawnParticle(Particle.FLASH, location.clone().add(0, 1.5, 0), 20); 

            new BukkitRunnable() {
                private long ticks = 0;
                @Override
                public void run() {
                    if (!interaction.isValid()) {
                        cancel();
                        return;
                    }
                    ticks++;
                    animatePistons(ticks);
                    if (ticks % 10 == 0) {
                        location.getWorld().spawnParticle(Particle.SMOKE, location.clone().add(0, 1.5, 0), 1, 0.1, 0.0, 0.1, 0.01);
                    }
                }
            }.runTaskTimer(plugin, 0L, 2L);
        }

        public void removeEntities() {
            for (ItemDisplay part : parts) {
                part.remove();
            }
            interaction.remove();
            
            Location lampLoc = location.clone().add(0, 2.9, 0.3); 
            if (lampLoc.getBlock().getType() == Material.LIGHT) {
                lampLoc.getBlock().setType(Material.AIR);
            }
        }
    }

    private class RepairSession extends BukkitRunnable {
        private final Player player;
        private final GeneratorInstance generator;
        private int tickCounter = 0;
        private boolean skillCheckActive = false;
        private int skillCheckPosition = 0;
        private int targetMin = 0;
        private int targetMax = 0;
        private boolean movingRight = true;

        public RepairSession(Player player, GeneratorInstance generator) {
            this.player = player;
            this.generator = generator;
        }

        public boolean isSkillCheckActive() { return skillCheckActive; }
        public GeneratorInstance getGenerator() { return generator; }

        @Override
        public void run() {
            if (generator.isCompleted() || !player.isOnline() || player.getLocation().distance(generator.getLocation()) > 6.0) {
                stopRepairing(player);
                return;
            }

            tickCounter++;
            generator.animatePistons(tickCounter); 

            if (skillCheckActive) {
                updateSkillCheck();
            } else {
                generator.addProgress(0.20); 
                displayProgress();

                if (tickCounter % 6 == 0) {
                    generator.location.getWorld().playSound(generator.location, Sound.BLOCK_BONE_BLOCK_BREAK, 0.6f, 0.7f);
                    generator.location.getWorld().playSound(generator.location, Sound.BLOCK_IRON_TRAPDOOR_OPEN, 0.3f, 0.5f);
                    generator.location.getWorld().spawnParticle(Particle.SMOKE, generator.location.clone().add(0, 1.5, 0), 2, 0.3, 0.1, 0.3, 0.02);
                }

                if (ThreadLocalRandom.current().nextDouble() < 0.015) {
                    startSkillCheck();
                }
            }
        }

        private void displayProgress() {
            int bars = (int) (generator.getProgress() / 5);
            String progressBar = "§e" + "■".repeat(bars) + "§7" + "■".repeat(20 - bars);
            player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(
                    "§7Ремонт: " + progressBar + " §6" + String.format("%.1f", generator.getProgress()) + "%"
            ));
        }

        private void startSkillCheck() {
            skillCheckActive = true;
            skillCheckPosition = 0;
            movingRight = true;
            targetMin = ThreadLocalRandom.current().nextInt(8, 14);
            targetMax = targetMin + 3;
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.0f);
        }

        private void updateSkillCheck() {
            if (movingRight) {
                skillCheckPosition++;
                if (skillCheckPosition >= 20) movingRight = false;
            } else {
                skillCheckPosition--;
                if (skillCheckPosition <= 0) {
                    failSkillCheck();
                    return;
                }
            }

            StringBuilder sb = new StringBuilder("§cПРОЖМИ ЛКМ: §7[");
            for (int i = 0; i <= 20; i++) {
                if (i == skillCheckPosition) {
                    sb.append("§4§l┃");
                } else if (i >= targetMin && i <= targetMax) {
                    sb.append("§a■");
                } else {
                    sb.append("§8-");
                }
            }
            sb.append("§7]");
            player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(sb.toString()));
        }

        public void handleSkillCheckInput() {
            if (!skillCheckActive) return;
            if (skillCheckPosition >= targetMin && skillCheckPosition <= targetMax) {
                generator.addProgress(6.0); 
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.5f);
                skillCheckActive = false;
            } else {
                failSkillCheck();
            }
        }

        private void failSkillCheck() {
            generator.addProgress(-8.0); 
            Objects.requireNonNull(generator.location.getWorld()).playSound(generator.location, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1.2f);
            generator.location.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, generator.location.clone().add(0, 1.5, 0), 15, 0.4, 0.4, 0.4, 0.5);
            generator.location.getWorld().spawnParticle(Particle.LARGE_SMOKE, generator.location.clone().add(0, 1.5, 0), 5);
            skillCheckActive = false;
        }
    }
}
