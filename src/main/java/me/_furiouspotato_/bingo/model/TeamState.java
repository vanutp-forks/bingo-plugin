package me._furiouspotato_.bingo.model;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class TeamState {
    private final TeamColor color;
    private final Set<String> members = new LinkedHashSet<>();
    private final boolean[] collected = new boolean[25];
    private int score = 0;
    private boolean finished = false;

    public TeamState(TeamColor color) {
        this.color = color;
    }

    public TeamColor color() {
        return color;
    }

    public Set<String> members() {
        return Collections.unmodifiableSet(members);
    }

    public void addMember(String nickname) {
        members.add(nickname);
    }

    public void removeMember(String nickname) {
        members.remove(nickname);
    }

    public int memberCount() {
        return members.size();
    }

    public boolean[] collected() {
        return collected;
    }

    public int score() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public void addScore(int delta) {
        this.score += delta;
    }

    public boolean finished() {
        return finished;
    }

    public void setFinished(boolean finished) {
        this.finished = finished;
    }

    public void resetRound() {
        for (int i = 0; i < collected.length; i++) {
            collected[i] = false;
        }
        score = 0;
        finished = false;
    }
}
