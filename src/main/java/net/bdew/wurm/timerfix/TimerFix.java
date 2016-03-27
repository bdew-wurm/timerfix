package net.bdew.wurm.timerfix;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.NotFoundException;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.interfaces.*;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class TimerFix implements WurmMod, Initable, PreInitable, ServerStartedListener, Configurable {
    private static final Logger logger = Logger.getLogger("TimerFix");

    enum Patches {
        FLATTEN, SPELLS, DESTROY, PRAY, SACRIFICE, SOW, MEDITATE, ALCHEMY, MISC
    }

    static EnumSet<Patches> enabledPatches = EnumSet.noneOf(Patches.class);

    static Set<String> spellBlacklist = new HashSet<>();
    static int minSpellTimer = 2;

    public static void logException(String msg, Throwable e) {
        if (logger != null)
            logger.log(Level.SEVERE, msg, e);
    }

    public static void logWarning(String msg) {
        if (logger != null)
            logger.log(Level.WARNING, msg);
    }

    public static void logInfo(String msg) {
        if (logger != null)
            logger.log(Level.INFO, msg);
    }

    static String sanitizeSpellName(String name) {
        return name.toLowerCase().replaceAll("[^A-Za-z0-9]", "");
    }

    @Override
    public void configure(Properties properties) {
        String patches = properties.getProperty("enabledPatches", "").trim();
        if (patches.indexOf(",") > 0) {
            for (String name : patches.split(","))
                enabledPatches.add(Patches.valueOf(name.trim()));
        } else if (patches.length() > 0) {
            enabledPatches.add(Patches.valueOf(patches));
        }
        logInfo("Enabled Patches: " + String.join(",", enabledPatches.stream().map(Enum::name).collect(Collectors.toList())));

        String spells = properties.getProperty("spellBlacklist", "").trim();
        if (spells.indexOf(",") > 0) {
            for (String name : spells.split(","))
                spellBlacklist.add(sanitizeSpellName(name));
        } else if (spells.length() > 0) {
            spellBlacklist.add(sanitizeSpellName(spells));
        }
        logInfo("Spell blacklist: " + String.join(",", spellBlacklist));

        minSpellTimer = Integer.parseInt(properties.getProperty("minSpellTimer", "2"));
        logInfo("minSpellTimer: " + minSpellTimer);
    }

    private static void applyEdit(ClassPool cp, String cls, String method, String descr, boolean sendActionControlPatch, boolean setTimeLeftPatch, boolean getCounterAsFloatPatch) throws NotFoundException, CannotCompileException {
        cp.getCtClass(cls).getMethod(method, descr).instrument(new ExprEditor() {
            @Override
            public void edit(MethodCall m) throws CannotCompileException {
                if (sendActionControlPatch && m.getMethodName().equals("sendActionControl")) {
                    m.replace("$proceed($1,$2,(int)($3/com.wurmonline.server.Servers.localServer.getActionTimer()));");
                    logInfo("Applied timer fix to sendActionControl in " + m.where().getDeclaringClass().getName() + " " + m.where().getMethodInfo().getName() + " " + m.getLineNumber());
                } else if (setTimeLeftPatch && m.getMethodName().equals("setTimeLeft")) {
                    m.replace("$proceed((int)($1/com.wurmonline.server.Servers.localServer.getActionTimer()));");
                    logInfo("Applied timer fix to setTimeLeft in " + m.where().getDeclaringClass().getName() + " " + m.where().getMethodInfo().getName() + " " + m.getLineNumber());
                } else if (getCounterAsFloatPatch && m.getMethodName().equals("getCounterAsFloat")) {
                    m.replace("$_ = $proceed() * com.wurmonline.server.Servers.localServer.getActionTimer();");
                    logInfo("Applied timer fix to getCounterAsFloat in " + m.where().getDeclaringClass().getName() + " " + m.where().getMethodInfo().getName() + " " + m.getLineNumber());
                }
            }
        });
    }

    @Override
    public void init() {
        try {
            ClassPool classPool = HookManager.getInstance().getClassPool();

            if (enabledPatches.contains(Patches.DESTROY)) {
                applyEdit(
                        classPool,
                        "com.wurmonline.server.behaviours.MethodsStructure",
                        "destroyWall",
                        "(SLcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/items/Item;Lcom/wurmonline/server/structures/Wall;ZF)Z",
                        true, true, false
                );

                applyEdit(
                        classPool,
                        "com.wurmonline.server.behaviours.MethodsStructure",
                        "destroyFence",
                        "(SLcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/items/Item;Lcom/wurmonline/server/structures/Fence;ZF)Z",
                        true, true, false
                );

                applyEdit(
                        classPool,
                        "com.wurmonline.server.behaviours.MethodsItems",
                        "destroyItem",
                        "(ILcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/items/Item;Lcom/wurmonline/server/items/Item;ZF)Z",
                        true, true, false
                );
            }

            if (enabledPatches.contains(Patches.SOW)) {
                // SOW
                applyEdit(
                        classPool,
                        "com.wurmonline.server.behaviours.TileDirtBehaviour",
                        "action",
                        "(Lcom/wurmonline/server/behaviours/Action;Lcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/items/Item;IIZIISF)Z",
                        true, true, false
                );
            }

            if (enabledPatches.contains(Patches.PRAY)) {
                applyEdit(
                        classPool,
                        "com.wurmonline.server.behaviours.MethodsReligion",
                        "pray",
                        "(Lcom/wurmonline/server/behaviours/Action;Lcom/wurmonline/server/creatures/Creature;F)Z",
                        true, true, false
                );

                applyEdit(
                        classPool,
                        "com.wurmonline.server.behaviours.MethodsReligion",
                        "pray",
                        "(Lcom/wurmonline/server/behaviours/Action;Lcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/items/Item;F)Z",
                        true, true, false
                );
            }

            if (enabledPatches.contains(Patches.SACRIFICE)) {
                classPool.getCtClass("com.wurmonline.server.behaviours.MethodsReligion").getMethod("sacrifice", "(Lcom/wurmonline/server/behaviours/Action;Lcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/items/Item;)Z").instrument(new ExprEditor() {
                    @Override
                    public void edit(MethodCall m) throws CannotCompileException {
                        if (m.getMethodName().equals("currentSecond")) {
                            m.replace("if ($proceed()>1) $_=$proceed()*com.wurmonline.server.Servers.localServer.getActionTimer(); else $_=$proceed();");
                            logInfo("Applied timer fix to currentSecond in " + m.where().getDeclaringClass().getName() + " " + m.where().getMethodInfo().getName() + " " + m.getLineNumber());
                        } else if (m.getMethodName().equals("sendActionControl")) {
                            m.replace("$proceed($1,$2,(int)($3/com.wurmonline.server.Servers.localServer.getActionTimer()));");
                            logInfo("Applied timer fix to sendActionControl in " + m.where().getDeclaringClass().getName() + " " + m.where().getMethodInfo().getName() + " " + m.getLineNumber());
                        }
                    }
                });
            }

            if (enabledPatches.contains(Patches.MEDITATE)) {
                applyEdit(
                        classPool,
                        "com.wurmonline.server.players.Cults",
                        "meditate",
                        "(Lcom/wurmonline/server/creatures/Creature;ILcom/wurmonline/server/behaviours/Action;FLcom/wurmonline/server/items/Item;)Z",
                        true, true, false
                );
            }

            if (enabledPatches.contains(Patches.ALCHEMY)) {
                applyEdit(
                        classPool,
                        "com.wurmonline.server.behaviours.MethodsItems",
                        "smear",
                        "(Lcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/items/Item;Lcom/wurmonline/server/items/Item;Lcom/wurmonline/server/behaviours/Action;F)Z",
                        false, true, false
                );

                applyEdit(
                        classPool,
                        "com.wurmonline.server.behaviours.MethodsItems",
                        "createOil",
                        "(Lcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/items/Item;Lcom/wurmonline/server/items/Item;Lcom/wurmonline/server/behaviours/Action;F)Z",
                        false, true, false
                );

                applyEdit(
                        classPool,
                        "com.wurmonline.server.behaviours.MethodsItems",
                        "createSalve",
                        "(Lcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/items/Item;Lcom/wurmonline/server/items/Item;Lcom/wurmonline/server/behaviours/Action;F)Z",
                        false, true, false
                );
            }

            if (enabledPatches.contains(Patches.MISC)) {
                applyEdit(
                        classPool,
                        "com.wurmonline.server.behaviours.MethodsStructure",
                        "colorWall",
                        "(Lcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/items/Item;Lcom/wurmonline/server/structures/Wall;Lcom/wurmonline/server/behaviours/Action;)Z",
                        true, false, true
                );

                applyEdit(
                        classPool,
                        "com.wurmonline.server.behaviours.MethodsStructure",
                        "removeColor",
                        "(Lcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/items/Item;Lcom/wurmonline/server/structures/Wall;Lcom/wurmonline/server/behaviours/Action;)Z",
                        true, false, true
                );

                applyEdit(
                        classPool,
                        "com.wurmonline.server.behaviours.MethodsStructure",
                        "colorFence",
                        "(Lcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/items/Item;Lcom/wurmonline/server/structures/Fence;Lcom/wurmonline/server/behaviours/Action;)Z",
                        true, false, true
                );

                applyEdit(
                        classPool,
                        "com.wurmonline.server.behaviours.MethodsStructure",
                        "colorFence",
                        "(Lcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/items/Item;Lcom/wurmonline/server/structures/Fence;Lcom/wurmonline/server/behaviours/Action;)Z",
                        true, false, true
                );


                applyEdit(
                        classPool,
                        "com.wurmonline.server.behaviours.MethodsItems",
                        "colorItem",
                        "(Lcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/items/Item;Lcom/wurmonline/server/items/Item;Lcom/wurmonline/server/behaviours/Action;)Z",
                        true, true, false
                );

                applyEdit(
                        classPool,
                        "com.wurmonline.server.behaviours.MethodsItems",
                        "improveColor",
                        "(Lcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/items/Item;Lcom/wurmonline/server/items/Item;Lcom/wurmonline/server/behaviours/Action;)Z",
                        true, true, false
                );


                applyEdit(
                        classPool,
                        "com.wurmonline.server.behaviours.MethodsItems",
                        "removeColor",
                        "(Lcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/items/Item;Lcom/wurmonline/server/items/Item;Lcom/wurmonline/server/behaviours/Action;)Z",
                        true, true, false
                );


                applyEdit(
                        classPool,
                        "com.wurmonline.server.behaviours.MethodsItems",
                        "string",
                        "(Lcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/items/Item;Lcom/wurmonline/server/items/Item;Lcom/wurmonline/server/behaviours/Action;)Z",
                        true, true, false
                );


                applyEdit(
                        classPool,
                        "com.wurmonline.server.behaviours.MethodsItems",
                        "stringRod",
                        "(Lcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/items/Item;Lcom/wurmonline/server/items/Item;Lcom/wurmonline/server/behaviours/Action;)Z",
                        true, true, false
                );

                applyEdit(
                        classPool,
                        "com.wurmonline.server.behaviours.MethodsItems",
                        "unstringBow",
                        "(Lcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/items/Item;Lcom/wurmonline/server/behaviours/Action;F)Z",
                        true, true, false
                );
            }

            if (enabledPatches.contains(Patches.SPELLS)) {
                classPool.getCtClass("com.wurmonline.server.spells.Spell").getMethod("getCastingTime", "(Lcom/wurmonline/server/creatures/Creature;)I").insertAfter(
                        "return net.bdew.wurm.timerfix.TimerHooks.getCastingTime(this, $_);"
                );
            }

        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void preInit() {
        if (enabledPatches.contains(Patches.FLATTEN)) {
            FlattenPatcher.patchFlatten();
        }
    }

    @Override
    public void onServerStarted() {
    }

}
