package me._furiouspotato_.bingo.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import me._furiouspotato_.bingo.BingoPlayer;
import me._furiouspotato_.bingo.Main;

public class BingoCommand implements CommandExecutor {
	private Main plugin;

	public BingoCommand(Main plugin) {
		this.plugin = plugin;
	}

	List<String> getBingoPlayersNames() {
		List<String> res = new ArrayList<String>();

		for (Map.Entry<String, BingoPlayer> entry : plugin.players.entrySet()) {
			BingoPlayer bplayer = entry.getValue();
			res.add(bplayer.player.getName());
		}

		return res;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if (sender instanceof Player) {
			Player player = (Player) sender;

			if (args.length == 0 || args.length == 1 && args[0].equalsIgnoreCase("help")) {
				sender.sendMessage(ChatColor.GOLD + "The bingo command help menu:");
				sender.sendMessage(ChatColor.DARK_AQUA + "/bingo setup [mode]" + ChatColor.GOLD
						+ " - setup a new game with the given game mode.");
				sender.sendMessage(ChatColor.DARK_AQUA + "/bingo join [player]" + ChatColor.GOLD
						+ " - join the current game or force player to join.");
				sender.sendMessage(ChatColor.DARK_AQUA + "/bingo leave [player]" + ChatColor.GOLD
						+ " - leave the current game or force player to leave.");
				sender.sendMessage(ChatColor.DARK_AQUA + "/bingo start" + ChatColor.GOLD + " - start a new game.");
				sender.sendMessage(ChatColor.DARK_AQUA + "/bingo quickstart" + ChatColor.GOLD
						+ " - start a new game with all online players without setting it up first.");
				sender.sendMessage(ChatColor.DARK_AQUA + "/bingo end" + ChatColor.GOLD + " - end the current game.");
				sender.sendMessage(ChatColor.DARK_AQUA + "/bingo list" + ChatColor.GOLD
						+ " - show the list of all the players in the current game.");
				sender.sendMessage(ChatColor.DARK_AQUA + "/bingo returncard" + ChatColor.GOLD
						+ " - return the Bingo Card if lost.");
				return false;
			}

			if (args[0].equalsIgnoreCase("setup")) {
				if (1 <= args.length && args.length <= 3) {
					if (player.hasPermission("bingo.operator")) {
						if (plugin.gameStatus != -1) {
							sender.sendMessage(ChatColor.RED + "The game is already running!");

							return false;
						}

						String mode = "default", difficulty = "easy";
						if (args.length >= 2) {
							mode = args[1];
						}
						if (args.length >= 3) {
							difficulty = args[2];
						}

						int result = setupGame(mode, difficulty);

						if (result == 1) {
							sender.sendMessage(
									ChatColor.RED + "Failed to start game! Not enough available Bingo items!");

							return false;
						}

						if (result == 2) {
							sender.sendMessage(
									ChatColor.RED + "Failed to start game! No valid Bingo Card combination found!");

							return false;
						}

						if (result == 0) {
							return false;
						}
					} else {
						sender.sendMessage(ChatColor.DARK_RED + "You don't have permission!");
						return true;
					}
				}
			}

			if (args[0].equalsIgnoreCase("join") || args[0].equalsIgnoreCase("leave")) {
				if (args.length == 1) {
					if (plugin.gameStatus == -1) {
						sender.sendMessage(ChatColor.RED + "The game has not been started yet.");

						return false;
					}
					if (args[0].equalsIgnoreCase("join")) {
						plugin.addPlayer(player.getName());
					} else {
						plugin.removePlayer(player.getName());
					}

					return false;
				} else {
					if (args.length == 2) {
						if (player.hasPermission("bingo.operator")) {
							if (plugin.gameStatus == -1) {
								sender.sendMessage(ChatColor.RED + "The game has not been started yet.");

								return false;
							}
							boolean found = false;
							if (args[0].equalsIgnoreCase("join")) {
								for (Player player2 : Bukkit.getOnlinePlayers()) {
									if (args[1].equalsIgnoreCase("*") || args[1].equalsIgnoreCase(player2.getName())) {
										if (!plugin.players.containsKey(player2.getName())) {
											plugin.addPlayer(player2.getName());
											found = true;
										}
										found = true;
									}
								}
							} else {
								List<String> names = getBingoPlayersNames();
								for (String name : names) {
									if (args[1].equalsIgnoreCase("*") || args[1].equalsIgnoreCase(name)) {
										plugin.removePlayer(name);
										found = true;
									}
								}
							}

							if (!found) {
								if (args[0].equalsIgnoreCase("join")) {
									if (!args[1].equalsIgnoreCase("*")) {
										sender.sendMessage(ChatColor.RED + "The player " + args[1]
												+ " is not found or he is already in game!");
									} else {
										sender.sendMessage(ChatColor.RED + "There are no players not in game!");
									}
								} else {
									if (!args[1].equalsIgnoreCase("*")) {
										sender.sendMessage(
												ChatColor.RED + "The player " + args[1] + " is not in game!");
									} else {
										sender.sendMessage(ChatColor.RED + "There are no players in game!");
									}
								}
							}

							return false;
						} else {
							sender.sendMessage(ChatColor.DARK_RED + "You don't have permission!");
							return true;
						}
					}
				}
			}

			if (args[0].equalsIgnoreCase("start")) {
				if (args.length == 1) {
					if (player.hasPermission("bingo.operator")) {
						if (plugin.gameStatus != 2) {
							if (plugin.gameStatus == 0) {
								sender.sendMessage(ChatColor.RED + "The game is already running!");
							} else {
								sender.sendMessage(ChatColor.RED + "The game has not been started yet!");
							}

							return false;
						}

						plugin.startGame();

						return false;
					} else {
						sender.sendMessage(ChatColor.DARK_RED + "You don't have permission!");
						return true;
					}
				}
			}

			if (args[0].equalsIgnoreCase("quickstart")) {
				if (1 <= args.length && args.length <= 3) {
					if (player.hasPermission("bingo.operator")) {
						if (plugin.gameStatus != -1) {
							sender.sendMessage(ChatColor.RED + "The game is already running!");

							return false;
						}

						String mode = "default", difficulty = "easy";
						if (args.length >= 2) {
							mode = args[1];
						}
						if (args.length >= 3) {
							difficulty = args[2];
						}

						int result = setupGame(mode, difficulty);

						if (result == 1) {
							sender.sendMessage(
									ChatColor.RED + "Failed to start game! Not enough available Bingo items!");

							return false;
						}

						if (result == 2) {
							sender.sendMessage(
									ChatColor.RED + "Failed to start game! No valid Bingo Card combination found!");

							return false;
						}

						if (result == 0) {
							for (Player player2 : Bukkit.getOnlinePlayers()) {
								if (!plugin.players.containsKey(player2.getName())) {
									plugin.addPlayer(player2.getName());
								}
							}

							plugin.startGame();

							return false;
						}
					} else {
						sender.sendMessage(ChatColor.DARK_RED + "You don't have permission!");
						return true;
					}
				}
			}

			if (args[0].equalsIgnoreCase("end")) {
				if (args.length == 1 || args.length == 2
						&& (args[1].equalsIgnoreCase("false") || args[1].equalsIgnoreCase("true"))) {
					if (player.hasPermission("bingo.operator")) {
						if (plugin.gameStatus == -1) {
							sender.sendMessage(ChatColor.RED + "The game is not running.");

							return false;
						}

						plugin.endGame(args.length == 1 || args.length == 2 && args[1].equalsIgnoreCase("false"), true);

						return false;
					} else {
						sender.sendMessage(ChatColor.DARK_RED + "You don't have permission!");
						return true;
					}
				}
			}

			if (args[0].equalsIgnoreCase("list")) {
				if (args.length == 1) {
					if (plugin.players.isEmpty()) {
						sender.sendMessage(ChatColor.GOLD + "There are no players in game.");
					} else {
						sender.sendMessage(ChatColor.GOLD + "Players in game:");
						String res = new String();
						for (Map.Entry<String, BingoPlayer> entry : plugin.players.entrySet()) {
							res += ChatColor.AQUA + entry.getKey() + ChatColor.GOLD + ", ";
						}
						res = res.substring(0, (int) res.length() - 2) + ChatColor.GOLD + ".";
						sender.sendMessage(ChatColor.GOLD + res);
					}

					return false;
				}
			}

			if (args[0].equalsIgnoreCase("returncard")) {
				if (args.length == 1) {
					if (sender instanceof Player && plugin.players.containsKey(sender.getName())) {
						BingoPlayer bplayer = plugin.players.get(sender.getName());
						plugin.returnCard(bplayer);

						return false;
					}
				}
			}

			if (args[0].equalsIgnoreCase("reload")) {
				if (args.length == 1) {
					if (player.hasPermission("bingo.operator")) {
						plugin.loadGameRules();

						sender.sendMessage(ChatColor.GOLD + "Successfully reload config.yml file.");

						return false;
					} else {
						sender.sendMessage(ChatColor.DARK_RED + "You don't have permission!");
						return true;
					}
				}
			}

			if (args[0].equalsIgnoreCase("gamerules")) {
				if (args.length == 2 && Arrays.asList("arcade", "realistic").contains(args[1])) {
					if (args[1].equalsIgnoreCase("arcade")) {
						plugin.setArcadeGameRules();
					} else {
						plugin.setRealisticGameRules();
					}

					sender.sendMessage(ChatColor.GOLD + "Successfully changed game rules.");

					return false;
				}
			}

			if (args[0].equalsIgnoreCase("setscore")) {
				if (args.length == 2 || args.length == 3) {
					if (player.hasPermission("bingo.operator")) {
						String target = sender.getName();
						if (args.length == 3)
							target = args[2];

						boolean found = false;
						for (Map.Entry<String, Integer> entry : plugin.globalPoints.entrySet()) {
							if (entry.getKey().equalsIgnoreCase(target) || target.equalsIgnoreCase("*")) {
								plugin.setGlobalScore(entry.getKey(), Integer.valueOf(args[1]));
								found = true;
							}
						}

						if (found) {
							sender.sendMessage(ChatColor.GOLD + "Successfully set the score.");
						} else {
							sender.sendMessage(ChatColor.RED + "The player " + target + " wasn't found!");
						}

						return false;
					} else {
						sender.sendMessage(ChatColor.DARK_RED + "You don't have permission!");
						return true;
					}
				}
			}

			if (args[0].equalsIgnoreCase("toggleglobalscore")) {
				if (player.hasPermission("bingo.operator")) {
					plugin.toggleGlobalScore();

					sender.sendMessage(ChatColor.GOLD + "Successfully toggled the global score.");

					return false;
				} else {
					sender.sendMessage(ChatColor.DARK_RED + "You don't have permission!");
					return true;
				}
			}

			sender.sendMessage(ChatColor.DARK_RED + "Wrong command syntax! Type " + ChatColor.BOLD + "/bingo help"
					+ ChatColor.RESET + ChatColor.DARK_RED + " for help.");

			return true;
		}

		return false;
	}

	int setupGame(String mode, String difficultyString) {
		int gameType = -1;
		if (mode.equalsIgnoreCase("default"))
			gameType = 0;
		if (mode.equalsIgnoreCase("butfast"))
			gameType = 1;
		if (mode.equalsIgnoreCase("collectall"))
			gameType = 2;

		int difficulty = -1;
		if (difficultyString.equalsIgnoreCase("baby"))
			difficulty = 0;
		if (difficultyString.equalsIgnoreCase("easy"))
			difficulty = 1;
		if (difficultyString.equalsIgnoreCase("medium"))
			difficulty = 2;
		if (difficultyString.equalsIgnoreCase("hard"))
			difficulty = 3;
		if (difficultyString.equalsIgnoreCase("insane"))
			difficulty = 4;

		int result = plugin.setupGame(gameType, difficulty);

		return result;
	}
}
