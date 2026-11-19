package land.beezz.craft;

import land.beezz.craft.mixin.AbstractContainerScreenAccessor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import com.mojang.blaze3d.platform.InputConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * BeezzCraft: in the inventory or at a crafting table, Alt + right click on an item opens an octagon wheel of what can
 * be crafted from it. Click crafts once, Shift + click as much as possible, Ctrl + click ten times; clicking a grey
 * recipe puts it in the craft queue (when switched on behind the gear).
 */
public final class BeezzCraft implements ClientModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("BeezzCraft");
    /** The wheel that is open right now (one at a time), or null. */
    static Wheel wheel;
    /** Held with a right click on an item to open the wheel; Left Alt by default, rebindable in Controls. */
    public static KeyMapping wheelKey;

    @Override
    public void onInitializeClient() {
        wheelKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.beezzcraft.wheel", InputConstants.KEY_LALT, KeyMapping.Category.register(Identifier.fromNamespaceAndPath("beezzcraft", "main"))));
        ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
            if (!(screen instanceof InventoryScreen) && !(screen instanceof CraftingScreen)) return;
            AbstractContainerScreen<?> cs = (AbstractContainerScreen<?>) screen;
            wheel = null;
            ScreenMouseEvents.allowMouseClick(screen).register((s, e) -> {
                // the click's own modifier flags, or the keys held right now (some inputs don't fill the flags)
                Minecraft mc2 = Minecraft.getInstance();
                return mouse(cs, e.x(), e.y(), e.button(), wheelKeyDown(mc2, e.hasAltDown()),
                        e.hasShiftDown() || mc2.hasShiftDown(), e.hasControlDown() || mc2.hasControlDown());
            });
            ScreenMouseEvents.allowMouseRelease(screen).register((s, e) -> wheel == null);
            ScreenMouseEvents.allowMouseDrag(screen).register((s, e, dx, dy) -> wheel == null);
            ScreenMouseEvents.allowMouseScroll(screen).register((s, x, y, hx, vy) -> {
                if (wheel == null) return true;
                wheel.scroll(vy);
                return false;
            });
            ScreenKeyboardEvents.allowKeyPress(screen).register((s, e) -> {
                if (wheel == null) return true;
                int key = e.input();
                if (key == InputConstants.KEY_ESCAPE || Minecraft.getInstance().options.keyInventory.matches(e)) wheel = null;
                return false; // keys do nothing under the wheel
            });
            ScreenEvents.afterTick(screen).register(s -> {
                if (wheel != null && wheel.screen == s) wheel.tick();
            });
            ScreenEvents.remove(screen).register(s -> {
                if (wheel != null && wheel.screen == s) wheel = null;
                Panel.close();
            });
        });
        ClientTickEvents.END_CLIENT_TICK.register(Crafter::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> {
            Crafter.clear();
            Recipes.forget();
            wheel = null;
        });
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("beezzcraft", "queue"), (g, delta) -> hud(g));
        Config.get();
        LOG.info("BeezzCraft ready: wheel key (Alt by default) + right click on an item in the inventory or at a crafting table");
    }

    /** Called by ScreenMixin after the screen and before its tooltips. */
    public static void renderWheel(Screen screen, GuiGraphicsExtractor g, int mx, int my) {
        if (!(screen instanceof InventoryScreen) && !(screen instanceof CraftingScreen)) return;
        Font font = Screens.getFont(screen);
        if (wheel != null && wheel.screen == screen) wheel.render(g, font, mx, my);
        Panel.render((AbstractContainerScreen<?>) screen, g, font, mx, my);
    }

    public static boolean wheelOpenOn(Object screen) {
        return wheel != null && wheel.screen == screen;
    }

    /**
     * Is the wheel key held? An Alt binding takes either Alt (and the click's own Alt flag); any other key is read from
     * the keyboard, a mouse button from the mouse. Unbound means the wheel never opens.
     */
    static boolean wheelKeyDown(Minecraft mc, boolean altFlag) {
        InputConstants.Key key = KeyMappingHelper.getBoundKeyOf(wheelKey);
        int code = key.getValue();
        if (key.getType() == InputConstants.Type.MOUSE) return wheelKey.isDown();
        if (code == InputConstants.KEY_LALT || code == InputConstants.KEY_RALT) return altFlag || mc.hasAltDown();
        return code != InputConstants.UNKNOWN.getValue() && keyDown(mc, code);
    }

    private static java.lang.invoke.MethodHandle keyDownHandle;

    /** InputConstants.isKeyDown: (int) on 26.3, (Window, int) before; found once at run time. */
    private static boolean keyDown(Minecraft mc, int code) {
        try {
            if (keyDownHandle == null) {
                var lookup = java.lang.invoke.MethodHandles.publicLookup();
                var type = java.lang.invoke.MethodType.methodType(boolean.class, int.class);
                try {
                    keyDownHandle = lookup.findStatic(InputConstants.class, "isKeyDown", type);
                } catch (NoSuchMethodException e) {
                    var window = com.mojang.blaze3d.platform.Window.class;
                    keyDownHandle = lookup.findStatic(InputConstants.class, "isKeyDown", type.insertParameterTypes(0, window));
                    keyDownHandle = java.lang.invoke.MethodHandles.insertArguments(keyDownHandle, 0, mc.getWindow());
                }
            }
            return (boolean) keyDownHandle.invoke(code);
        } catch (Throwable t) {
            throw new IllegalStateException("BeezzCraft: cannot read the keyboard", t);
        }
    }

    /** @return true to let the screen handle the click as usual */
    private static boolean mouse(AbstractContainerScreen<?> screen, double mx, double my, int button, boolean alt, boolean shift, boolean ctrl) {
        if (Panel.click(mx, my, button)) return false; // the gear, its settings and the queue (top left)
        if (wheel != null) {
            if (!wheel.click(mx, my, button, shift, ctrl)) wheel = null;
            return false;
        }
        if (Boolean.getBoolean("beezzcraft.debug")) LOG.info("click button={} alt={} slot={}", button, alt, ((AbstractContainerScreenAccessor) screen).beezzcraft$hoveredSlot());
        if (!alt || button != InputConstants.MOUSE_BUTTON_RIGHT) return true;
        Slot slot = ((AbstractContainerScreenAccessor) screen).beezzcraft$hoveredSlot();
        if (slot == null || !slot.hasItem()) return true;
        open(screen, slot.getItem(), mx, my);
        return false;
    }

    static void open(AbstractContainerScreen<?> screen, ItemStack stack, double mx, double my) {
        Minecraft mc = Minecraft.getInstance();
        List<Recipes.Option> options = Recipes.uses(mc, stack, Recipes.gridSize(screen.getMenu()));
        if (Boolean.getBoolean("beezzcraft.debug")) {
            LOG.info("open {}: {} options, {} recipe collections", stack, options.size(), mc.player.getRecipeBook().getCollections().size());
        }
        if (options.isEmpty()) {
            mc.player.sendOverlayMessage(Component.translatable(Recipes.gridSize(screen.getMenu()) >= 3 ? "beezzcraft.nothing" : "beezzcraft.nothing.inv",
                    stack.getHoverName()).withStyle(ChatFormatting.GRAY));
            return;
        }
        wheel = new Wheel(screen, stack, options, mx, my);
    }

    /** The craft queue on the HUD while playing: a small card with what waits for resources. */
    private static void hud(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || screen(mc) != null) return;
        List<Crafter.Job> queued = Crafter.jobs().stream().filter(j -> j.queued).toList();
        if (queued.isEmpty()) return;
        Font font = mc.font;
        int shown = Math.min(queued.size(), 6), w = 22 + shown * 26, x = 4, y = 4;
        Ui.panel(g, x, y, w, 22);
        Ui.icon(g, Ui.QUEUE, x + 5, y + 5, 12, Ui.BLUE);
        for (int i = 0; i < shown; i++) {
            Crafter.Job j = queued.get(i);
            int ix = x + 20 + i * 26;
            g.item(j.result, ix, y + 3);
            String n = j.all ? "∞" : String.valueOf(j.left);
            g.text(font, n, ix + 17 - font.width(n), y + 12, 0xFFFFFFFF, true);
        }
        if (queued.size() > shown) g.text(font, "+" + (queued.size() - shown), x + w + 3, y + 7, Ui.MUTED, true);
        if (Config.get().showNeeds) Needs.render(g, font, Needs.of(mc.player, mc.player.inventoryMenu), x, y + 26, 0, false);
    }

    private static java.lang.invoke.MethodHandle screenGetter;

    /**
     * The open screen. 26.2+ keeps it in Gui (mc.gui.screen()), 26.1 in the Minecraft.screen field; found once at run
     * time so one jar source builds for every supported version.
     */
    static Screen screen(Minecraft mc) {
        try {
            if (screenGetter == null) {
                var lookup = java.lang.invoke.MethodHandles.publicLookup();
                try {
                    screenGetter = java.lang.invoke.MethodHandles.filterReturnValue(
                            lookup.findGetter(Minecraft.class, "gui", net.minecraft.client.gui.Gui.class),
                            lookup.findVirtual(net.minecraft.client.gui.Gui.class, "screen", java.lang.invoke.MethodType.methodType(Screen.class)));
                } catch (NoSuchMethodException | NoSuchFieldException e) {
                    screenGetter = lookup.findGetter(Minecraft.class, "screen", Screen.class);
                }
            }
            return (Screen) screenGetter.invoke(mc);
        } catch (Throwable t) {
            throw new IllegalStateException("BeezzCraft: cannot read the open screen", t);
        }
    }
}
