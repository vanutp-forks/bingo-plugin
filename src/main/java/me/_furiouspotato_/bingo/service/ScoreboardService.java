package me._furiouspotato_.bingo.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import me._furiouspotato_.bingo.model.GameMode;
import me._furiouspotato_.bingo.model.TeamState;

public final class ScoreboardService {
    public void update(GameSessionManager session) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
            applyTeams(scoreboard, session);

            if (session.isRunning()) {
                Objective objective = scoreboard.registerNewObjective(
                        "bingo",
                        Criteria.DUMMY,
                        Component.text("Bingo", NamedTextColor.GOLD));
                objective.setDisplaySlot(DisplaySlot.SIDEBAR);

                int line = 15;
                String modeLine = "\u00A76Mode: \u00A7e" + session.mode().key() + "\u00A76 / Diff: \u00A7e"
                        + session.difficulty().key();
                objective.getScore(trim(modeLine)).setScore(line--);

                String timeLine = "\u00A76Time: \u00A7a" + formatTime(session.elapsedSeconds()) + "\u00A76/\u00A7a"
                        + formatTime(session.finishSeconds());
                objective.getScore(trim(timeLine)).setScore(line--);
                objective.getScore("\u00A77 ").setScore(line--);

                List<TeamState> teams = new ArrayList<>(session.visibleTeams());
                teams.sort(teamComparator(session.mode()));
                for (TeamState state : teams) {
                    String row = formatTeamRow(session, state);
                    objective.getScore(trim(row)).setScore(line--);
                    if (line <= 0) {
                        break;
                    }
                }
            }

            if (session.rules().showGlobalScore()) {
                Objective objective = scoreboard.registerNewObjective("bingo_global", Criteria.DUMMY,
                        Component.text("Global Points", NamedTextColor.GOLD));
                objective.setDisplaySlot(DisplaySlot.PLAYER_LIST);
                for (Map.Entry<String, Integer> entry : session.globalPoints().entrySet()) {
                    objective.getScore(entry.getKey()).setScore(entry.getValue());
                }
            }

            player.setScoreboard(scoreboard);
        }
    }

    public void clearAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    private static void applyTeams(Scoreboard scoreboard, GameSessionManager session) {
        for (TeamState state : session.teamManager().activeTeams()) {
            Team team = scoreboard.registerNewTeam("bingo_" + state.color().name().toLowerCase());
            team.color(state.color().textColor());
            for (String member : state.members()) {
                team.addEntry(member);
            }
        }
    }

    private static String trim(String value) {
        return value.length() <= 40 ? value : value.substring(0, 40);
    }

    private static String formatTime(int seconds) {
        int safe = Math.max(0, seconds);
        int hours = safe / 3600;
        int minutes = (safe % 3600) / 60;
        int secs = safe % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, secs);
    }

    private static String teamLabel(TeamState state) {
        String colorCode = legacyColor(state);
        if (state.memberCount() == 1) {
            String member = state.members().iterator().next();
            return colorCode + member + "\u00A7r";
        }
        return colorCode + state.color().displayName() + "\u00A7r";
    }

    private static String formatTeamRow(GameSessionManager session, TeamState state) {
        String label = teamLabel(state);
        if (session.mode() != GameMode.DEFAULT) {
            return "\u00A76" + label + "\u00A76: \u00A7f" + state.score();
        }
        if (state.finished() && state.finishedAtSeconds() >= 0) {
            return "\u00A76" + label + "\u00A76: \u00A7a" + formatTime(state.finishedAtSeconds());
        }
        return "\u00A76" + label + "\u00A76: \u00A7c" + state.score() + "/5";
    }

    private static Comparator<TeamState> teamComparator(GameMode mode) {
        if (mode != GameMode.DEFAULT) {
            return Comparator.comparingInt(TeamState::score).reversed();
        }
        return Comparator
                .comparing(TeamState::finished).reversed()
                .thenComparingInt(state -> state.finished() ? state.finishedAtSeconds() : Integer.MAX_VALUE)
                .thenComparing(Comparator.comparingInt(TeamState::score).reversed());
    }

    private static String legacyColor(TeamState state) {
        return switch (state.color()) {
            case BLACK -> "\u00A70";
            case BLUE -> "\u00A71";
            case GREEN -> "\u00A72";
            case CYAN -> "\u00A73";
            case RED -> "\u00A7c";
            case PURPLE -> "\u00A75";
            case ORANGE, BROWN -> "\u00A76";
            case LIGHT_GRAY -> "\u00A77";
            case GRAY -> "\u00A78";
            case LIGHT_BLUE -> "\u00A79";
            case LIME -> "\u00A7a";
            case PINK, MAGENTA -> "\u00A7d";
            case YELLOW -> "\u00A7e";
            case WHITE -> "\u00A7f";
        };
    }
}
