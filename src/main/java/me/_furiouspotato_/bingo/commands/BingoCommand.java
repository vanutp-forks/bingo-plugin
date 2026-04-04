package me._furiouspotato_.bingo.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import me._furiouspotato_.bingo.model.GameDifficulty;
import me._furiouspotato_.bingo.model.GameMode;
import me._furiouspotato_.bingo.model.TeamColor;
import me._furiouspotato_.bingo.service.GameSessionManager;
import me._furiouspotato_.bingo.service.ItemPoolService;
import me._furiouspotato_.bingo.service.RulesConfigService;
import me._furiouspotato_.bingo.service.TeamManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public final class BingoCommand {
    private static final String ADMIN_PERMISSION = "bingo.operator";

    private final GameSessionManager session;
    private final RulesConfigService rules;
    private final ItemPoolService itemPool;
    private final TeamManager teamManager;

    public BingoCommand(GameSessionManager session, RulesConfigService rules, ItemPoolService itemPool,
            TeamManager teamManager) {
        this.session = session;
        this.rules = rules;
        this.itemPool = itemPool;
        this.teamManager = teamManager;
    }

    public void register(Commands registrar) {
        registrar.register(
                Commands.literal("bingo")
                        .executes(this::runHelp)
                        .then(Commands.literal("help").executes(this::runHelp))
                        .then(Commands.literal("join")
                                .executes(this::runJoinSelfAuto)
                                .then(Commands.argument("arg", StringArgumentType.word())
                                        .suggests(this::suggestJoinFirstArg)
                                        .executes(this::runJoinWithOneArg)
                                        .then(Commands.argument("player", StringArgumentType.word())
                                                .suggests(this::suggestPlayers)
                                                .executes(this::runJoinWithTeamAndPlayer))))
                        .then(Commands.literal("leave")
                                .executes(this::runLeaveSelf)
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .suggests(this::suggestPlayers)
                                        .executes(this::runLeavePlayer)))
                        .then(Commands.literal("list").executes(this::runList))
                        .then(Commands.literal("start")
                                .executes(this::runStartDefault)
                                .then(Commands.argument("targets", ArgumentTypes.players())
                                        .executes(this::runStartTargets)))
                        .then(Commands.literal("end").executes(this::runEnd))
                        .then(Commands.literal("mode")
                                .then(Commands.argument("mode", StringArgumentType.word())
                                        .suggests((ctx, builder) -> suggestWords(builder, GameMode.keys()))
                                        .executes(this::runMode)))
                        .then(Commands.literal("difficulty")
                                .then(Commands.argument("difficulty", StringArgumentType.word())
                                        .suggests((ctx, builder) -> suggestWords(builder, GameDifficulty.keys()))
                                        .executes(this::runDifficulty)))
                        .then(Commands.literal("reload").executes(this::runReload))
                        .build(),
                "Team Bingo game command.");
    }

    private int runHelp(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        printHelp(sender(ctx));
        return Command.SINGLE_SUCCESS;
    }

    private int runJoinSelfAuto(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSender sender = sender(ctx);
        boolean admin = isAdmin(sender);
        Player self = requirePlayer(sender);
        ensureJoinAllowed(self, admin);
        session.joinPlayer(self, null);
        return Command.SINGLE_SUCCESS;
    }

    private int runJoinWithOneArg(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSender sender = sender(ctx);
        String arg = StringArgumentType.getString(ctx, "arg");
        boolean admin = isAdmin(sender);

        TeamColor team = parseTeamOrNull(arg);
        if (team != null) {
            Player self = requirePlayer(sender);
            ensureJoinAllowed(self, admin);
            session.joinPlayer(self, team);
            return Command.SINGLE_SUCCESS;
        }

        requireAdmin(sender);
        Player target = requireOnlinePlayer(arg);
        session.joinPlayer(target, null);
        target.sendMessage("An admin assigned you to a team.");
        return Command.SINGLE_SUCCESS;
    }

    private int runJoinWithTeamAndPlayer(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSender sender = sender(ctx);
        requireAdmin(sender);
        TeamColor team = TeamColor.parse(StringArgumentType.getString(ctx, "arg"));
        Player target = requireOnlinePlayer(StringArgumentType.getString(ctx, "player"));
        session.joinPlayer(target, team);
        target.sendMessage(Component.text("An admin assigned you to team ").append(team.displayNameComponent())
                .append(Component.text(".")));
        return Command.SINGLE_SUCCESS;
    }

    private int runLeaveSelf(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSender sender = sender(ctx);
        boolean admin = isAdmin(sender);
        Player self = requirePlayer(sender);
        if (session.isRunning() && !admin) {
            throw new IllegalStateException("Only admins can modify teams while a game is running.");
        }
        session.removePlayer(self);
        self.sendMessage("You left your team.");
        return Command.SINGLE_SUCCESS;
    }

    private int runLeavePlayer(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSender sender = sender(ctx);
        requireAdmin(sender);
        Player target = requireOnlinePlayer(StringArgumentType.getString(ctx, "player"));
        session.removePlayer(target);
        target.sendMessage("An admin removed you from the game teams.");
        return Command.SINGLE_SUCCESS;
    }

    private int runStartDefault(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSender sender = sender(ctx);
        requireAdmin(sender);
        session.startGame(new ArrayList<>(Bukkit.getOnlinePlayers()));
        return Command.SINGLE_SUCCESS;
    }

    private int runStartTargets(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSender sender = sender(ctx);
        requireAdmin(sender);
        List<Player> targets = ctx.getArgument("targets", PlayerSelectorArgumentResolver.class)
                .resolve(ctx.getSource());
        try {
            session.startGame(targets);
        } catch (IllegalStateException | IllegalArgumentException ex) {
            sender.sendMessage(Component.text(ex.getMessage(), NamedTextColor.RED));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int runEnd(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        requireAdmin(sender(ctx));
        session.endGame(true);
        return Command.SINGLE_SUCCESS;
    }

    private int runList(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSender sender = sender(ctx);
        String summary = session.participantsSummary();
        if (summary.isBlank()) {
            sender.sendMessage("No active team members.");
            return Command.SINGLE_SUCCESS;
        }
        sender.sendMessage("Teams:");
        sender.sendMessage(summary);
        return Command.SINGLE_SUCCESS;
    }

    private int runMode(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSender sender = sender(ctx);
        requireAdmin(sender);
        GameMode mode = GameMode.fromKey(StringArgumentType.getString(ctx, "mode"));
        session.setMode(mode);
        sender.sendMessage("Default mode set to " + mode.key() + ".");
        return Command.SINGLE_SUCCESS;
    }

    private int runDifficulty(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSender sender = sender(ctx);
        requireAdmin(sender);
        GameDifficulty difficulty = GameDifficulty.fromKey(StringArgumentType.getString(ctx, "difficulty"));
        session.setDifficulty(difficulty);
        sender.sendMessage("Default difficulty set to " + difficulty.key() + ".");
        return Command.SINGLE_SUCCESS;
    }

    private int runReload(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSender sender = sender(ctx);
        requireAdmin(sender);
        rules.load();
        itemPool.load();
        sender.sendMessage("Reloaded config and item database.");
        return Command.SINGLE_SUCCESS;
    }

    private void ensureJoinAllowed(Player player, boolean admin) {
        if (session.isRunning() && !admin) {
            throw new IllegalStateException("Only admins can join/switch teams while the game is running.");
        }
    }

    private TeamColor parseTeamOrNull(String value) {
        try {
            return TeamColor.parse(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private Player requirePlayer(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            throw new IllegalArgumentException("This command requires an in-game player.");
        }
        return player;
    }

    private static Player requireOnlinePlayer(String nickname) {
        Player player = Bukkit.getPlayerExact(nickname);
        if (player == null) {
            throw new IllegalArgumentException("Player not found: " + nickname);
        }
        return player;
    }

    private void printHelp(CommandSender sender) {
        sender.sendMessage("Bingo commands:");
        sender.sendMessage("/bingo join - auto-join lowest population team.");
        sender.sendMessage("/bingo join <team> - join a specific wool-color team.");
        sender.sendMessage("/bingo list - list non-empty teams.");
        sender.sendMessage("/bingo start [selector] - start game. Default: @a.");
        sender.sendMessage("/bingo end - end current game.");
        sender.sendMessage("/bingo mode <mode> - set game mode.");
        sender.sendMessage("/bingo difficulty <difficulty> - set game difficulty.");
        sender.sendMessage(
                "Admin-only: /bingo join <player>, /bingo join <team> <player>, /bingo leave <player>, /bingo reload");
    }

    private static boolean isAdmin(CommandSender sender) {
        return sender.hasPermission(ADMIN_PERMISSION);
    }

    private static void requireAdmin(CommandSender sender) {
        if (!isAdmin(sender)) {
            throw new IllegalArgumentException("You do not have permission.");
        }
    }

    private CompletableFuture<Suggestions> suggestJoinFirstArg(CommandContext<CommandSourceStack> ctx,
            SuggestionsBuilder builder) throws CommandSyntaxException {
        List<String> options = new ArrayList<>(teamManager.teamNames());
        CommandSender sender = sender(ctx);
        if (isAdmin(sender)) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                options.add(player.getName());
            }
        }
        return suggestWords(builder, options);
    }

    private CompletableFuture<Suggestions> suggestPlayers(CommandContext<CommandSourceStack> ctx,
            SuggestionsBuilder builder)
            throws CommandSyntaxException {
        List<String> options = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            options.add(player.getName());
        }
        return suggestWords(builder, options);
    }

    private static CompletableFuture<Suggestions> suggestWords(SuggestionsBuilder builder, List<String> words) {
        String remaining = builder.getRemainingLowerCase();
        for (String word : words) {
            if (word.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                builder.suggest(word);
            }
        }
        return builder.buildFuture();
    }

    private static CommandSender sender(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return ctx.getSource().getSender();
    }
}
