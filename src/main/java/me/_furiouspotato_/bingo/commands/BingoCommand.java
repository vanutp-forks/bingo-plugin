package me._furiouspotato_.bingo.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

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
import me._furiouspotato_.bingo.model.BoardClaimMode;
import me._furiouspotato_.bingo.model.GameDifficulty;
import me._furiouspotato_.bingo.model.GameMode;
import me._furiouspotato_.bingo.model.TeamColor;
import me._furiouspotato_.bingo.service.DependencyCostService;
import me._furiouspotato_.bingo.service.GameSessionManager;
import me._furiouspotato_.bingo.service.ItemPoolService;
import me._furiouspotato_.bingo.service.NodeDebugPrinter;
import me._furiouspotato_.bingo.service.RulesConfigService;
import me._furiouspotato_.bingo.service.TeamManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public final class BingoCommand {
    private static final String ADMIN_PERMISSION = "bingo.operator";

    private final JavaPlugin plugin;
    private final GameSessionManager session;
    private final RulesConfigService rules;
    private final ItemPoolService itemPool;
    private final DependencyCostService dependencyCostService;
    private final TeamManager teamManager;

    public BingoCommand(JavaPlugin plugin, GameSessionManager session, RulesConfigService rules,
            ItemPoolService itemPool,
            DependencyCostService dependencyCostService,
            TeamManager teamManager) {
        this.plugin = plugin;
        this.session = session;
        this.rules = rules;
        this.itemPool = itemPool;
        this.dependencyCostService = dependencyCostService;
        this.teamManager = teamManager;
    }

    public void register(Commands registrar) {
        registrar.register(
                Commands.literal("bingo")
                        .executes(this::runHelp)
                        .then(Commands.literal("help").executes(this::runHelp))
                        .then(Commands.literal("join")
                                .executes(this::runJoinSelfAuto)
                                .then(Commands.argument("team", StringArgumentType.word())
                                        .suggests((ctx, builder) -> suggestWords(builder, teamManager.teamNames()))
                                        .executes(this::runJoinWithTeamSelf)
                                        .then(Commands.argument("targets", ArgumentTypes.players())
                                                .executes(this::runJoinWithTeamTargets))))
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
                        .then(Commands.literal("stop").executes(this::runStop))
                        .then(Commands.literal("restart")
                                .executes(this::runRestartDefault)
                                .then(Commands.argument("targets", ArgumentTypes.players())
                                        .executes(this::runRestartTargets)))
                        .then(Commands.literal("mode")
                                .then(Commands.argument("mode", StringArgumentType.word())
                                        .suggests((ctx, builder) -> suggestWords(builder, GameMode.keys()))
                                        .executes(this::runMode)))
                        .then(Commands.literal("difficulty")
                                .then(Commands.argument("difficulty", StringArgumentType.word())
                                        .suggests((ctx, builder) -> suggestWords(builder, rules.difficultyKeys()))
                                        .executes(this::runDifficulty)))
                        .then(Commands.literal("cardmode")
                                .then(Commands.argument("mode", StringArgumentType.word())
                                        .suggests((ctx, builder) -> suggestWords(builder, List.of("auto", "manual")))
                                        .executes(this::runCardMode)))
                        .then(Commands.literal("consumeonclaim")
                                .then(Commands.argument("enabled", StringArgumentType.word())
                                        .suggests((ctx, builder) -> suggestWords(builder, List.of("true", "false")))
                                        .executes(this::runConsumeOnClaim)))
                        .then(Commands.literal("returncard").executes(this::runReturnCard))
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

    private int runJoinWithTeamSelf(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSender sender = sender(ctx);
        boolean admin = isAdmin(sender);

        try {
            TeamColor team = TeamColor.parse(StringArgumentType.getString(ctx, "team"));
            Player self = requirePlayer(sender);
            ensureJoinAllowed(self, admin);
            session.joinPlayer(self, team);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return fail(sender, ex.getMessage());
        }
        return Command.SINGLE_SUCCESS;
    }

    private int runJoinWithTeamTargets(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSender sender = sender(ctx);
        try {
            requireAdmin(sender);
            TeamColor team = TeamColor.parse(StringArgumentType.getString(ctx, "team"));
            List<Player> targets = ctx.getArgument("targets", PlayerSelectorArgumentResolver.class)
                    .resolve(ctx.getSource());
            if (targets.isEmpty()) {
                throw new IllegalArgumentException("No players were selected.");
            }
            for (Player target : targets) {
                session.joinPlayer(target, team);
                target.sendMessage(Component.text("An admin assigned you to team ").append(team.displayNameComponent())
                        .append(Component.text(".")));
            }
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return fail(sender, ex.getMessage());
        }
        return Command.SINGLE_SUCCESS;
    }

    private int runLeaveSelf(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSender sender = sender(ctx);
        try {
            Player self = requirePlayer(sender);
            boolean leavingRound = session.isRoundParticipant(self.getName());
            session.removePlayer(self);
            info(self, leavingRound ? "You left the current game." : "You left your team.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return fail(sender, ex.getMessage());
        }
        return Command.SINGLE_SUCCESS;
    }

    private int runLeavePlayer(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSender sender = sender(ctx);
        try {
            requireAdmin(sender);
            Player target = requireOnlinePlayer(StringArgumentType.getString(ctx, "player"));
            boolean leavingRound = session.isRoundParticipant(target.getName());
            session.removePlayer(target);
            info(target, leavingRound
                    ? "An admin removed you from the current game."
                    : "An admin removed you from the game teams.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return fail(sender, ex.getMessage());
        }
        return Command.SINGLE_SUCCESS;
    }

    private int runStartDefault(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSender sender = sender(ctx);
        requireAdmin(sender);
        try {
            session.startGame(new ArrayList<>(Bukkit.getOnlinePlayers()));
        } catch (IllegalStateException | IllegalArgumentException ex) {
            sender.sendMessage(Component.text(ex.getMessage(), NamedTextColor.RED));
        }
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

    private int runStop(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        requireAdmin(sender(ctx));
        session.endGame(true);
        return Command.SINGLE_SUCCESS;
    }

    private int runRestartDefault(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSender sender = sender(ctx);
        requireAdmin(sender);
        List<Player> targets = new ArrayList<>(Bukkit.getOnlinePlayers());
        try {
            if (targets.isEmpty()) {
                throw new IllegalArgumentException("No players were selected.");
            }
            session.endGame(false);
            session.startGame(targets);
        } catch (IllegalStateException | IllegalArgumentException ex) {
            sender.sendMessage(Component.text(ex.getMessage(), NamedTextColor.RED));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int runRestartTargets(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSender sender = sender(ctx);
        requireAdmin(sender);
        List<Player> targets = ctx.getArgument("targets", PlayerSelectorArgumentResolver.class)
                .resolve(ctx.getSource());
        try {
            if (targets.isEmpty()) {
                throw new IllegalArgumentException("No players were selected.");
            }
            session.endGame(false);
            session.startGame(targets);
        } catch (IllegalStateException | IllegalArgumentException ex) {
            sender.sendMessage(Component.text(ex.getMessage(), NamedTextColor.RED));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int runList(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSender sender = sender(ctx);
        String summary = session.participantsSummary();
        if (summary.isBlank()) {
            info(sender, "No active team members.");
            return Command.SINGLE_SUCCESS;
        }
        info(sender, "Teams:");
        sender.sendMessage(Component.text(summary, NamedTextColor.YELLOW));
        return Command.SINGLE_SUCCESS;
    }

    private int runMode(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSender sender = sender(ctx);
        try {
            requireAdmin(sender);
            GameMode mode = GameMode.fromKey(StringArgumentType.getString(ctx, "mode"));
            session.setMode(mode);
            rules.saveRuntimeSettings();
            info(sender, "Default mode set to " + mode.key() + ".");
        } catch (IllegalArgumentException ex) {
            return fail(sender, ex.getMessage());
        }
        return Command.SINGLE_SUCCESS;
    }

    private int runDifficulty(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSender sender = sender(ctx);
        try {
            requireAdmin(sender);
            GameDifficulty difficulty = rules.requireDifficulty(StringArgumentType.getString(ctx, "difficulty"));
            session.setDifficulty(difficulty);
            rules.saveRuntimeSettings();
            info(sender, "Default difficulty set to " + difficulty.key() + ".");
        } catch (IllegalArgumentException ex) {
            return fail(sender, ex.getMessage());
        }
        return Command.SINGLE_SUCCESS;
    }

    private int runReload(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSender sender = sender(ctx);
        requireAdmin(sender);
        rules.load();
        itemPool.load();
        dependencyCostService.setNodes(itemPool.nodes());
        dependencyCostService.refresh();
        plugin.getLogger().info("Debug information enabled: " + rules.printDebugInformation());
        if (rules.printDebugInformation()) {
            NodeDebugPrinter.print(plugin.getLogger(), itemPool, dependencyCostService);
        }
        info(sender, "Reloaded config and node graph.");
        return Command.SINGLE_SUCCESS;
    }

    private int runCardMode(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSender sender = sender(ctx);
        requireAdmin(sender);
        BoardClaimMode mode;
        try {
            mode = BoardClaimMode.fromKey(StringArgumentType.getString(ctx, "mode"));
        } catch (IllegalArgumentException ex) {
            sender.sendMessage(Component.text(ex.getMessage(), NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }
        rules.setBoardClaimMode(mode);
        rules.saveRuntimeSettings();
        info(sender, "Board claim mode set to " + mode.key() + ".");
        return Command.SINGLE_SUCCESS;
    }

    private int runConsumeOnClaim(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSender sender = sender(ctx);
        requireAdmin(sender);
        String raw = StringArgumentType.getString(ctx, "enabled").toLowerCase(Locale.ROOT);
        if (!raw.equals("true") && !raw.equals("false")) {
            sender.sendMessage(Component.text("Value must be true or false.", NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }
        boolean enabled = Boolean.parseBoolean(raw);
        rules.setConsumeOnClaim(enabled);
        rules.saveRuntimeSettings();
        info(sender, "Consume on claim set to " + enabled + ".");
        return Command.SINGLE_SUCCESS;
    }

    private int runReturnCard(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Player player = requirePlayer(sender(ctx));
        session.returnCard(player);
        player.sendMessage(Component.text("Returned your bingo card to slot 9.", NamedTextColor.GOLD));
        return Command.SINGLE_SUCCESS;
    }

    private void ensureJoinAllowed(Player player, boolean admin) {
        if (session.isRunning() && !admin) {
            throw new IllegalStateException("Only admins can join/switch teams while the game is running.");
        }
    }

    private static int fail(CommandSender sender, String message) {
        sender.sendMessage(Component.text(message, NamedTextColor.RED));
        return Command.SINGLE_SUCCESS;
    }

    private static void info(CommandSender sender, String message) {
        sender.sendMessage(Component.text(message, NamedTextColor.GOLD));
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
        info(sender, "Bingo commands:");
        sender.sendMessage(Component.text("/bingo join - auto-join lowest population team.", NamedTextColor.YELLOW));
        sender.sendMessage(
                Component.text("/bingo join <team> - join a specific wool-color team.", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/bingo join <team> <players> - admin: assign selected players to a team.",
                NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/bingo list - list non-empty teams.", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/bingo start [selector] - start game. Default: @a.", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/bingo stop - end current game.", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/bingo restart [selector] - restart with current settings.",
                NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/bingo mode <mode> - set game mode.", NamedTextColor.YELLOW));
        sender.sendMessage(
                Component.text("/bingo difficulty <difficulty> - set game difficulty.", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/bingo cardmode <auto|manual> - set claim mode.", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/bingo returncard - return your bingo card.", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text(
                "Admin-only: /bingo join <team> <players>, /bingo leave <player>, /bingo reload, /bingo consumeonclaim",
                NamedTextColor.YELLOW));
    }

    private static boolean isAdmin(CommandSender sender) {
        return sender.hasPermission(ADMIN_PERMISSION);
    }

    private static void requireAdmin(CommandSender sender) {
        if (!isAdmin(sender)) {
            throw new IllegalArgumentException("You do not have permission.");
        }
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
