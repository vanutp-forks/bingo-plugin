package me._furiouspotato_.bingo.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import me._furiouspotato_.bingo.model.TeamColor;
import me._furiouspotato_.bingo.model.TeamState;

public final class TeamManager {
    private final TeamPreferenceStore preferences;
    private final Map<TeamColor, TeamState> teams = new EnumMap<>(TeamColor.class);
    private final Map<String, TeamColor> playerTeams = new java.util.HashMap<>();

    public TeamManager(TeamPreferenceStore preferences) {
        this.preferences = preferences;
        for (TeamColor color : TeamColor.values()) {
            teams.put(color, new TeamState(color));
        }
    }

    public void load() {
        preferences.load();
    }

    public Optional<TeamColor> teamOf(String nickname) {
        return Optional.ofNullable(playerTeams.get(normalize(nickname)));
    }

    public TeamColor assignAutoBalanced(String nickname) {
        TeamColor preferred = preferences.getPreference(nickname);
        if (preferred != null) {
            return assignToTeam(nickname, preferred, false);
        }

        TeamColor selected = teams.values().stream()
                .min(Comparator.comparingInt(TeamState::memberCount).thenComparing(t -> t.color().ordinal()))
                .map(TeamState::color)
                .orElse(TeamColor.WHITE);
        return assignToTeam(nickname, selected, true);
    }

    public TeamColor assignToTeam(String nickname, TeamColor team, boolean storePreference) {
        String key = normalize(nickname);
        TeamColor old = playerTeams.get(key);
        if (old != null && old != team) {
            teams.get(old).removeMember(nickname);
        }

        teams.get(team).addMember(nickname);
        playerTeams.put(key, team);
        if (storePreference) {
            preferences.setPreference(nickname, team);
        }
        return team;
    }

    public void removePlayer(String nickname) {
        String key = normalize(nickname);
        TeamColor current = playerTeams.remove(key);
        if (current != null) {
            teams.get(current).removeMember(nickname);
        }
    }

    public void resetRoundState() {
        for (TeamState teamState : teams.values()) {
            teamState.resetRound();
        }
    }

    public Collection<TeamState> allTeams() {
        return teams.values();
    }

    public List<TeamState> activeTeams() {
        List<TeamState> result = new ArrayList<>();
        for (TeamState state : teams.values()) {
            if (state.memberCount() > 0) {
                result.add(state);
            }
        }
        return result;
    }

    public TeamState stateOf(TeamColor color) {
        return teams.get(color);
    }

    public List<String> teamNames() {
        List<String> names = new ArrayList<>();
        for (TeamColor color : TeamColor.values()) {
            names.add(color.name().toLowerCase(Locale.ROOT));
        }
        return names;
    }

    private static String normalize(String nickname) {
        return nickname.toLowerCase(Locale.ROOT);
    }
}
