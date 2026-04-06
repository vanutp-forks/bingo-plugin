package me._furiouspotato_.bingo.service;

import org.bukkit.Keyed;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public final class PlayerRoundPreparationService {
    private final RulesConfigService rules;
    private final BoardUiService boardUiService;
    private final NamespacedKey starterItemKey;

    public PlayerRoundPreparationService(RulesConfigService rules, BoardUiService boardUiService,
            NamespacedKey starterItemKey) {
        this.rules = rules;
        this.boardUiService = boardUiService;
        this.starterItemKey = starterItemKey;
    }

    public void prepare(Player player) {
        player.setGameMode(org.bukkit.GameMode.SURVIVAL);
        player.setFlying(false);
        player.setAllowFlight(false);
        Double maxHealth = player.getAttribute(Attribute.MAX_HEALTH) != null
                ? player.getAttribute(Attribute.MAX_HEALTH).getValue()
                : 20.0;
        player.setHealth(Math.min(maxHealth, 20.0));
        player.setFoodLevel(20);
        player.setSaturation(5.0f);
        player.setFireTicks(0);
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));

        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setArmorContents(null);
        inventory.setItemInOffHand(new ItemStack(Material.AIR));

        if (rules.giveInitialItems()) {
            fillStarterItems(inventory);
        }
        inventory.setItem(8, boardUiService.createCard());

        applyConfiguredEffects(player);
        unlockAllRecipes(player);
        resetAllAdvancements(player);
        player.updateInventory();
    }

    private void applyConfiguredEffects(Player player) {
        if (rules.enableFireResistanceEffect()) {
            player.addPotionEffect(
                    new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, false, false,
                            false));
        }
        if (rules.enableWaterBreathingEffect()) {
            player.addPotionEffect(
                    new PotionEffect(PotionEffectType.WATER_BREATHING, Integer.MAX_VALUE, 0, false, false,
                            false));
        }
        if (rules.enableNightVisionEffect()) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, Integer.MAX_VALUE, 0, false, false,
                    false));
        }
    }

    public boolean isStarterItem(ItemStack item) {
        if (item == null) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        Byte marker = meta.getPersistentDataContainer().get(starterItemKey, PersistentDataType.BYTE);
        return marker != null && marker == 1;
    }

    private void fillStarterItems(PlayerInventory inventory) {
        ItemStack pickaxe = new ItemStack(Material.NETHERITE_PICKAXE);
        pickaxe.addUnsafeEnchantment(Enchantment.EFFICIENCY, 5);
        pickaxe.addUnsafeEnchantment(Enchantment.FORTUNE, 3);
        setLegacyNameUnbreakable(pickaxe, "Potato Pickaxe");

        ItemStack axe = new ItemStack(Material.NETHERITE_AXE);
        axe.addUnsafeEnchantment(Enchantment.SHARPNESS, 5);
        axe.addUnsafeEnchantment(Enchantment.LOOTING, 3);
        axe.addUnsafeEnchantment(Enchantment.EFFICIENCY, 5);
        setLegacyNameUnbreakable(axe, "Potato Axe");

        ItemStack shovel = new ItemStack(Material.NETHERITE_SHOVEL);
        shovel.addUnsafeEnchantment(Enchantment.EFFICIENCY, 5);
        shovel.addUnsafeEnchantment(Enchantment.SILK_TOUCH, 1);
        setLegacyNameUnbreakable(shovel, "Potato Shovel");

        ItemStack bakedPotato = new ItemStack(Material.COOKED_PORKCHOP, 64);
        ItemMeta bakedMeta = bakedPotato.getItemMeta();
        bakedMeta.displayName(Component.text("Baked Potato", NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false));
        markStarterItem(bakedMeta);
        bakedPotato.setItemMeta(bakedMeta);

        ItemStack boots = new ItemStack(Material.LEATHER_BOOTS);
        boots.addUnsafeEnchantment(Enchantment.DEPTH_STRIDER, 3);
        setLegacyNameUnbreakable(boots, "Potato Boots");

        ItemStack helmet = new ItemStack(Material.LEATHER_HELMET);
        helmet.addUnsafeEnchantment(Enchantment.AQUA_AFFINITY, 1);
        setLegacyNameUnbreakable(helmet, "Potato Helmet");

        inventory.setItem(0, axe);
        inventory.setItem(1, pickaxe);
        inventory.setItem(2, shovel);
        inventory.setItem(3, bakedPotato);
        inventory.setArmorContents(new ItemStack[] { boots, null, null, helmet });
    }

    private void setLegacyNameUnbreakable(ItemStack item, String name) {
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        markStarterItem(meta);
        item.setItemMeta(meta);
    }

    private void markStarterItem(ItemMeta meta) {
        meta.getPersistentDataContainer().set(starterItemKey, PersistentDataType.BYTE, (byte) 1);
    }

    private static void unlockAllRecipes(Player player) {
        java.util.Set<NamespacedKey> recipes = new java.util.HashSet<>();
        java.util.Iterator<Recipe> iterator = Bukkit.recipeIterator();
        while (iterator.hasNext()) {
            Recipe recipe = iterator.next();
            if (recipe instanceof Keyed keyed) {
                recipes.add(keyed.getKey());
            }
        }
        if (!recipes.isEmpty()) {
            player.discoverRecipes(recipes);
        }
    }

    private static void resetAllAdvancements(Player player) {
        java.util.Iterator<Advancement> iterator = Bukkit.advancementIterator();
        while (iterator.hasNext()) {
            Advancement advancement = iterator.next();
            org.bukkit.advancement.AdvancementProgress progress = player.getAdvancementProgress(advancement);
            for (String criteria : progress.getAwardedCriteria()) {
                progress.revokeCriteria(criteria);
            }
        }
    }
}
