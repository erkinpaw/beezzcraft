package land.beezz.craft;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.lang.reflect.Field;

/**
 * Plays the mod in a real client: the wheel in the inventory, a hover tooltip, craft once / Shift / Ctrl, the craft
 * queue (a grey torch made by itself once coal arrives), the gear, and the wheel at a crafting table.
 * Every step saves a screenshot to build/run/clientGameTest/screenshots.
 */
public class WheelTest implements FabricClientGameTest {
    private static final int LEFT = InputConstants.MOUSE_BUTTON_LEFT, RIGHT = InputConstants.MOUSE_BUTTON_RIGHT;
    private static final String LANG = System.getenv().getOrDefault("BEEZZCRAFT_LANG", "ru_ru");
    private ClientGameTestContext ctx;

    private static Object field(Class<?> type, Object o, String name) {
        try {
            Field f = type.getDeclaredField(name);
            f.setAccessible(true);
            return f.get(o);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private void cursor(double x, double y) {
        double scale = ctx.computeOnClient(mc -> (double) mc.getWindow().getWidth() / BeezzCraft.screen(mc).width);
        ctx.getInput().setCursorPos(x * scale, y * scale);
        ctx.waitTicks(3);
    }

    private void click(double x, double y, int button) {
        cursor(x, y);
        ctx.getInput().pressMouse(button);
        ctx.waitTicks(4);
    }

    private void shot(String name) {
        ctx.waitTicks(6);
        ctx.takeScreenshot(name);
    }

    /** Screen point of the first inventory slot holding this item. */
    private double[] slotOf(Item item) {
        return ctx.computeOnClient(mc -> {
            AbstractContainerScreen<?> s = (AbstractContainerScreen<?>) BeezzCraft.screen(mc);
            int left = (int) field(AbstractContainerScreen.class, s, "leftPos"), top = (int) field(AbstractContainerScreen.class, s, "topPos");
            for (Slot slot : s.getMenu().slots) {
                if (slot.container instanceof Inventory && slot.getItem().is(item)) return new double[] {left + slot.x + 8, top + slot.y + 8};
            }
            throw new IllegalStateException("no slot with " + item);
        });
    }

    /** Opens the wheel on an item: Alt + right click. */
    private void wheelOn(Item item) {
        double[] p = slotOf(item);
        cursor(p[0], p[1]);
        ctx.getInput().holdAlt();
        ctx.getInput().pressMouse(RIGHT);
        ctx.getInput().releaseAlt();
        ctx.waitTicks(5);
        if (ctx.computeOnClient(mc -> BeezzCraft.wheel == null)) throw new IllegalStateException("the wheel did not open on " + item);
    }

    /** Screen point of the sector showing this result. */
    private double[] sectorOf(Item result) {
        return ctx.computeOnClient(mc -> {
            Wheel w = BeezzCraft.wheel;
            for (int i = 0; i < w.shown.size(); i++) {
                var group = w.shown.get(i);
                int m = -1;
                for (int k = 0; k < group.members.size(); k++) if (group.members.get(k).result.is(result)) m = k;
                if (m < 0) continue;
                group.sel = m;
                w.page = i / Wheel.PER_PAGE;
                float[] p = w.centre(i);
                return new double[] {p[0], p[1]};
            }
            throw new IllegalStateException("no sector for " + result + " among " + w.shown.size());
        });
    }

    /** The recipes of the open wheel in their order. */
    private String order() {
        return ctx.computeOnClient(mc -> BeezzCraft.wheel.shown.stream().map(g -> g.current().itemId()).toList().toString());
    }

    private int count(Item item) {
        return ctx.computeOnClient(mc -> mc.player.getInventory().countItem(item));
    }

    private void check(String what, boolean ok) {
        BeezzCraft.LOG.info("BeezzCraft test: {} {}", ok ? "PASS" : "FAIL", what);
        if (!ok) throw new AssertionError(what);
    }

    private void waitCount(Item item, int atLeast) {
        ctx.waitFor(mc -> mc.player.getInventory().countItem(item) >= atLeast, 100);
    }

    @Override
    public void runTest(ClientGameTestContext ctx) {
        this.ctx = ctx;
        System.setProperty("beezzcraft.debug", "true");
        ctx.getInput().resizeWindow(1920, 1080);
        ctx.runOnClient(mc -> {
            mc.options.languageCode = LANG;
            mc.getLanguageManager().setSelected(LANG);
            mc.options.guiScale().set(3);
            mc.resizeGui();
        });
        ctx.runOnClient(Minecraft::reloadResourcePacks);
        ctx.waitFor(mc -> overlay(mc) == null, 20 * 120);
        Config.get().queue = false;
        Config.get().recent.clear();
        Config.get().counts.clear();

        try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
            var server = world.getServer();
            ctx.waitTicks(60);
            for (String c : new String[] {"gamemode survival @a", "clear @a", "recipe give @a *", "time set day", "weather clear",
                    "give @a oak_planks 12", "give @a stick 2", "give @a iron_ingot 3", "give @a cobblestone 8"}) {
                server.runCommand(c);
            }
            ctx.waitTicks(20);

            // 1) the wheel in the inventory: 2x2 recipes from planks
            ctx.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
            ctx.waitForScreen(InventoryScreen.class);
            // 0) the wheel key can be rebound in Controls: V + right click opens it, then back to Alt
            ctx.runOnClient(mc -> {
                BeezzCraft.wheelKey.setKey(InputConstants.getKey("key.keyboard.v"));
                net.minecraft.client.KeyMapping.resetMapping();
            });
            double[] plankSlot = slotOf(Items.OAK_PLANKS);
            cursor(plankSlot[0], plankSlot[1]);
            ctx.getInput().holdKey(InputConstants.KEY_V);
            ctx.getInput().pressMouse(RIGHT);
            ctx.getInput().releaseKey(InputConstants.KEY_V);
            ctx.waitTicks(5);
            check("a rebound wheel key (V) + right click opens the wheel", ctx.computeOnClient(mc -> BeezzCraft.wheel != null));
            ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
            ctx.waitTicks(3);
            ctx.runOnClient(mc -> {
                BeezzCraft.wheelKey.setKey(BeezzCraft.wheelKey.getDefaultKey());
                net.minecraft.client.KeyMapping.resetMapping();
            });
            wheelOn(Items.OAK_PLANKS);
            shot("craft_01_inventory_wheel");
            double[] table = sectorOf(Items.CRAFTING_TABLE);
            cursor(table[0], table[1]);
            shot("craft_02_hover_tooltip");

            // 2) click crafts once, Ctrl ten times, Shift as much as possible
            String before = order();
            int sticks = count(Items.STICK);
            double[] stick = sectorOf(Items.STICK);
            click(stick[0], stick[1], LEFT);
            waitCount(Items.STICK, sticks + 4);
            check("click crafts sticks once: " + sticks + " -> " + count(Items.STICK), count(Items.STICK) == sticks + 4);
            ctx.waitTicks(12); // the wheel has refreshed by now
            check("crafting does not move the recipes around", order().equals(before));
            ctx.waitTicks(8); // the wheel re-sorts after a craft
            table = sectorOf(Items.CRAFTING_TABLE);
            click(table[0], table[1], LEFT);
            waitCount(Items.CRAFTING_TABLE, 1);
            check("crafting table crafted in the inventory", count(Items.CRAFTING_TABLE) == 1);
            // Ctrl: ten at once (here the planks allow three), all in one go, not one by one
            server.runCommand("give @a oak_planks 6");
            ctx.waitTicks(5);
            int before10 = count(Items.STICK), planks6 = count(Items.OAK_PLANKS);
            stick = sectorOf(Items.STICK);
            cursor(stick[0], stick[1]);
            ctx.getInput().holdControl();
            ctx.getInput().pressMouse(LEFT);
            ctx.getInput().releaseControl();
            int ticks = ctx.waitFor(mc -> mc.player.getInventory().countItem(Items.STICK) >= before10 + planks6 / 2 * 4, 40);
            check("Ctrl + click crafts all it can at once (" + planks6 / 2 + " crafts in " + ticks + " ticks)", ticks <= 4);
            server.runCommand("give @a oak_planks 8");
            ctx.waitTicks(5);
            int planks = count(Items.OAK_PLANKS);
            ctx.waitTicks(8);
            stick = sectorOf(Items.STICK);
            cursor(stick[0], stick[1]);
            ctx.getInput().holdShift();
            ctx.getInput().pressMouse(LEFT);
            ctx.getInput().releaseShift();
            ctx.waitFor(mc -> mc.player.getInventory().countItem(Items.OAK_PLANKS) <= 1, 100);
            check("Shift + click uses up the planks: " + planks + " -> " + count(Items.OAK_PLANKS), count(Items.OAK_PLANKS) <= 1);
            shot("craft_03_after_crafting");

            // 3) the gear and the craft queue: a torch needs coal we don't have yet
            ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
            ctx.waitTicks(3);
            check("Esc closes only the wheel", ctx.computeOnClient(mc -> BeezzCraft.wheel == null && BeezzCraft.screen(mc) instanceof InventoryScreen));
            wheelOn(Items.STICK);
            // the gear sits in the top left corner of the inventory; its first switch is the craft queue
            click(16, 16, LEFT);
            shot("craft_04_settings");
            click(90, 6 + 20 + 6 + 4 + 9, LEFT);
            check("the switch turns the craft queue on", Config.get().queue);
            click(16, 16, LEFT); // close the settings
            ctx.waitTicks(8);
            double[] torch = sectorOf(Items.TORCH);
            cursor(torch[0], torch[1]);
            shot("craft_05_grey_torch");
            ctx.getInput().holdControl();
            ctx.getInput().pressMouse(LEFT);
            ctx.getInput().releaseControl();
            ctx.waitTicks(5);
            ctx.getInput().pressMouse(LEFT);
            ctx.waitTicks(3);
            ctx.getInput().pressMouse(LEFT);
            ctx.waitTicks(5);
            check("the same recipe queued again adds up on one line: Ctrl + 2 clicks = ×12",
                    ctx.computeOnClient(mc -> Crafter.jobs().stream().filter(j -> j.queued).count() == 1
                            && Crafter.jobs().stream().anyMatch(j -> j.queued && j.left == 12)));
            shot("craft_06_queued");
            int coalMissing = ctx.computeOnClient(mc -> Needs.of(mc.player, mc.player.inventoryMenu).stream()
                    .filter(n -> n.icon().is(Items.COAL) || n.icon().is(Items.CHARCOAL)).mapToInt(Needs.Item::missing).sum());
            check("what to get: 12 coal for the 12 queued torches (" + coalMissing + ")", coalMissing == 12);
            ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
            ctx.waitTicks(3);
            shot("craft_06b_needs_in_inventory");
            ctx.setScreen(() -> null);
            ctx.waitTicks(10);
            shot("craft_06c_needs_while_playing");
            server.runCommand("give @a coal 2");
            waitCount(Items.TORCH, 8);
            check("queued torches crafted by themselves once coal came: " + count(Items.TORCH), count(Items.TORCH) == 8);
            check("what the coal could not cover stays queued: ×10 left",
                    ctx.computeOnClient(mc -> Crafter.jobs().stream().anyMatch(j -> j.queued && j.left == 10)));
            ctx.waitTicks(10);
            shot("craft_07_queue_hud");
            ctx.runOnClient(mc -> Crafter.clear());

            // a recipe the recipe book does not have (a torch before any stone pickaxe): laid out by hand clicks
            server.runCommand("recipe take @a minecraft:torch");
            server.runCommand("give @a coal 1");
            ctx.waitTicks(10);
            ctx.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
            ctx.waitForScreen(InventoryScreen.class);
            int torches = count(Items.TORCH);
            wheelOn(Items.COAL);
            check("a torch shows up although the recipe book does not know it", ctx.computeOnClient(mc ->
                    BeezzCraft.wheel.shown.stream().anyMatch(g -> g.members.stream().anyMatch(o -> o.result.is(Items.TORCH) && o.entry == null))));
            double[] t2 = sectorOf(Items.TORCH);
            click(t2[0], t2[1], LEFT);
            waitCount(Items.TORCH, torches + 4);
            check("the unknown torch recipe is crafted by hand: " + torches + " -> " + count(Items.TORCH), count(Items.TORCH) == torches + 4);
            ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
            ctx.waitTicks(3);
            ctx.setScreen(() -> null);
            ctx.waitTicks(5);

            // 4) at a crafting table: 3x3 recipes from iron
            server.runCommand("give @a stick 4");
            BlockPos pos = ctx.computeOnClient(mc -> mc.player.blockPosition().north(2));
            server.runCommand("setblock " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " crafting_table");
            ctx.waitTicks(10);
            ctx.getInput().lookAt(pos);
            ctx.waitTicks(5);
            ctx.getInput().pressKey(mc -> mc.keyUse);
            ctx.waitForScreen(CraftingScreen.class);
            wheelOn(Items.IRON_INGOT);
            double[] pick = sectorOf(Items.IRON_PICKAXE);
            cursor(pick[0], pick[1]);
            shot("craft_08_table_wheel");
            click(pick[0], pick[1], LEFT);
            waitCount(Items.IRON_PICKAXE, 1);
            check("iron pickaxe crafted at the table", count(Items.IRON_PICKAXE) == 1);
            shot("craft_09_table_after");

            // sticks at the table: more recipes than one page holds
            ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
            ctx.waitTicks(3);
            wheelOn(Items.STICK);
            int[] info = ctx.computeOnClient(mc -> new int[] {BeezzCraft.wheel.shown.size(), BeezzCraft.wheel.pages(),
                    (int) BeezzCraft.wheel.shown.stream().filter(g -> g.current().result.is(Items.TORCH)).count()});
            int torchAt = ctx.computeOnClient(mc -> {
                for (int i = 0; i < BeezzCraft.wheel.shown.size(); i++) if (BeezzCraft.wheel.shown.get(i).current().result.is(Items.TORCH)) return i;
                return -1;
            });
            BeezzCraft.LOG.info("BeezzCraft test: stick at the table: {} recipes, {} pages, torch at {}", info[0], info[1], torchAt);
            check("a torch short of only coal is on the first page, not lost among wood variants", torchAt >= 0 && torchAt < Wheel.PER_PAGE);
            check("sticks at the table fit on one page (" + info[0] + " buttons)", info[1] == 1);
            String pickaxeShown = ctx.computeOnClient(mc -> BeezzCraft.wheel.shown.stream().filter(g -> g.family.equals("pickaxe"))
                    .map(g -> g.current().itemId()).findFirst().orElse("none"));
            check("the pickaxe button opens on the iron pickaxe crafted last: " + pickaxeShown, pickaxeShown.equals("minecraft:iron_pickaxe"));
            String pickaxes = ctx.computeOnClient(mc -> {
                StringBuilder sb = new StringBuilder();
                int last = -2;
                boolean together = true;
                for (int i = 0; i < BeezzCraft.wheel.shown.size(); i++) {
                    var grp = BeezzCraft.wheel.shown.get(i);
                    if (!grp.family.equals("pickaxe")) continue;
                    if (last >= 0) together = false; // one button
                    last = i;
                    for (var m : grp.members) sb.append(m.itemId().substring(10)).append(' ');
                }
                return (together ? "together: " : "SPLIT: ") + sb.toString().trim();
            });
            BeezzCraft.LOG.info("BeezzCraft test: pickaxes {}", pickaxes);
            check("all pickaxes are one button, tiers in order from wooden to diamond", pickaxes.startsWith("together: wooden_pickaxe stone_pickaxe")
                    && pickaxes.indexOf("iron_pickaxe") < pickaxes.indexOf("golden_pickaxe") && pickaxes.indexOf("golden_pickaxe") < pickaxes.indexOf("diamond_pickaxe"));
            int fences = ctx.computeOnClient(mc -> BeezzCraft.wheel.shown.stream().filter(g -> g.family.equals("fence")).mapToInt(g -> g.members.size()).sum());
            int fenceButtons = ctx.computeOnClient(mc -> (int) BeezzCraft.wheel.shown.stream().filter(g -> g.family.equals("fence")).count());
            check("all " + fences + " wooden fences are one button", fences > 5 && fenceButtons == 1);
            java.util.List<java.util.List<String>> torchButtons = ctx.computeOnClient(mc -> BeezzCraft.wheel.shown.stream()
                    .filter(g -> g.members.stream().anyMatch(o -> o.result.is(Items.TORCH) || o.result.is(Items.SOUL_TORCH)))
                    .map(g -> g.members.stream().map(o -> o.itemId().substring(10)).toList()).toList());
            check("the torch and the soul torch are one button: " + torchButtons, torchButtons.size() == 1
                    && torchButtons.getFirst().contains("torch") && torchButtons.getFirst().contains("soul_torch"));

            // multiplayer: the recipes come from the game's own data instead of the server
            ctx.runOnClient(mc -> {
                System.setProperty("beezzcraft.vanillaData", "true");
                Recipes.forget();
            });
            int known = ctx.computeOnClient(mc -> Recipes.catalog(mc).size());
            ctx.runOnClient(mc -> {
                System.clearProperty("beezzcraft.vanillaData");
                Recipes.forget();
            });
            check("the game's own recipe data reads (multiplayer): " + known + " crafting recipes", known > 400);
            shot("craft_10_table_sticks_page1");
            ctx.getInput().scroll(-1);
            ctx.waitTicks(5);
            shot("craft_11_table_sticks_page2");
            ctx.setScreen(() -> null);
        }
        Config.get().queue = false;
        Config.get().recent.clear();
        Config.get().save();
        BeezzCraft.LOG.info("BeezzCraft test: all steps done");
    }

    /** The loading overlay: mc.gui.overlay() since 26.2, mc.getOverlay() in 26.1. */
    private static Object overlay(Minecraft mc) {
        try {
            Object gui = Minecraft.class.getField("gui").get(mc);
            return gui.getClass().getMethod("overlay").invoke(gui);
        } catch (ReflectiveOperationException e) {
            try {
                return Minecraft.class.getMethod("getOverlay").invoke(mc);
            } catch (ReflectiveOperationException e2) {
                throw new IllegalStateException(e2);
            }
        }
    }
}
