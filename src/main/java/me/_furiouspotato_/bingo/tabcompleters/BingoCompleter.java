package me._furiouspotato_.bingo.tabcompleters;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import me._furiouspotato_.bingo.BingoPlayer;
import me._furiouspotato_.bingo.Main;

public class BingoCompleter implements TabCompleter {
	private Main plugin;

	public BingoCompleter(Main plugin) {
		this.plugin = plugin;
	}

	public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
		List<String> res = new ArrayList<>();

		List<String> all = new ArrayList<>();
		List<String> operator = new ArrayList<>();

		if (args.length == 1) {
			all = Arrays.asList("help", "list", "returncard");
			operator = Arrays.asList("setup", "join", "leave", "start", "quickstart", "end", "reload", "gamerules",
					"setscore", "toggleglobalscore");
		}

		if (args[0].equalsIgnoreCase("join")) {
			if (args.length == 2) {
				Player[] players = new Player[Bukkit.getServer().getOnlinePlayers().size()];
				Bukkit.getServer().getOnlinePlayers().toArray((Object[]) players);
				for (Player player : players) {
					if (!plugin.players.containsKey(player.getName())) {
						operator.add(player.getName());
					}
				}
				if (!operator.isEmpty()) {
					operator.add("*");
				}
			}
		}

		if (args[0].equalsIgnoreCase("leave")) {
			if (args.length == 2) {
				for (Map.Entry<String, BingoPlayer> entry : plugin.players.entrySet()) {
					BingoPlayer bplayer = entry.getValue();
					operator.add(bplayer.player.getName());
				}
				operator.add("*");
			}
		}

		if (args[0].equalsIgnoreCase("end")) {
			operator = Arrays.asList("false", "true");
		}

		if (args[0].equalsIgnoreCase("setup") || args[0].equalsIgnoreCase("quickstart")) {
			if (args.length == 2)
				operator = Arrays.asList("default", "butfast", "collectall");
			if (args.length >= 2 && Arrays.asList("default", "butfast", "collectall").contains(args[1])) {
				if (args.length == 3)
					operator = Arrays.asList("baby", "easy", "medium", "hard", "insane");
			}
		}

		if (args.length == 2 && args[0].equalsIgnoreCase("gamerules")) {
			operator = Arrays.asList("arcade", "realistic");
		}

		if (args.length == 3 && args[0].equalsIgnoreCase("setscore")) {
			Player[] players = new Player[Bukkit.getServer().getOnlinePlayers().size()];
			Bukkit.getServer().getOnlinePlayers().toArray((Object[]) players);
			for (Player player : players) {
				operator.add(player.getDisplayName());
			}
			operator.add("*");
		}

		List<String> arguments = new ArrayList<>();
		for (String argument : all) {
			arguments.add(argument);
		}
		if (((Player) sender).hasPermission("manhunt.operator")) {
			for (String argument : operator) {
				arguments.add(argument);
			}
		}

		for (String argument : arguments) {
			if (argument.toLowerCase().indexOf(args[args.length - 1].toLowerCase()) == 0) {
				res.add(argument);
			}
		}
		Collections.sort(res);
		return res;
	}
}
