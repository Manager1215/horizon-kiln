package net.minecraft.world.scores;

import net.minecraft.network.chat.Component;

/** Kiln's stand-in for {@code ScoreHolder}: whom a score is for, by the name the scoreboard keeps it under. */
public interface ScoreHolder {
    String WILDCARD_NAME = "*";
    ScoreHolder WILDCARD = () -> WILDCARD_NAME;

    String getScoreboardName();

    default Component getDisplayName() {
        return Component.literal(getScoreboardName());
    }

    default Component getFeedbackDisplayName() {
        return getDisplayName();
    }

    static ScoreHolder forNameOnly(String name) {
        if (name.equals(WILDCARD_NAME)) {
            return WILDCARD;
        }
        return () -> name;
    }
}
