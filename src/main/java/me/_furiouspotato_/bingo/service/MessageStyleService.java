package me._furiouspotato_.bingo.service;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import me._furiouspotato_.bingo.model.GameDifficulty;
import me._furiouspotato_.bingo.model.GameMode;
import me._furiouspotato_.bingo.model.TeamColor;

public final class MessageStyleService {
    public Component teamLabel(String nickname, TeamColor color, int memberCount) {
        if (nickname != null && !nickname.isBlank()) {
            return Component.text(nickname, color.textColor());
        }
        return color.displayNameComponent();
    }

    public Component joinedTeam(String playerName, TeamColor color) {
        return Component.text(playerName, NamedTextColor.AQUA)
                .append(Component.text(" joined team ", NamedTextColor.GOLD))
                .append(color.displayNameComponent())
                .append(Component.text(".", NamedTextColor.GOLD));
    }

    public Component switchedTeam(String playerName, TeamColor color) {
        return Component.text(playerName, NamedTextColor.AQUA)
                .append(Component.text(" switched to team ", NamedTextColor.GOLD))
                .append(color.displayNameComponent())
                .append(Component.text(".", NamedTextColor.GOLD));
    }

    public Component gameStartingIn(int seconds) {
        return Component.text("Game starts in " + seconds + "s", NamedTextColor.YELLOW);
    }

    public Component gameStartedNow() {
        return Component.text("GO!", NamedTextColor.GREEN);
    }

    public Component gameStarted(GameMode mode, GameDifficulty difficulty) {
        return Component.text("Game started: ", NamedTextColor.GREEN)
                .append(Component.text("mode ", NamedTextColor.GREEN))
                .append(Component.text(mode.key(), NamedTextColor.GOLD))
                .append(Component.text(", difficulty ", NamedTextColor.GREEN))
                .append(Component.text(difficulty.key(), NamedTextColor.GOLD));
    }

    public Component teamCollected(String nickname, TeamColor teamColor, int memberCount, String itemName) {
        return teamLabel(nickname, teamColor, memberCount)
                .append(Component.text(" collected ", NamedTextColor.GOLD))
                .append(Component.text(itemName, NamedTextColor.AQUA))
                .append(Component.text(".", NamedTextColor.GOLD));
    }

    public Component claimFailed() {
        return Component.text("Missing required item.", NamedTextColor.RED);
    }

    public Component timeWarning(String message) {
        return Component.text(message, NamedTextColor.RED);
    }

    public Component gameEndingIn(int seconds) {
        return Component.text("Game ending in " + seconds + "s", NamedTextColor.YELLOW);
    }

    public Component gameEndedNow() {
        return Component.text("Game over!", NamedTextColor.GOLD);
    }
}
