package probe;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;

/** Plasmo Voice Voice Changer, opened the way players reach it: from Plasmo Voice's settings. */
final class PlasmoTarget extends Target {
    private static final String VOICE_CLIENT = "su.plo.voice.client.ModVoiceClient";
    private static final List<String> HOTKEYS = List.of("pvvoicechanger.toggle", "pvvoicechanger.open_studio");
    private static final String HOTKEY_CATEGORY = "category.pvvoicechanger";

    PlasmoTarget() {
        super("org.sawiq.client.ui.VoiceChangerStudioScreen", "org.sawiq.client.VoiceChangerAddon");
    }

    @Override
    boolean isVoiceConnected() throws ReflectiveOperationException {
        Object client = Reflect.staticField(VOICE_CLIENT, "INSTANCE");
        Object udp = Reflect.call(client, "getUdpClientManager");
        return (boolean) Reflect.call(udp, "isConnected")
                && ((Optional<?>) Reflect.call(client, "getServerInfo")).isPresent();
    }

    /** The keys live in Plasmo Voice's hotkey list, which labels each one by its id and heading. */
    @Override
    List<String> bindingProblems(Minecraft mc) throws ReflectiveOperationException {
        Object hotkeys = Reflect.call(Reflect.staticField(VOICE_CLIENT, "INSTANCE"), "getHotkeys");
        List<String> problems = new ArrayList<>();
        for (String id : HOTKEYS) {
            if (((Optional<?>) Reflect.call(hotkeys, "getHotkey", id)).isEmpty()) {
                problems.add("hotkey " + id + " is not registered");
            } else if (!isTranslated(id)) {
                problems.add("hotkey " + id + " has no translated name");
            }
        }
        if (!isTranslated(HOTKEY_CATEGORY)) {
            problems.add("hotkey heading " + HOTKEY_CATEGORY + " has no translation");
        }
        return problems;
    }

    @Override
    void addWorldSteps(Probe probe) {
        probe.step("Plasmo Voice settings", 200, (mc, t) -> {
            if (t == 0) {
                Object screens = Reflect.staticField("su.plo.voice.client.gui.settings.VoiceScreens", "INSTANCE");
                Reflect.call(screens, "openSettings", Reflect.staticField(VOICE_CLIENT, "INSTANCE"));
            }
            return t >= 40 && settingsScreen() != null;
        });
        probe.screenshot("pv-settings");
        probe.step("activation tab with the voice changer section", 200, (mc, t) -> {
            if (t == 0) {
                openActivationTab();
            }
            return t >= 40;
        });
        probe.screenshot("pv-activation-tab");
        probe.step("scroll to the voice changer section", 100, (mc, t) -> {
            if (t == 0) {
                Object navigation = Reflect.call(settingsScreen(), "getNavigation");
                Object tab = ((Optional<?>) Reflect.call(navigation, "getActiveTab")).orElseThrow();
                Reflect.call(tab, "setScrollTop", (double) (int) Reflect.call(tab, "getMaxScroll"));
            }
            return t >= 20;
        });
        probe.screenshot("pv-activation-tab-voice-changer");
        probe.step("studio from the Plasmo Voice menu", 200, (mc, t) -> {
            if (t == 0) {
                Screens.show(mc, newStudio(Screens.current(mc)));
            }
            return t >= 40 && isStudio(Screens.current(mc));
        });
        probe.screenshot("world-studio");
    }

    private static Object settingsScreen() throws ReflectiveOperationException {
        Optional<?> wrapper = (Optional<?>) Reflect.call(
                Reflect.type("su.plo.lib.mod.client.gui.screen.ScreenWrapper"), "getCurrentWrappedScreen");
        if (wrapper.isEmpty()) {
            return null;
        }
        Object screen = Reflect.call(wrapper.get(), "getScreen");
        return screen.getClass().getSimpleName().equals("VoiceSettingsScreen") ? screen : null;
    }

    private static void openActivationTab() throws ReflectiveOperationException {
        Object navigation = Reflect.call(settingsScreen(), "getNavigation");
        List<?> tabs = (List<?>) Reflect.field(navigation, "tabWidgets");
        for (int i = 0; i < tabs.size(); i++) {
            if (tabs.get(i).getClass().getSimpleName().equals("ActivationTabWidget")) {
                Reflect.call(navigation, "openTab", i);
                return;
            }
        }
        throw new IllegalStateException("Plasmo Voice settings have no activation tab");
    }
}
