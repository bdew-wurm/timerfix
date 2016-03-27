package net.bdew.wurm.timerfix;


import com.wurmonline.mesh.Tiles;
import com.wurmonline.server.Servers;
import com.wurmonline.server.behaviours.Action;
import com.wurmonline.server.spells.Spell;

public class TimerHooks {
    static public boolean shouldFlattenTick(Action act, boolean insta, float counter, byte type, boolean first) {
        if (insta) return true;

        float tickTimes = 5;
        if (act.getNumber() == 150) {
            tickTimes = 10;
        }
        if (type == Tiles.Tile.TILE_CLAY.id || type == Tiles.Tile.TILE_TAR.id || type == Tiles.Tile.TILE_PEAT.id) {
            tickTimes = 30;
        }
        tickTimes = tickTimes / Servers.localServer.getActionTimer();

        if (counter == 1 && first) {
            act.setNextTick(counter + tickTimes);
            return true;
        } else if (act.getNextTick() < counter) {
            if (!first)
                act.setNextTick(counter + tickTimes);
            return true;
        }

        return false;
    }

    static public int getCastingTime(Spell spell, int base) {
        if (TimerFix.spellBlacklist.contains(TimerFix.sanitizeSpellName(spell.getName())))
            return base;
        else
            return Math.max((int) (base / Servers.localServer.getActionTimer()), TimerFix.minSpellTimer);
    }
}
