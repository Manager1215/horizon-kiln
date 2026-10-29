package io.github.manager1215.kilntimer.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SettingsTest {
    @Test
    void settingsSurviveSavingAndBadLinesKeepDefaults() {
        Settings s = new Settings();
        s.corner = Settings.Corner.BOTTOM_RIGHT;
        s.finish = FinishMode.LEAVE_END;
        s.shown = false;
        s.chatSplits = true;
        Settings again = Settings.load(s.save());
        assertEquals(Settings.Corner.BOTTOM_RIGHT, again.corner);
        assertEquals(FinishMode.LEAVE_END, again.finish);
        assertFalse(again.shown);
        assertTrue(again.chatSplits);

        Settings odd = Settings.load("corner=middle\nfinish=boat\nshown=maybe\nnoise\n");
        assertEquals(Settings.Corner.TOP_LEFT, odd.corner);
        assertEquals(FinishMode.DRAGON, odd.finish);
        assertTrue(odd.shown);
        assertFalse(odd.chatSplits);
        assertEquals(FinishMode.MANUAL, Settings.load("finish=Manual").finish);
        assertEquals(FinishMode.LEAVE_END, FinishMode.parse("leave-end").orElseThrow());
    }
}
