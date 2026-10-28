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
    private static final int REBOUND_KEY = 79;
    private static final String HOTKEY_TYPE = "su.plo.voice.api.client.config.hotkey.Hotkey$Type";

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

    @Override
    String engineKindPackage() {
        return "client.ai.engine.";
    }

    /**
     * Voice activation at the lowest threshold Plasmo Voice allows, on the
     * parent activation, which is the one proximity chat uses.
     */
    @Override
    Runnable sendOnRoomNoise() throws ReflectiveOperationException {
        Object client = Reflect.staticField(VOICE_CLIENT, "INSTANCE");
        Object activation = ((Optional<?>) Reflect.call(Reflect.call(client, "getActivationManager"),
                "getParentActivation")).orElseThrow();
        Object type = Reflect.field(activation, "configType");
        Object toggle = Reflect.field(activation, "configToggle");
        Object threshold = Reflect.call(Reflect.call(Reflect.call(client, "getConfig"), "getVoice"),
                "getActivationThreshold");

        Object previousType = Reflect.call(type, "value");
        Object previousToggle = Reflect.call(toggle, "value");
        Object previousThreshold = Reflect.call(threshold, "value");
        @SuppressWarnings({"unchecked", "rawtypes"})
        Object voice = Enum.valueOf((Class) Reflect.type("su.plo.voice.api.client.audio.capture.ClientActivation$Type"), "VOICE");
        Reflect.call(type, "set", voice);
        Reflect.call(toggle, "set", false);
        Reflect.call(threshold, "set", -60.0D);

        return () -> {
            try {
                Reflect.call(type, "set", previousType);
                Reflect.call(toggle, "set", previousToggle);
                Reflect.call(threshold, "set", previousThreshold);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException(exception);
            }
        };
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
        probe.step("rebind the studio key in Plasmo Voice's settings", 20, (mc, t) -> {
            Object key = Reflect.call(Reflect.staticField(HOTKEY_TYPE, "KEYSYM"), "getOrCreate", REBOUND_KEY);
            Reflect.call(hotkeyEntry(HOTKEYS.get(1)), "updateKeys", java.util.Set.of(key));
            return true;
        });
        probe.step("studio from the Plasmo Voice menu", 200, (mc, t) -> {
            if (t == 0) {
                Screens.show(mc, newStudio(Screens.current(mc)));
            }
            return t >= 40 && isStudio(Screens.current(mc));
        });
        probe.screenshot("world-studio");
        probe.step("the rebound key survives a save and a reload", 100, (mc, t) -> t >= 20 && checkReboundKey());
    }

    /**
     * Reads the studio key back from the file Plasmo Voice saved when its
     * settings closed, with its own loader, as the next start would, then
     * puts the default back. Players reported the key reading "None" again
     * after a restart.
     */
    private static boolean checkReboundKey() throws ReflectiveOperationException {
        String id = HOTKEYS.get(1);
        Object config = Reflect.call(Reflect.staticField(VOICE_CLIENT, "INSTANCE"), "getConfig");
        java.io.File file = (java.io.File) Reflect.field(config, "configFile");
        Object toml = Reflect.call(Reflect.type("su.plo.config.provider.ConfigurationProvider"), "getProvider",
                Reflect.type("su.plo.config.provider.toml.TomlConfiguration"));
        Object reloaded = toml.getClass().getMethod("load", Class.class, java.io.File.class, boolean.class)
                .invoke(toml, config.getClass(), file, false);
        Optional<?> found = (Optional<?>) Reflect.call(Reflect.call(reloaded, "getKeyBindings"), "getConfigHotkey", id);
        if (found.isEmpty()) {
            logDeserializeSteps(file);
            throw new IllegalStateException(id + " is not read back from " + file);
        }
        java.util.Collection<?> keys = (java.util.Collection<?>) Reflect.call(Reflect.call(found.get(), "value"), "getKeys");
        List<Object> codes = new ArrayList<>();
        for (Object key : keys) {
            codes.add(Reflect.call(key, "getCode"));
        }
        Probe.LOG.info("[probe] {} after a save and a reload: {}", id, codes);
        if (!codes.equals(List.of(REBOUND_KEY))) {
            throw new IllegalStateException(id + " reloads as " + codes + ", bound to " + REBOUND_KEY);
        }
        Reflect.call(hotkeyEntry(id), "reset");
        Reflect.call(config, "save", false);
        return true;
    }

    /** Plasmo Voice drops a hotkey it cannot read without a word; this says which part failed. */
    private static void logDeserializeSteps(java.io.File file) {
        try {
            Object parsed = Reflect.call(Reflect.call(Reflect.type("su.plo.config.toml.Toml").getConstructor().newInstance(),
                    "read", file), "toMap");
            Object list = ((java.util.Map<?, ?>) parsed).get("key_bindings");
            Probe.LOG.info("[probe] parsed key_bindings: {} ({})", list, list == null ? "-" : list.getClass().getName());
            for (Object value : (List<?>) list) {
                java.util.Map<?, ?> map = (java.util.Map<?, ?>) value;
                map.forEach((k, v) -> Probe.LOG.info("[probe]   {} = {} ({})", k, v, v == null ? "-" : v.getClass().getName()));
                for (Object key : (List<?>) map.get("keys")) {
                    Object hotkey = Reflect.call(Reflect.type("su.plo.lib.mod.extensions.HotkeyKt"), "toHotkeyKey", key);
                    Probe.LOG.info("[probe]   key {} reads as {}", key, hotkey);
                }
            }
            Object fresh = Reflect.type("su.plo.voice.client.config.hotkey.ConfigHotkeys").getConstructor().newInstance();
            Reflect.call(fresh, "deserialize", list);
            Probe.LOG.info("[probe] a fresh ConfigHotkeys reads: {}", ((java.util.Map<?, ?>) Reflect.call(fresh, "getHotkeys")).keySet());
        } catch (Throwable throwable) {
            Probe.LOG.info("[probe] deserialize steps threw", throwable);
        }
    }

    private static Object hotkeyEntry(String id) throws ReflectiveOperationException {
        Object hotkeys = Reflect.call(Reflect.staticField(VOICE_CLIENT, "INSTANCE"), "getHotkeys");
        return ((Optional<?>) Reflect.call(hotkeys, "getConfigHotkey", id)).orElseThrow();
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
