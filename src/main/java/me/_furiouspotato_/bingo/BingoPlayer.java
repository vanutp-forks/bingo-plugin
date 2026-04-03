package me._furiouspotato_.bingo;

import java.util.Iterator;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import net.md_5.bungee.api.ChatColor;

public class BingoPlayer {
	Main plugin;

	public Player player;
	boolean hasWon;
	int score;

	boolean[] isCollected;

	Inventory inv;

	public boolean allowBreaking;

	int lastDeathTime;

	int displayMode;

	public BingoPlayer(Player player, Main plugin) {
		this.player = player;
		this.plugin = plugin;
		hasWon = false;
		score = 0;
		isCollected = new boolean[25];
		inv = Bukkit.createInventory((InventoryHolder) player, 45, "Bingo board");
		allowBreaking = true;
		lastDeathTime = 0;
		displayMode = 0;
	}

	public boolean hasBingoCard() {
		for (int i = 0; i < player.getInventory().getContents().length; i++) {
			if (plugin.bingoCard.equals(player.getInventory().getContents()[i])) {
				return true;
			}
		}
		return false;
	}

	public void prepare() {
		if (!hasBingoCard()) {
			player.getInventory().addItem(plugin.bingoCard.clone());
		}
		if (plugin.enableFireResistanceEffect)
			player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 2147483647, 0, false, false));
		if (plugin.enableWaterBreathingEffect)
			player.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 2147483647, 0, false, false));
		if (plugin.enableNightVisionEffect)
			player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 2147483647, 0, false, false));
		checkHoldingBingoCard();
	}

	public void addSpeed() {
		if (plugin.enableSpeedBonus)
			player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 2147483647, 2, false, false));
	}

	public void removeSpeed() {
		if (plugin.enableSpeedBonus)
			player.removePotionEffect(PotionEffectType.SPEED);
	}

	public boolean isHoldingBingoCard() {
		return !player.getGameMode().equals(GameMode.SPECTATOR) && plugin.bingoCard
				.equals(player.getInventory().getContents()[player.getInventory().getHeldItemSlot()]);
	}

	public void checkHoldingBingoCard() {
		if (isHoldingBingoCard()) {
			addSpeed();
		} else {
			removeSpeed();
		}
	}

	public void returnCard() {
		if (!hasBingoCard()) {
			player.getInventory().addItem(plugin.bingoCard);
		}
	}

	public void reset() {
		clear();
		player.spigot().respawn();
		hasWon = false;
		player.setGameMode(GameMode.SURVIVAL);
		player.setHealth(20.0);
		player.setFoodLevel(20);
		player.setSaturation(5.f);
		player.setRemainingAir(player.getMaximumAir());
		if (plugin.giveInitialItems)
			player.getInventory().setContents(plugin.playerInv);
		else {
			player.getInventory().clear();
			player.getInventory().setItem(8, plugin.bingoCard);
		}
		isCollected = new boolean[25];
		for (int i = 0; i < isCollected.length; i++) {
			isCollected[i] = false;
		}
		player.teleport((new Location(plugin.world, plugin.x, 221, plugin.z)).add(0.5, 0, 0.5));
		Iterator<Advancement> advancements = Bukkit.getServer().advancementIterator();

		while (advancements.hasNext()) {
			AdvancementProgress progress = player.getAdvancementProgress(advancements.next());
			for (String s : progress.getAwardedCriteria())
				progress.revokeCriteria(s);
		}
		prepare();
	}

	public void changeDisplayMode() {
		displayMode = (displayMode + 1) % 3;
		player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1, 1);
		updateShowBoard();
	}

	public void updateShowBoard() {
		for (int i = 0; i < 5; i++) {
			for (int j = 0; j < 5; j++) {
				ItemStack cur = new ItemStack(Material.BARRIER);
				{
					ItemMeta im = cur.getItemMeta();
					im.setDisplayName(ChatColor.RESET + "" + ChatColor.GOLD + "Completed/Unavailable");
					im.addEnchant(Enchantment.INFINITY, 1, true);
					im.addItemFlags(ItemFlag.HIDE_ENCHANTS);
					cur.setItemMeta(im);
				}

				if ((!isCollected[5 * i + j] || displayMode == 2) && plugin.boardItems[5 * i + j] != null) {
					cur = plugin.boardItems[5 * i + j].clone();
					if (displayMode == 1) {
						ItemMeta im = cur.getItemMeta();
						im.setDisplayName(ChatColor.RESET + "" + ChatColor.GOLD + "["
								+ String.valueOf(plugin.boardComplexities[5 * i + j]) + "]");
						cur.setItemMeta(im);
					}
					if (displayMode == 2) {
						ItemMeta im = cur.getItemMeta();
						im.setDisplayName(ChatColor.RESET + "" + ChatColor.GOLD + "("
								+ String.valueOf(plugin.rewardPoints[5 * i + j]) + ")");
						cur.setItemMeta(im);
					}
				}

				if (plugin.boardItems[5 * i + j] == null) {
					cur = null;
				}

				inv.setItem(9 * i + 2 + j, cur);
			}
		}
		{
			ItemStack temp = new ItemStack(Material.DEBUG_STICK);
			ItemMeta im = temp.getItemMeta();
			im.setDisplayName(ChatColor.RESET + "" + ChatColor.GOLD + "Change display mode");
			im.addEnchant(Enchantment.INFINITY, 1, true);
			im.addItemFlags(ItemFlag.HIDE_ENCHANTS);
			temp.setItemMeta(im);
			inv.setItem(8, temp);
		}
		{
			Material material = Material.NAME_TAG;
			String name = "Showing names";

			if (displayMode == 1) {
				material = Material.DIAMOND;
				name = "Showing rarities";
			}
			if (displayMode == 2) {
				material = Material.NETHER_STAR;
				name = "Showing score";
			}

			ItemStack temp = new ItemStack(material);
			ItemMeta im = temp.getItemMeta();
			im.setDisplayName(ChatColor.RESET + "" + ChatColor.GOLD + name);
			im.addEnchant(Enchantment.INFINITY, 1, true);
			im.addItemFlags(ItemFlag.HIDE_ENCHANTS);
			temp.setItemMeta(im);
			inv.setItem(17, temp);
		}
		player.updateInventory();
	}

	public void clear() {
		if (player.getOpenInventory().getTitle().contains("Bingo board"))
			player.closeInventory();
		score = 0;
		for (PotionEffect effect : player.getActivePotionEffects())
			player.removePotionEffect(effect.getType());
	}

	public void showCard() {
		updateShowBoard();

		player.openInventory(inv);
	}

	public void completeBoard() {
		hasWon = true;
		player.setGameMode(GameMode.SPECTATOR);
		if (player.getOpenInventory().getTitle().contains("Bingo board")) {
			player.closeInventory();
		}
		removeSpeed();

		plugin.finishBingo(this);
	}

	public void checkCompleteBoard() {
		if (plugin.gameType == 0) {
			List<Integer[]> allLines = plugin.getAllLines();
			for (Integer[] line : allLines) {
				boolean full = true;
				for (int i = 0; i < 5; i++) {
					if (!isCollected[line[i]]) {
						full = false;
					}
				}
				if (full) {
					completeBoard();
					return;
				}
			}
		}
		if (plugin.gameType == 2) {
			boolean full = true;
			for (int i = 0; i < 25; i++) {
				if (plugin.boardItems[i] != null && !isCollected[i]) {
					full = false;
				}
			}
			if (full) {
				completeBoard();
				return;
			}
		}
	}

	public void addItem(int num) {
		isCollected[num] = true;

		updateShowBoard();

		plugin.updateScore(this, num);

		checkCompleteBoard();
	}

	public void checkItem(int num) {
		if (plugin.boardItems[num] != null && !player.getGameMode().equals(GameMode.SPECTATOR)) {
			if (!isCollected[num]) {
				ItemStack item = plugin.boardItems[num];
				ItemStack[] inv = player.getInventory().getContents();
				for (int i = 0; i < 41; i++) {
					if (inv[i] != null) {
						boolean isInitItem = false;
						for (int j = 0; j < 41; j++) {
							if (plugin.playerInv[j] != null) {
								ItemStack temp1 = inv[i].clone(), temp2 = plugin.playerInv[j].clone();
								temp1.setAmount(1);
								temp2.setAmount(1);
								if (temp1.equals(temp2)) {
									isInitItem = true;
									break;
								}
							}
						}
						if (isInitItem)
							continue;

						if (inv[i].getType() == item.getType()) {
							inv[i].setAmount(inv[i].getAmount() - 1);
							addItem(num);
							return;
						}
					}
				}
			}
			player.playSound(player.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 1.0f, 0.0f);
		}
	}
}
