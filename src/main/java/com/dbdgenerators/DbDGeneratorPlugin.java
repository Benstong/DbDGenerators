package com.dbdgenerators;

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
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
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
    }

    public ItemStack getGeneratorItem() {
        ItemStack item = new ItemStack(Material.IRON_BLOCK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§e§lГенератор из DbD (Ванильный)");
            meta.setLore(Arrays.asList(
                    "§7Установите этот блок, чтобы собрать",
                    "§7генератор из ванильных деталей.",
                    "",
                    "§6Заведите его, чтобы подать редстоун сигнал!"
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private void registerGeneratorRecipe() {
        NamespacedKey key = new NamespacedKey(this, "dbd_generator");
        ShapedRecipe recipe = new ShapedRecipe(key, getGeneratorItem());

        recipe.shape(
                "I I",
                "RLR",
                " D "
        );

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
            event.getPlayer().sendMessage("§aВанильный генератор успешно собран!");
        }
    }

    public void spawnGenerator(Location loc) {
        Location centerLoc = loc.getBlock().getLocation().add(0.5, 0.1, 0.5);

        Interaction interaction = centerLoc.getWorld().spawn(centerLoc, Interaction.class, ent -> {
            ent.setInteractionWidth(1.4f);
            ent.setInteractionHeight(2.0f);
        });

        List<ItemDisplay> parts = new ArrayList<>();

        // 1. Основание генератора - Большая плавильная печь
        parts.add(spawnPart(centerLoc, Material.BLAST_FURNACE, 0, 0, 0, 1.2f, 0.9f, 1.2f));

        // 2. Левый поршень мотора - Маленькая наковальня
        parts.add(spawnPart(centerLoc, Material.ANVIL, -0.25, 0.65, 0, 0.5f, 0.5f, 0.5f));

        // 3. Правый поршень мотора - Маленькое точило
        parts.add(spawnPart(centerLoc, Material.GRINDSTONE, 0.25, 0.65, 0, 0.5f, 0.5f, 0.5f));

        // 4. Мачта освещения - Тонкий высокий громоотвод
        parts.add(spawnPart(centerLoc, Material.LIGHTNING_ROD, 0, 1.1, 0, 0.8f, 1.6f, 0.8f));

        // 5. Сигнальная лампа - Уменьшенная редстоун лампа
        ItemDisplay lamp = spawnPart(centerLoc, Material.REDSTONE_LAMP, 0, 2.0, 0, 0.4f, 0.4f, 0.4f);
        parts.add(lamp);

        generators.add(new GeneratorInstance(centerLoc, parts, lamp, interaction));
    }

    private ItemDisplay spawnPart(Location center, Material mat, double dx, double dy, double dz, float sx, float sy, float sz) {
        Location partLoc = center.clone().add(dx, dy, dz);
        return partLoc.getWorld().spawn(partLoc, ItemDisplay.class, ent -> {
            ent.setItemStack(new ItemStack(mat));
            Transformation t = ent.getTransformation();
            t.getScale().set(sx, sy, sz);
            ent.setTransformation(t);
        });
    }

    @EventHandler
    public void onGeneratorInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Interaction interaction)) return;

        GeneratorInstance gen = findGeneratorByInteraction(interaction);
        if (gen == null || gen.isCompleted()) return;

        Player player = event.getPlayer();
        if (activeSessions.containsKey(player.getUniqueId())) return;

        RepairSession session = new RepairSession(player, gen);
        activeSessions.put(player.getUniqueId(), session);
        session.runTaskTimer(this, 0L, 2L);
    }

    @EventHandler
    public void onSkillCheckClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        RepairSession session = activeSessions.get(player.getUniqueId());

        if (session != null && (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK)) {
            event.setCancelled(true);
            session.handleSkillCheckInput();
        }
    }

    @EventHandler
    public void onPlayerSneak(PlayerToggleSneakEvent event) {
        if (event.isSneaking()) stopRepairing(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        stopRepairing(event.getPlayer());
    }

    private void stopRepairing(Player player) {
        RepairSession session = activeSessions.remove(player.getUniqueId());
        if (session != null) {
            session.cancel();
            player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize("§cВы отошли. Прогресс сохранен!"));
        }
    }

    private GeneratorInstance findGeneratorByInteraction(Interaction interaction) {
        return generators.stream()
                .filter(g -> g.getInteraction().getUniqueId().equals(interaction.getUniqueId()))
                .findFirst()
                .orElse(null);
    }

    private static class GeneratorInstance {
        private final Location location;
        private final List<ItemDisplay> parts;
        private final ItemDisplay lampDisplay;
        private final Interaction interaction;
        private double progress = 0.0;
        private boolean completed = false;

        public GeneratorInstance(Location location, List<ItemDisplay> parts, ItemDisplay lampDisplay, Interaction interaction) {
            this.location = location;
            this.parts = parts;
            this.lampDisplay = lampDisplay;
            this.interaction = interaction;
        }

        public Interaction getInteraction() { return interaction; }
        public boolean isCompleted() { return completed; }
        public double getProgress() { return progress; }

        public void addProgress(double amount) {
            if (completed) return;
            this.progress = Math.max(0.0, Math.min(100.0, this.progress + amount));
            if (this.progress >= 100.0) {
                complete();
            }
        }

        private void complete() {
            this.completed = true;

            lampDisplay.setItemStack(new ItemStack(Material.SEA_LANTERN));

            Location lampLoc = location.clone().add(0, 2, 0);
            lampLoc.getBlock().setType(Material.LIGHT);
            if (lampLoc.getBlock().getBlockData() instanceof Light lightData) {
                lightData.setLevel(15);
                lampLoc.getBlock().setBlockData(lightData);
            }

            Location redstoneLoc = location.clone().subtract(0, 1, 0);
            redstoneLoc.getBlock().setType(Material.REDSTONE_BLOCK);

            Objects.requireNonNull(location.getWorld()).playSound(location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            location.getWorld().playSound(location, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f, 1.5f);
            location.getWorld().spawnParticle(Particle.FLASH, location.clone().add(0, 1, 0), 20);
        }

        public void removeEntities() {
            for (ItemDisplay part : parts) {
                part.remove();
            }
            interaction.remove();
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

        @Override
        public void run() {
            if (generator.isCompleted() || !player.isOnline() || player.getLocation().distance(generator.location) > 3.5) {
                stopRepairing(player);
                return;
            }

            tickCounter++;

            if (skillCheckActive) {
                updateSkillCheck();
            } else {
                generator.addProgress(0.15); 
                displayProgress();

                if (tickCounter % 6 == 0) {
                    generator.location.getWorld().playSound(generator.location, Sound.BLOCK_BONE_BLOCK_BREAK, 0.6f, 0.7f);
                    // Вот тут исправлено название звука:
                    generator.location.getWorld().playSound(generator.location, Sound.BLOCK_IRON_TRAPDOOR_OPEN, 0.3f, 0.5f);
                    generator.location.getWorld().spawnParticle(Particle.SMOKE, generator.location.clone().add(0, 0.7, 0), 2, 0.2, 0.1, 0.2, 0.02);
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
            generator.location.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, generator.location.clone().add(0, 0.7, 0), 15, 0.3, 0.3, 0.3, 0.5);
            generator.location.getWorld().spawnParticle(Particle.LARGE_SMOKE, generator.location.clone().add(0, 0.7, 0), 5);
            skillCheckActive = false;
        }
    }
}
