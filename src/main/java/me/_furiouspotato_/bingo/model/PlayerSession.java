package me._furiouspotato_.bingo.model;

public final class PlayerSession {
    private final String nickname;
    private TeamColor teamColor;

    public PlayerSession(String nickname, TeamColor teamColor) {
        this.nickname = nickname;
        this.teamColor = teamColor;
    }

    public String nickname() {
        return nickname;
    }

    public TeamColor teamColor() {
        return teamColor;
    }

    public void setTeamColor(TeamColor teamColor) {
        this.teamColor = teamColor;
    }
}
