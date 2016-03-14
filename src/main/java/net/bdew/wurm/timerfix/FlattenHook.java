package net.bdew.wurm.timerfix;


import com.wurmonline.mesh.Tiles;
import com.wurmonline.server.Servers;
import com.wurmonline.server.behaviours.Action;

public class FlattenHook {
    static public boolean shouldTick(Action act, boolean insta, float counter, byte type, boolean first) {
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
}
