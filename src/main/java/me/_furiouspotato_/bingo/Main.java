package me._furiouspotato_.bingo;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

import me._furiouspotato_.bingo.commands.BingoCommand;
import me._furiouspotato_.bingo.tabcompleters.BingoCompleter;
import net.md_5.bungee.api.ChatColor;

public class Main extends JavaPlugin implements Listener {
	public ItemStack[] playerInv;

	public ItemStack bingoCard;

	boolean enableSpeedBonus;
	boolean enableFireResistanceEffect;
	boolean enableWaterBreathingEffect;
	boolean enableNightVisionEffect;
	boolean giveInitialItems;
	boolean preventFallDamage;
	boolean teleportBackOnDeath;
	boolean returnItemsOnDeath;
	boolean fixBingoCard;
	boolean showGlobalScore;
	String defaultWorldName;

	int countdownTime;
	int punishmentTime;
	int afterGameTime;

	//

	public HashMap<String, BingoPlayer> players;

	public int gameStatus;

	public int gameType;
	public int difficulty;

	World world;
	int x, z;

	ItemStack[] boardItems;
	Integer[] boardComplexities;
	Integer[] rewardPoints;

	List<String> finishOrder;

	File itemsFile;
	FileConfiguration itemsConfig;

	List<String> items;
	List<Integer> complexities;

	int time;
	Integer[][] gameDuration;
	int finishTime;

	Biome[] forbiddenBiomes;

	public HashMap<String, Integer> globalPoints;

	Scoreboard scoreboard;
	Objective infoObjective;
	Objective globalPointsObjective;

	@Override
	public void onEnable() {
		{
			File itemsFile = new File(getDataFolder() + File.separator + "items.yml");
			if (!itemsFile.exists()) {
				saveResource("items.yml", false);
			}

			File configFile = new File(getDataFolder() + File.separator + "config.yml");
			if (!configFile.exists()) {
				saveResource("config.yml", false);
			}
			reloadConfig();

			itemsFile = new File(getDataFolder() + File.separator + "items.yml");
			itemsConfig = YamlConfiguration.loadConfiguration(itemsFile);

			loadDatabaseConfig();
		}

		loadGameRules();

		//

		getCommand("bingo").setExecutor(new BingoCommand(this));
		getCommand("bingo").setTabCompleter(new BingoCompleter(this));
		Bukkit.getPluginManager().registerEvents(this, (Plugin) this);

		getBingoCard();

		playerInv = new ItemStack[41];
		playerInv[0] = new ItemStack(Material.NETHERITE_AXE, 1);
		playerInv[0].addUnsafeEnchantment(Enchantment.SHARPNESS, 5);
		playerInv[0].addUnsafeEnchantment(Enchantment.LOOTING, 3);
		playerInv[0].addUnsafeEnchantment(Enchantment.EFFICIENCY, 5);
		formatItemName(playerInv[0], "Potato Axe");
		playerInv[1] = new ItemStack(Material.NETHERITE_PICKAXE, 1);
		playerInv[1].addUnsafeEnchantment(Enchantment.EFFICIENCY, 5);
		playerInv[1].addUnsafeEnchantment(Enchantment.FORTUNE, 3);
		formatItemName(playerInv[1], "Potato Pickaxe");
		playerInv[2] = new ItemStack(Material.NETHERITE_SHOVEL, 1);
		playerInv[2].addUnsafeEnchantment(Enchantment.EFFICIENCY, 5);
		playerInv[2].addUnsafeEnchantment(Enchantment.SILK_TOUCH, 1);
		formatItemName(playerInv[2], "Potato Shovel");
		playerInv[3] = new ItemStack(Material.COOKED_PORKCHOP, 64);
		formatItemName(playerInv[3], "Baked Potato");
		playerInv[8] = bingoCard.clone();
		playerInv[36] = new ItemStack(Material.LEATHER_BOOTS, 1);
		playerInv[36].addUnsafeEnchantment(Enchantment.DEPTH_STRIDER, 3);
		formatItemName(playerInv[36], "Potato Boots");
		playerInv[39] = new ItemStack(Material.LEATHER_HELMET, 1);
		playerInv[39].addUnsafeEnchantment(Enchantment.AQUA_AFFINITY, 1);
		formatItemName(playerInv[39], "Potato Helmet");
		{
			int makeUnbreakable[] = { 0, 1, 2, 36, 39 };
			for (int i : makeUnbreakable) {
				ItemMeta itemMeta = playerInv[i].getItemMeta();
				itemMeta.setUnbreakable(true);
				itemMeta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
				playerInv[i].setItemMeta(itemMeta);
			}
		}

		forbiddenBiomes = new Biome[] { Biome.COLD_OCEAN, Biome.DEEP_COLD_OCEAN, Biome.DEEP_FROZEN_OCEAN,
				Biome.DEEP_LUKEWARM_OCEAN, Biome.DEEP_OCEAN, Biome.FROZEN_OCEAN,
				Biome.LUKEWARM_OCEAN, Biome.OCEAN, Biome.WARM_OCEAN };

		players = new HashMap<String, BingoPlayer>();
		finishOrder = new ArrayList<String>();

		gameStatus = -1;

		globalPoints = new HashMap<String, Integer>();
		for (Player player : Bukkit.getOnlinePlayers()) {
			globalPoints.put(player.getName(), 0);
		}

		scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
		infoObjective = scoreboard.registerNewObjective("info", "dummy", ChatColor.GOLD + "Bingo");
		globalPointsObjective = scoreboard.registerNewObjective("globalPoints", "dummy",
				ChatColor.GOLD + "Global Points");
		updateGlobalPoints();
	}

	void formatItemName(ItemStack item, String name) {
		ItemMeta itemMeta = item.getItemMeta();
		itemMeta.setDisplayName(ChatColor.RESET + name);
		item.setItemMeta(itemMeta);
	}

	public String getFormattedName(String initName) {
		char[] nameChars = initName.toLowerCase().replace('_', ' ').toCharArray();
		for (int i = 0; i < nameChars.length; i++) {
			if ((i == 0 || nameChars[i - 1] == ' ') && Character.isLetter(nameChars[i])) {
				nameChars[i] = Character.toUpperCase(nameChars[i]);
			}
		}
		return String.copyValueOf(nameChars);
	}

	void loadDatabaseConfig() {
		items = new ArrayList<String>();
		complexities = new ArrayList<Integer>();
		for (String rawData : itemsConfig.getStringList("items")) {
			String[] raw = rawData.split(":");

			if (!raw[1].equalsIgnoreCase("-1")) {
				items.add(raw[0]);
				complexities.add(Integer.valueOf(raw[1]));
			}
		}
	}

	public void loadGameRules() {
		reloadConfig();
		enableSpeedBonus = getConfig().getBoolean("enable-speed-bonus");
		enableFireResistanceEffect = getConfig().getBoolean("enable-fire-resistance-effect");
		enableWaterBreathingEffect = getConfig().getBoolean("enable-water-breathing-effect");
		enableNightVisionEffect = getConfig().getBoolean("enable-night-vision-effect");
		giveInitialItems = getConfig().getBoolean("give-initial-items");
		preventFallDamage = getConfig().getBoolean("prevent-fall-damage");
		teleportBackOnDeath = getConfig().getBoolean("teleport-back-on-death");
		returnItemsOnDeath = getConfig().getBoolean("return-items-on-death");
		fixBingoCard = getConfig().getBoolean("fix-bingo-card");
		showGlobalScore = getConfig().getBoolean("show-global-score");
		defaultWorldName = getConfig().getString("default-world-name");
		countdownTime = getConfig().getInt("countdown-time");
		punishmentTime = getConfig().getInt("punishment-time");
		afterGameTime = getConfig().getInt("after-game-time");
		gameDuration = new Integer[3][5];
		for (int i = 0; i < 5; i++)
			gameDuration[0][i] = getConfig().getInt("default-game-duration." + String.valueOf(i));
		for (int i = 0; i < 5; i++)
			gameDuration[1][i] = getConfig().getInt("butfast-game-duration." + String.valueOf(i));
		for (int i = 0; i < 5; i++)
			gameDuration[2][i] = getConfig().getInt("collectall-game-duration." + String.valueOf(i));
	}

	public void saveGameRules() {
		getConfig().set("enable-speed-bonus", enableSpeedBonus);
		getConfig().set("enable-fire-resistance-effect", enableFireResistanceEffect);
		getConfig().set("enable-water-breathing-effect", enableWaterBreathingEffect);
		getConfig().set("enable-night-vision-effect", enableNightVisionEffect);
		getConfig().set("give-initial-items", giveInitialItems);
		getConfig().set("prevent-fall-damage", preventFallDamage);
		getConfig().set("teleport-back-on-death", teleportBackOnDeath);
		getConfig().set("return-items-on-death", returnItemsOnDeath);
		getConfig().set("fix-bingo-card", fixBingoCard);
		for (int i = 0; i < 5; i++)
			getConfig().set("default-game-duration." + String.valueOf(i), gameDuration[0][i]);
		for (int i = 0; i < 5; i++)
			getConfig().set("butfast-game-duration." + String.valueOf(i), gameDuration[1][i]);
		for (int i = 0; i < 5; i++)
			getConfig().set("collectall-game-duration." + String.valueOf(i), gameDuration[2][i]);
		saveConfig();
	}

	public void setArcadeGameRules() {
		this.enableSpeedBonus = true;
		this.enableFireResistanceEffect = false;
		this.enableWaterBreathingEffect = true;
		this.enableNightVisionEffect = true;
		this.giveInitialItems = true;
		this.preventFallDamage = true;
		this.teleportBackOnDeath = true;
		this.returnItemsOnDeath = true;
		saveGameRules();
	}

	public void setRealisticGameRules() {
		this.enableSpeedBonus = false;
		this.enableFireResistanceEffect = false;
		this.enableWaterBreathingEffect = false;
		this.enableNightVisionEffect = false;
		this.giveInitialItems = false;
		this.preventFallDamage = false;
		this.teleportBackOnDeath = false;
		this.returnItemsOnDeath = false;
		saveGameRules();
	}

	void getBingoCard() {
		bingoCard = new ItemStack(Material.PAPER);
		ItemMeta im = bingoCard.getItemMeta();
		im.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "Bingo Card");
		im.addEnchant(Enchantment.INFINITY, 1, true);
		im.addItemFlags(ItemFlag.HIDE_ENCHANTS);
		bingoCard.setItemMeta(im);
	}

	public List<Integer[]> getAllLines() {
		List<Integer[]> res = new ArrayList<Integer[]>();

		for (int i = 0; i < 5; i++) {
			res.add(new Integer[5]);
			for (int j = 0; j < 5; j++) {
				res.get(res.size() - 1)[j] = 5 * i + j;
			}

			res.add(new Integer[5]);
			for (int j = 0; j < 5; j++) {
				res.get(res.size() - 1)[j] = 5 * j + i;
			}
		}

		res.add(new Integer[5]);
		for (int i = 0; i < 5; i++) {
			res.get(res.size() - 1)[i] = 5 * i + i;
		}

		res.add(new Integer[5]);
		for (int i = 0; i < 5; i++) {
			res.get(res.size() - 1)[i] = 5 * i + 4 - i;
		}

		return res;
	}

	void shuffleItems(List<String> items) {
		Random random = new Random();
		for (int k = 0; k < items.size(); k++) {
			int l = random.nextInt(items.size());
			String temp = items.get(k);
			items.set(k, items.get(l));
			items.set(l, temp);
		}
	}

	int prepareBoard() {
		int minComplexity, maxComplexity, minAverageComplexity, maxAverageComplexity, totalAverageComplexity;

		Integer[] values = null;
		if (difficulty == 0) {
			values = new Integer[] { -1, 25, -1, 20, 10 };
		}
		if (difficulty == 1) {
			values = new Integer[] { -1, 60, -1, 45, 32 };
		}
		if (difficulty == 2) {
			values = new Integer[] { 40, 100, 50, 90, 75 };
		}
		if (difficulty == 3) {
			values = new Integer[] { 80, 200, 90, 180, 150 };
		}
		if (difficulty == 4) {
			values = new Integer[] { 150, -1, 175, -1, 400 };
		}

		minComplexity = values[0];
		maxComplexity = values[1];
		minAverageComplexity = values[2];
		maxAverageComplexity = values[3];
		totalAverageComplexity = values[4];

		List<String> curItems = new ArrayList<String>();
		List<Integer> curComplexities = new ArrayList<Integer>();
		for (int i = 0; i < items.size(); i++) {
			if ((minComplexity == -1 || minComplexity <= complexities.get(i))
					&& (maxComplexity == -1 || complexities.get(i) <= maxComplexity)) {
				curItems.add(items.get(i));
				curComplexities.add(complexities.get(i));
			}
		}

		if ((gameType == 0 || gameType == 1) && curItems.size() < 25 || gameType == 2 && curItems.size() < 9)
			return 1;

		Random random = new Random();
		for (int i = 0; i < curItems.size(); i++) {
			int j = random.nextInt(i + 1);
			{
				String temp = curItems.get(i);
				curItems.set(i, curItems.get(j));
				curItems.set(j, temp);
			}
			{
				Integer temp = curComplexities.get(i);
				curComplexities.set(i, curComplexities.get(j));
				curComplexities.set(j, temp);
			}
		}

		Integer[] itemsNums = new Integer[25];
		int sumDist = 2147483647;

		for (int it = 0; it < 1000; it++) {
			Integer[] curNums = new Integer[25];
			Set<Integer> usedNums = new HashSet<Integer>();

			if (gameType == 0 || gameType == 1) {
				for (int i = 0; i < 25; i++) {
					int cur;
					do {
						cur = random.nextInt(curItems.size());
					} while (usedNums.contains(cur));
					curNums[i] = cur;
					usedNums.add(cur);
				}
			}
			if (gameType == 2) {
				for (int i = 0; i < 9; i++) {
					int cur;
					do {
						cur = random.nextInt(curItems.size());
					} while (usedNums.contains(cur));
					curNums[i] = cur;
					usedNums.add(cur);
				}
			}

			int curSumDist = 0;
			int sum;
			boolean good = true;

			if (gameType == 0) {
				List<Integer[]> allLines = getAllLines();
				for (Integer[] line : allLines) {
					sum = 0;
					for (int i = 0; i < 5; i++) {
						sum += curComplexities.get(curNums[line[i]]);
					}
					if (minAverageComplexity != -1 && sum < 5 * minAverageComplexity
							|| maxAverageComplexity != -1 && sum > 5 * maxAverageComplexity) {
						good = false;
						break;
					}
					if (totalAverageComplexity != -1) {
						curSumDist += (sum - totalAverageComplexity * 5) * (sum - totalAverageComplexity * 5);
					}
				}
			}
			if (gameType == 1) {
				sum = 0;
				for (int i = 0; i < 25; i++) {
					sum += curComplexities.get(i);
				}
				if (minAverageComplexity != -1 && sum < 25 * minAverageComplexity
						|| maxAverageComplexity != -1 && sum > 25 * maxAverageComplexity) {
					good = false;
				} else {
					curSumDist = Math.abs(sum - totalAverageComplexity * 25);
				}
			}
			if (gameType == 2) {
				sum = 0;
				for (int i = 0; i < 9; i++) {
					sum += curComplexities.get(i);
				}
				if (minAverageComplexity != -1 && sum < 25 * minAverageComplexity
						|| maxAverageComplexity != -1 && sum > 25 * maxAverageComplexity) {
					good = false;
				} else {
					curSumDist = Math.abs(sum - totalAverageComplexity * 25);
				}
			}

			if (good) {
				if (curSumDist < sumDist) {
					sumDist = curSumDist;
					itemsNums = curNums.clone();
				}
				if (curSumDist == 0) {
					break;
				}
			}
		}

		if (itemsNums[0] == null) {
			return 2;
		}

		boardItems = new ItemStack[25];
		boardComplexities = new Integer[25];
		rewardPoints = new Integer[25];
		if (gameType == 0 || gameType == 1) {
			for (int i = 0; i < 25; i++) {
				boardItems[i] = new ItemStack(Material.getMaterial(curItems.get(itemsNums[i])));
				boardComplexities[i] = curComplexities.get(itemsNums[i]);
			}
		}
		if (gameType == 2) {
			for (int i = 0; i < 3; i++) {
				for (int j = 0; j < 3; j++) {
					boardItems[5 * (i + 1) + (j + 1)] = new ItemStack(
							Material.getMaterial(curItems.get(itemsNums[3 * i + j])));
					boardComplexities[5 * (i + 1) + (j + 1)] = curComplexities.get(itemsNums[3 * i + j]);
				}
			}
		}

		return 0;
	}

	public void updateScore(BingoPlayer bplayer, int newItem) {
		bplayer.player.playSound(bplayer.player.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 1.f, 1.f);

		String itemName = getFormattedName(boardItems[newItem].getType().toString());
		String message = ChatColor.AQUA + bplayer.player.getName() + ChatColor.GOLD + " has collected "
				+ ChatColor.LIGHT_PURPLE + itemName;
		if (gameType == 0)
			message += ChatColor.GOLD + ".";
		if (gameType == 1)
			message += ChatColor.GOLD + " (" + ChatColor.GREEN + "+" + String.valueOf(rewardPoints[newItem])
					+ ChatColor.GOLD + ").";
		if (gameType == 2)
			message += ChatColor.GOLD + ".";
		for (Player player : Bukkit.getOnlinePlayers()) {
			player.sendMessage(message);
			if (!player.getName().equalsIgnoreCase(bplayer.player.getName())) {
				player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_BURP, 1.f, 0.f);
			}
		}

		if (gameType == 0) {
			bplayer.score = 0;

			List<Integer[]> allLines = getAllLines();
			for (Integer[] line : allLines) {
				int cur = 0;
				for (int i = 0; i < 5; i++) {
					if (bplayer.isCollected[line[i]]) {
						cur++;
					}
				}
				if (cur > bplayer.score)
					bplayer.score = cur;
			}
		}
		if (gameType == 1) {
			bplayer.score += rewardPoints[newItem];
		}
		if (gameType == 2) {
			bplayer.score = 0;

			for (int i = 0; i < 25; i++) {
				if (boardItems[i] != null && bplayer.isCollected[i]) {
					bplayer.score++;
				}
			}
		}

		updateRewardPoints(newItem);

		updateScoreboard();
	}

	int getRandCoord() {
		return (int) (25000 * (Math.random() * 2 - 1));
	}

	void getRandCoords() {
		boolean good;
		int counter = 1000;
		do {
			x = getRandCoord();
			z = getRandCoord();

			good = x * x + z * z <= 25000 * 25000;
			if (good) {
				for (int i = 0; i < forbiddenBiomes.length; i++) {
					if (forbiddenBiomes[i].equals(world.getBiome(x, 63, z))) {
						good = false;
						break;
					}
				}
			}
			counter--;
		} while (!good && counter >= 0);
	}

	public int setupGame(int gameType, int difficulty) {
		if (gameType == -1 || difficulty == -1)
			return -1;

		clearScoreboard();
		gameStatus = 2;
		this.gameType = gameType;
		this.difficulty = difficulty;

		int result = prepareBoard();
		if (result != 0) {
			gameStatus = -1;
			return result;
		}

		boolean found = false;
		for (World world : Bukkit.getWorlds()) {
			if (world.getName().equals(defaultWorldName)) {
				this.world = world;
				found = true;
				break;
			}
		}

		if (!found) {
			for (World world : Bukkit.getWorlds()) {
				if (!world.getName().contains("nether") && !world.getName().contains("the_end")) {
					this.world = world;
					found = true;
					break;
				}
			}
		}

		if (!found) {
			for (World world : Bukkit.getWorlds()) {
				this.world = world;
				break;
			}
		}

		getRandCoords();

		String modeString = Arrays.asList("5 In a Row", "But Fast", "Collect All").get(gameType);
		String difficultyString = Arrays.asList("Baby", "Easy", "Medium", "Hard", "Insane").get(difficulty);

		for (Player player : Bukkit.getOnlinePlayers()) {
			player.sendMessage(ChatColor.GOLD + "New Bingo game is going to start soon!");
			player.sendMessage(ChatColor.GOLD + "Mode: " + ChatColor.DARK_GREEN + modeString + ChatColor.GOLD
					+ ", difficulty: " + ChatColor.DARK_GREEN + difficultyString + ChatColor.GOLD + ".");
			player.sendMessage(ChatColor.GOLD + "Type " + ChatColor.DARK_AQUA + "/bingo join" + ChatColor.RESET
					+ ChatColor.GOLD + " to join.");
		}

		return 0;
	}

	void updateRewardPoints(int i) {
		int cnt = 0;
		for (Map.Entry<String, BingoPlayer> entry : players.entrySet()) {
			BingoPlayer bplayer = entry.getValue();
			if (bplayer.isCollected[i]) {
				cnt++;
			}
		}
		if (players.size() > 0)
			rewardPoints[i] = (players.size() - cnt) * (60 / players.size());

		for (Map.Entry<String, BingoPlayer> entry : players.entrySet()) {
			BingoPlayer bplayer = entry.getValue();
			bplayer.updateShowBoard();
		}
	}

	public void addPlayer(String playerName) {
		if (!players.containsKey(playerName)) {
			BingoPlayer player = new BingoPlayer(Bukkit.getPlayerExact(playerName), this);
			players.put(playerName, player);

			if (gameStatus == 0) {
				player.reset();
			}
			for (int i = 0; i < 25; i++) {
				updateRewardPoints(i);
			}

			updateGlobalPoints();

			for (Player player2 : Bukkit.getOnlinePlayers()) {
				player2.sendMessage(ChatColor.GOLD + "Player " + ChatColor.AQUA + playerName + ChatColor.GOLD
						+ " has joined the game.");
			}
		}
	}

	public void removePlayer(String playerName) {
		if (players.containsKey(playerName)) {
			players.remove(playerName);

			for (int i = 0; i < 25; i++) {
				updateRewardPoints(i);
			}

			for (Player player2 : Bukkit.getOnlinePlayers()) {
				player2.sendMessage(ChatColor.GOLD + "Player " + ChatColor.AQUA + playerName + ChatColor.GOLD
						+ " has left the game.");
			}

			if (Bukkit.getPlayerExact(playerName) != null) {
				Bukkit.getPlayerExact(playerName).setGameMode(GameMode.SPECTATOR);
			}
		}
	}

	void fill(int x1, int y1, int z1, int x2, int y2, int z2, World world, Material material) {
		if (x1 > x2) {
			int t = x1;
			x1 = x2;
			x2 = t;
		}
		if (y1 > y2) {
			int t = y1;
			y1 = y2;
			y2 = t;
		}
		if (z1 > z2) {
			int t = z1;
			z1 = z2;
			z2 = t;
		}

		for (int i = x1; i <= x2; i++) {
			for (int j = y1; j <= y2; j++) {
				for (int k = z1; k <= z2; k++) {
					world.getBlockAt(i, j, k).setType(material);
				}
			}
		}
	}

	public void startGame() {
		if (gameStatus != 2)
			return;

		gameStatus = 0;

		fill(x - 2, 220, z - 2, x + 2, 220, z + 2, world, Material.GLASS);
		fill(x - 2, 224, z - 2, x + 2, 224, z + 2, world, Material.GLASS);

		fill(x - 2, 220, z - 2, x + 2, 224, z - 2, world, Material.GLASS);
		fill(x - 2, 220, z - 2, x - 2, 224, z + 2, world, Material.GLASS);
		fill(x + 2, 220, z + 2, x + 2, 224, z - 2, world, Material.GLASS);
		fill(x + 2, 220, z + 2, x - 2, 224, z + 2, world, Material.GLASS);

		fill(x - 2, 230, z - 2, x + 2, 230, z + 2, world, Material.BEDROCK);
		fill(x - 2, 235, z - 2, x + 2, 235, z + 2, world, Material.BEDROCK);

		fill(x - 2, 230, z - 2, x + 2, 235, z - 2, world, Material.BEDROCK);
		fill(x - 2, 230, z - 2, x - 2, 235, z + 2, world, Material.BEDROCK);
		fill(x + 2, 230, z + 2, x + 2, 235, z - 2, world, Material.BEDROCK);
		fill(x + 2, 230, z + 2, x - 2, 235, z + 2, world, Material.BEDROCK);

		fill(x - 1, 234, z - 1, x + 1, 234, z + 1, world, Material.GLOWSTONE);

		finishOrder.clear();

		world.setTime(12000);
		world.setStorm(false);

		for (Map.Entry<String, BingoPlayer> entry : players.entrySet()) {
			BingoPlayer bplayer = entry.getValue();
			bplayer.reset();
			bplayer.allowBreaking = false;
		}

		for (int i = 0; i < 25; i++) {
			updateRewardPoints(i);
		}

		for (Player player : (List<Player>) Bukkit.getOnlinePlayers()) {
			if (!players.containsKey(player.getName())) {
				player.setGameMode(GameMode.SPECTATOR);
				player.teleport((new Location(world, x, 255, z)).add(0.5, 0, 0.5));
			}
		}

		String startMessage = ChatColor.GOLD + "The Bingo game is starting in " + String.valueOf(countdownTime)
				+ " second";
		if (countdownTime != 1) {
			startMessage += "s";
		}
		startMessage += "!";
		for (Player player : (List<Player>) Bukkit.getOnlinePlayers()) {
			player.sendMessage(startMessage);
			player.sendMessage(ChatColor.GOLD + "Starting biome: " + ChatColor.GRAY
					+ getFormattedName(world.getBiome(x, 63, z).toString()) + ChatColor.GOLD + ".");
		}

		Plugin plugin = (Plugin) this;
		Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
			public void run() {
				for (Player player : (List<Player>) Bukkit.getOnlinePlayers()) {
					player.sendMessage(ChatColor.GOLD + "The Bingo game is starting now!");
					player.playSound(player.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 0.5f, 0.5f);
				}
				for (Map.Entry<String, BingoPlayer> entry : players.entrySet()) {
					BingoPlayer bplayer = entry.getValue();
					bplayer.allowBreaking = true;
				}
				fill(x - 1, 220, z - 1, x + 1, 220, z + 1, world, Material.AIR);

				time = -1;
				finishTime = gameDuration[gameType][difficulty];

				Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
					public void run() {
						time++;

						if (time == finishTime) {
							endGame(false, false);
							return;
						}

						if ((finishTime - time) % 600 == 0
								|| (finishTime - time) % 60 == 0 && (finishTime - time) / 60 <= 5
								|| finishTime - time <= 30 && (finishTime - time) % 10 == 0
								|| (finishTime - time) <= 5) {
							String endMessage = ChatColor.GOLD + "The Bingo game is ending in ";

							if ((finishTime - time) % 60 == 0) {
								endMessage += ChatColor.GREEN + "" + Integer.valueOf((finishTime - time) / 60)
										+ ChatColor.GOLD + " minute";
								if ((finishTime - time) / 60 != 1)
									endMessage += "s";
							} else {
								endMessage += ChatColor.GREEN + "" + Integer.valueOf(finishTime - time) + ChatColor.GOLD
										+ " second";
								if (finishTime - time != 1)
									endMessage += "s";
							}
							endMessage += "!";

							for (Player player : (List<Player>) Bukkit.getOnlinePlayers()) {
								player.sendMessage(endMessage);
							}
						}

						updateScoreboard();
					}
				}, 0L, 20L);
			}
		}, 20L * countdownTime);
	}

	String getTimeString(int time) {
		String res = new String();
		{
			String hours = String.valueOf(time / 3600);
			if (hours.length() < 2)
				hours = "0" + hours;
			res += hours + ":";
		}
		{
			String minutes = String.valueOf(time / 60 % 60);
			if (minutes.length() < 2)
				minutes = "0" + minutes;
			res += minutes + ":";
		}
		{
			String seconds = String.valueOf(time % 60);
			if (seconds.length() < 2)
				seconds = "0" + seconds;
			res += seconds;
		}

		return res;
	}

	public void updateScoreboard() {
		infoObjective.unregister();
		infoObjective = scoreboard.registerNewObjective("info", "dummy", ChatColor.GOLD + "Bingo");
		infoObjective.setDisplaySlot(DisplaySlot.SIDEBAR);

		{
			String name = ChatColor.GOLD + "Time: " + ChatColor.GREEN + getTimeString(time);
			if (gameDuration[gameType][difficulty] != -1) {
				name += ChatColor.GOLD + "/" + ChatColor.GREEN + getTimeString(gameDuration[gameType][difficulty]);
			}

			Score score = infoObjective.getScore(name);
			score.setScore(players.size());
		}

		Map.Entry<String, BingoPlayer>[] scoreboard2 = (Map.Entry<String, BingoPlayer>[]) new Map.Entry[players
				.entrySet().size()];
		players.entrySet().toArray(scoreboard2);
		Arrays.sort(scoreboard2, new BingoPlayerComparator());

		int counter = players.size() - 1;
		int place = 1, t = 0, old = -1;
		for (Map.Entry<String, BingoPlayer> entry : scoreboard2) {
			BingoPlayer bplayer = entry.getValue();

			if (bplayer.score != old) {
				place += t;
				t = 0;
				old = bplayer.score;
			}
			t++;

			String name = ChatColor.GOLD + String.valueOf(place) + ". " + ChatColor.AQUA + bplayer.player.getName()
					+ ChatColor.GOLD + ": " + String.valueOf(bplayer.score);

			Score score = infoObjective.getScore(name);
			score.setScore(counter--);
		}

		for (Player player : (List<Player>) Bukkit.getOnlinePlayers()) {
			player.setScoreboard(scoreboard);
		}
	}

	public void clearScoreboard() {
		for (Player player : (List<Player>) Bukkit.getOnlinePlayers()) {
			scoreboard.clearSlot(DisplaySlot.SIDEBAR);
		}
	}

	public void updateGlobalPoints() {
		if (!showGlobalScore)
			return;

		globalPointsObjective.unregister();
		globalPointsObjective = scoreboard.registerNewObjective("globalPoints", "dummy",
				ChatColor.GOLD + "Global Points");
		globalPointsObjective.setDisplaySlot(DisplaySlot.PLAYER_LIST);

		for (Map.Entry<String, Integer> entry : globalPoints.entrySet()) {
			Score score = globalPointsObjective.getScore(entry.getKey());
			score.setScore(entry.getValue());
		}

		for (Player player : (List<Player>) Bukkit.getOnlinePlayers()) {
			player.setScoreboard(scoreboard);
		}
	}

	public void clearGlobalPoints() {
		scoreboard.clearSlot(DisplaySlot.PLAYER_LIST);

		for (Player player : (List<Player>) Bukkit.getOnlinePlayers()) {
			player.setScoreboard(scoreboard);
		}
	}

	public void setGlobalScore(String nickname, Integer score) {
		globalPoints.put(nickname, score);
		updateGlobalPoints();
	}

	public void toggleGlobalScore() {
		showGlobalScore = !showGlobalScore;
		if (!showGlobalScore) {
			clearGlobalPoints();
		} else {
			updateGlobalPoints();
		}

		getConfig().set("show-global-score", showGlobalScore);
		saveConfig();
	}

	public void endGame(boolean clearPlayers, boolean fromCommand) {
		if (gameStatus == -1)
			return;

		for (Player player : (List<Player>) Bukkit.getOnlinePlayers()) {
			player.sendMessage(ChatColor.GOLD + "The current Bingo game has been finished.");
		}

		if (!fromCommand) {
			Map.Entry<String, BingoPlayer>[] scoreboard = (Map.Entry<String, BingoPlayer>[]) new Map.Entry[players
					.entrySet().size()];
			players.entrySet().toArray(scoreboard);
			Arrays.sort(scoreboard, new BingoPlayerComparator());

			if (gameStatus == 0) {
				for (Player player : (List<Player>) Bukkit.getOnlinePlayers()) {
					player.sendMessage(ChatColor.GOLD + "Scoreboard:");
					int place = 1, t = 0, old = -1;
					for (Map.Entry<String, BingoPlayer> entry : scoreboard) {
						BingoPlayer bplayer = entry.getValue();

						if (old != bplayer.score) {
							place += t;
							t = 0;
							old = bplayer.score;
						}
						t++;

						player.sendMessage(ChatColor.GOLD + String.valueOf(place) + ". " + ChatColor.AQUA
								+ bplayer.player.getName() + ChatColor.GOLD + ": " + bplayer.score);
					}
					player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
				}
			}

			if (gameType == 0 || gameType == 2) {
				int curScore = players.size() - 1;
				for (int i = 0; i < finishOrder.size(); i++) {
					globalPoints.put(finishOrder.get(i), globalPoints.get(finishOrder.get(i)) + curScore);
					curScore--;
				}
			} else {
				int curScore = scoreboard.length - 1, t = 0, old = -1;
				for (Map.Entry<String, BingoPlayer> entry : scoreboard) {
					BingoPlayer bplayer = entry.getValue();

					if (old != bplayer.score) {
						curScore -= t;
						t = 0;
						old = bplayer.score;
					}
					t++;

					globalPoints.put(bplayer.player.getName(), globalPoints.get(bplayer.player.getName()) + curScore);
				}
			}
			updateGlobalPoints();
		}

		gameStatus = 2;

		fill(x - 2, 220, z - 2, x + 2, 235, z + 2, world, Material.AIR);

		clearScoreboard();

		for (Map.Entry<String, BingoPlayer> entry : players.entrySet()) {
			BingoPlayer bplayer = entry.getValue();
			bplayer.clear();
		}

		Bukkit.getScheduler().cancelTasks((Plugin) this);

		if (clearPlayers) {
			players.clear();
			gameStatus = -1;
		} else {
			setupGame(this.gameType, this.difficulty);
		}
	}

	public void finishBingo(BingoPlayer bplayer) {
		for (Player player : Bukkit.getOnlinePlayers()) {
			player.sendMessage(
					ChatColor.AQUA + bplayer.player.getName() + ChatColor.GOLD + " has cleared their Bingo Card in "
							+ ChatColor.GREEN + getTimeString(time) + ChatColor.GOLD + "!");
			player.playSound(player.getLocation(), Sound.BLOCK_BELL_USE, 1.f, 1.f);
		}

		finishOrder.add(bplayer.player.getName());

		int alreadyFinished = 0;
		for (Map.Entry<String, BingoPlayer> entry : players.entrySet()) {
			BingoPlayer bplayer2 = entry.getValue();
			alreadyFinished += bplayer2.hasWon ? 1 : 0;
		}

		if (alreadyFinished == players.size() - 1 || players.size() == 1) {
			finishTime = time + afterGameTime + 1;
		}
	}

	public void returnCard(BingoPlayer player) {
		player.returnCard();
	}

	@EventHandler
	private void onPlayerLogin(PlayerLoginEvent e) {
		Bukkit.getScheduler().runTaskLater((Plugin) this, new Runnable() {
			public void run() {
				if (!globalPoints.containsKey(e.getPlayer().getName())) {
					globalPoints.put(e.getPlayer().getName(), 0);
					updateGlobalPoints();
				}
			}
		}, 100L);

		if (gameStatus == -1)
			return;

		if (!players.containsKey(e.getPlayer().getName())) {
			if (gameStatus == 0) {
				Player player = e.getPlayer();
				Bukkit.getScheduler().runTaskLater((Plugin) this, new Runnable() {
					public void run() {
						if (player.isOnline()) {
							player.setGameMode(GameMode.SPECTATOR);
							player.teleport((new Location(world, x, 255, z)).add(0.5, 0, 0.5));
						}
					}
				}, 100L);
			}
		} else {
			players.get(e.getPlayer().getName()).player = e.getPlayer();
		}
	}

	@EventHandler
	private void onPlayerTakeDamage(EntityDamageEvent e) {
		if (gameStatus != 0)
			return;

		BingoPlayer bplayer = null;
		if (e.getEntity() instanceof Player)
			bplayer = players.get(e.getEntity().getName());

		if (e.getCause() == DamageCause.FALL) {
			if (bplayer != null && (preventFallDamage || time - bplayer.lastDeathTime <= punishmentTime + 10)) {
				e.setCancelled(true);
			}
		}
	}

	@EventHandler
	public void onPlayerDeath(PlayerDeathEvent e) {
		if (gameStatus != 0)
			return;

		Player p = e.getEntity();
		if (players.containsKey(p.getName())) {
			BingoPlayer bplayer = players.get(p.getName());
			DamageCause dc = p.getLastDamageCause().getCause();
			Location deathLocation = p.getLocation();

			for (Player player : Bukkit.getOnlinePlayers()) {
				player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_DEATH, 1.f, 1.f);
			}

			p.spigot().respawn();
			p.teleport(new Location(world, x, 231, z));
			bplayer.allowBreaking = false;

			ItemStack[] playerInv = p.getInventory().getContents().clone();

			e.getDrops().clear();

			String deathMessage = ChatColor.GOLD + "You have died! You will be teleported ";
			if (!teleportBackOnDeath) {
				deathMessage += "on the spawn";
			} else {
				deathMessage += "back";
			}
			deathMessage += " in " + ChatColor.AQUA + String.valueOf(punishmentTime) + ChatColor.GOLD + " second";
			if (punishmentTime != 1) {
				deathMessage += "s";
			}
			deathMessage += "!";
			p.sendMessage(deathMessage);

			Bukkit.getScheduler().runTaskLater((Plugin) this, new Runnable() {
				public void run() {
					bplayer.lastDeathTime = time;

					p.spigot().respawn();
					bplayer.prepare();
					if (!enableFireResistanceEffect && (dc == DamageCause.FIRE || dc == DamageCause.FIRE_TICK
							|| dc == DamageCause.HOT_FLOOR || dc == DamageCause.LAVA || dc == DamageCause.MELTING)) {
						p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 20 * 20, 0, false, false));
					}
					p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 20 * 10, 127, false, false));

					if (returnItemsOnDeath) {
						bplayer.player.getInventory().setContents(playerInv);
						bplayer.checkHoldingBingoCard();
					}

					bplayer.allowBreaking = true;
					if (teleportBackOnDeath) {
						p.teleport(deathLocation);
					} else {
						p.teleport(new Location(world, x, 221, z));
					}
				}
			}, 20L * punishmentTime);
		}
	}

	@EventHandler
	public void onPlayerRespawn(PlayerRespawnEvent e) {
		if (gameStatus != 0) {
			return;
		}

		Player p = e.getPlayer();
		if (players.containsKey(p.getName())) {
			BingoPlayer bplayer = players.get(p.getName());
			bplayer.prepare();
		}
	}

	@EventHandler
	public void onSwitchItem(PlayerSwapHandItemsEvent e) {
		if (gameStatus != 0)
			return;

		if (players.containsKey(e.getPlayer().getName())) {
			BingoPlayer bplayer = players.get(e.getPlayer().getName());
			if (fixBingoCard) {
				if (e.getMainHandItem().equals(bingoCard) || e.getOffHandItem().equals(bingoCard))
					e.setCancelled(true);
			} else {
				Bukkit.getScheduler().runTaskLater((Plugin) this, new Runnable() {
					public void run() {
						bplayer.checkHoldingBingoCard();
					}
				}, 1L);
			}
		}
	}

	@EventHandler
	public void onPlayerInventoryClick(InventoryClickEvent e) {
		if (gameStatus != 0)
			return;

		if (e.getWhoClicked() instanceof Player && players.containsKey(e.getWhoClicked().getName())) {
			if (e.getView().getTitle().contains("Bingo board")) {
				int x, y;
				x = e.getSlot() / 9;
				y = e.getSlot() % 9 - 2;
				if (e.getClickedInventory().getType() != InventoryType.PLAYER) {
					BingoPlayer bplayer = players.get(e.getWhoClicked().getName());
					if (0 <= x && x < 5 && 0 <= y && y < 5) {
						bplayer.checkItem(x * 5 + y);
					}
					if (x == 0 && y == 6) {
						bplayer.changeDisplayMode();
					}
				}

				e.setCancelled(true);
				return;
			}

			BingoPlayer bplayer = players.get(e.getWhoClicked().getName());
			if (fixBingoCard) {
				List<ItemStack> items = new ArrayList<>();
				items.add(e.getCurrentItem());
				items.add(e.getCursor());
				if (e.getClick() == org.bukkit.event.inventory.ClickType.NUMBER_KEY) {
					items.add(e.getWhoClicked().getInventory().getItem(e.getHotbarButton()));
				}

				for (ItemStack item : items) {
					if (item != null && item.equals(bingoCard)) {
						e.setCancelled(true);
						return;
					}
				}
			} else {
				Bukkit.getScheduler().runTaskLater((Plugin) this, new Runnable() {
					public void run() {
						bplayer.checkHoldingBingoCard();
					}
				}, 1L);
			}
		}
	}

	@EventHandler
	public void onDrop(PlayerDropItemEvent e) {
		if (gameStatus != 0)
			return;

		if (players.containsKey(e.getPlayer().getName())) {
			BingoPlayer bplayer = players.get(e.getPlayer().getName());
			if (fixBingoCard) {
				if (players.containsKey(e.getPlayer().getName()) && e.getItemDrop().getItemStack().equals(bingoCard)) {
					e.setCancelled(true);
				}
			} else {
				Bukkit.getScheduler().runTaskLater((Plugin) this, new Runnable() {
					public void run() {
						bplayer.checkHoldingBingoCard();
					}
				}, 1L);
			}
		}
	}

	@EventHandler
	public void onEntityPinkupItem(EntityPickupItemEvent e) {
		if (gameStatus != 0)
			return;

		if (e.getEntity() instanceof Player && players.containsKey(e.getEntity().getName())) {
			BingoPlayer bplayer = players.get(e.getEntity().getName());
			Bukkit.getScheduler().runTaskLater((Plugin) this, new Runnable() {
				public void run() {
					bplayer.checkHoldingBingoCard();
				}
			}, 1L);
		}
	}

	@EventHandler
	public void onPlayerCLick(PlayerInteractEvent e) {
		if (gameStatus != 0)
			return;

		if (players.containsKey(e.getPlayer().getName())) {
			BingoPlayer bplayer = players.get(e.getPlayer().getName());

			if (bplayer.isHoldingBingoCard() || bplayer.player.getGameMode().equals(GameMode.SPECTATOR)) {
				if (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK
						|| bplayer.player.getGameMode().equals(GameMode.SPECTATOR)
								&& (e.getAction() == Action.LEFT_CLICK_AIR
										|| e.getAction() == Action.LEFT_CLICK_BLOCK)) {
					bplayer.showCard();
					e.setCancelled(true);
				}
			}
		}
	}

	@EventHandler
	public void onHoldBingoCard(PlayerItemHeldEvent e) {
		if (gameStatus != 0)
			return;

		Player p = e.getPlayer();
		if (players.containsKey(p.getName())) {
			BingoPlayer bplayer = players.get(p.getName());

			if (bingoCard.equals(p.getInventory().getContents()[e.getNewSlot()])) {
				bplayer.addSpeed();
			} else {
				bplayer.removeSpeed();
			}
		}
	}

	@EventHandler
	public void onEntityDamage(EntityDamageByEntityEvent e) {
		if (gameStatus != 0)
			return;
		if (!(e.getDamager() instanceof Player))
			return;
		if (!(e.getEntity() instanceof Player))
			return;

		if (!players.containsKey(e.getEntity().getName()) || !players.containsKey(e.getDamager().getName()))
			return;
		e.setCancelled(true);
	}

	@EventHandler
	private void onBlockPlace(BlockPlaceEvent e) {
		if (gameStatus != 0)
			return;
		if (!players.containsKey(e.getPlayer().getName()))
			return;

		BingoPlayer bplayer = players.get(e.getPlayer().getName());
		if (!bplayer.allowBreaking) {
			e.setCancelled(true);
		}
	}

	@EventHandler
	private void onBlockBreak(BlockBreakEvent e) {
		if (gameStatus != 0)
			return;
		if (!players.containsKey(e.getPlayer().getName()))
			return;

		BingoPlayer bplayer = players.get(e.getPlayer().getName());
		if (!bplayer.allowBreaking) {
			e.setCancelled(true);
		}
	}
}
