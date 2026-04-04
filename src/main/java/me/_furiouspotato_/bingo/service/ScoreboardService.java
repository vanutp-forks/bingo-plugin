package me._furiouspotato_.bingo.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import me._furiouspotato_.bingo.model.TeamState;

public final class ScoreboardService {
    public void update(GameSessionManager session) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
            Objective objective = scoreboard.registerNewObjective(
                    "bingo",
                    Criteria.DUMMY,
                    Component.text("Team Bingo", NamedTextColor.GOLD));
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);

            int line = 15;
            String modeLine = "Mode: " + session.mode().key();
            objective.getScore(trim(modeLine)).setScore(line--);

            String timeLine = "Time: " + session.elapsedSeconds() + " / "
                    + session.finishSeconds();
            objective.getScore(trim(timeLine)).setScore(line--);
            objective.getScore(" ").setScore(line--);

            List<TeamState> teams = new ArrayList<>(session.teamManager().activeTeams());
            teams.sort(Comparator.comparingInt(TeamState::score).reversed());
            for (TeamState state : teams) {
                String row = state.color().displayName() + " [" + state.memberCount() + "] " + state.score();
                objective.getScore(trim(row)).setScore(line--);
                if (line <= 0) {
                    break;
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

    private static String trim(String value) {
        return value.length() <= 40 ? value : value.substring(0, 40);
    }
}
