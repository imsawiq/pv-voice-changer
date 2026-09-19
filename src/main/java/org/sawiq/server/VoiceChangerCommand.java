package org.sawiq.server;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.sawiq.protocol.SharedPreset;
import org.sawiq.protocol.VoiceSourceRule;
import su.plo.slib.api.chat.component.McTextComponent;
import su.plo.slib.api.chat.style.McTextStyle;
import su.plo.slib.api.command.McCommand;
import su.plo.slib.api.command.McCommandSource;
import su.plo.slib.api.entity.player.McGameProfile;
import su.plo.slib.api.server.entity.player.McServerPlayer;

/**
 * {@code /voicechanger} — what a moderator uses to run the policy.
 *
 * <p>Muting works on a UUID looked up from the name, so it applies to an
 * offline player, survives a name change, and cannot be shed by reconnecting.
 * </p>
 */
public final class VoiceChangerCommand implements McCommand {
    private static final List<String> SUBCOMMANDS =
            List.of("status", "on", "off", "restrict", "mute", "unmute", "mutelist", "voices", "reload");

    private final VoiceChangerServerAddon addon;

    public VoiceChangerCommand(VoiceChangerServerAddon addon) {
        this.addon = addon;
    }

    @Override
    public boolean hasPermission(McCommandSource source, String[] arguments) {
        return source.hasPermission(VoiceChangerServerAddon.PERMISSION_COMMAND);
    }

    @Override
    public List<String> suggest(McCommandSource source, String[] arguments) {
        if (arguments.length <= 1) {
            return prefixed(SUBCOMMANDS, arguments.length == 0 ? "" : arguments[0]);
        }
        if (arguments.length == 2 && isPlayerSubcommand(arguments[0])) {
            return prefixed(playerNames(arguments[0]), arguments[1]);
        }
        if (arguments.length == 2 && arguments[0].equalsIgnoreCase("restrict")) {
            return prefixed(ruleNames(), arguments[1]);
        }
        return List.of();
    }

    @Override
    public void execute(McCommandSource source, String[] arguments) {
        if (!this.addon.isReady()) {
            source.sendMessage(error(
                    "The voice changer is still starting up. Try again in a moment."));
            return;
        }

        if (arguments.length == 0) {
            status(source);
            return;
        }

        switch (arguments[0].toLowerCase(Locale.ROOT)) {
            case "status" -> status(source);
            case "on" -> setAllowed(source, true);
            case "off" -> setAllowed(source, false);
            case "restrict" -> setRestriction(source, arguments);
            case "mute" -> setMuted(source, arguments, true);
            case "unmute" -> setMuted(source, arguments, false);
            case "mutelist" -> muteList(source);
            case "voices" -> voices(source);
            case "reload" -> reload(source);
            default -> usage(source);
        }
    }

    // --- Subcommands ---------------------------------------------------------

    private void status(McCommandSource source) {
        boolean allowed = this.addon.config().isAllowed();
        sendLines(source,
                McTextComponent.literal("Voice changer: ")
                        .append(McTextComponent.literal(allowed ? "allowed" : "denied")
                                .withStyle(allowed ? McTextStyle.GREEN : McTextStyle.RED)),
                info("Voices allowed: " + this.addon.config().getAllowedVoices().configValue()),
                info(this.addon.mutes().size() + " player(s) muted, "
                        + this.addon.presetLibrary().presets().size() + " shared voice(s)"),
                info("The voice is changed on the player's machine, so this is a policy an "
                        + "unmodified client follows, not something the server can enforce."));
    }

    private void setAllowed(McCommandSource source, boolean allowed) {
        if (this.addon.config().isAllowed() == allowed) {
            source.sendMessage(info("The voice changer is already "
                    + (allowed ? "allowed" : "denied") + " here."));
            return;
        }

        McTextComponent warning = null;
        try {
            this.addon.config().setAllowed(allowed);
        } catch (IOException exception) {
            // The change is live either way; what failed is remembering it.
            warning = error("Applied, but could not write the config: "
                    + describe(exception) + ". It will revert on restart.");
        }

        this.addon.broadcastPolicy();
        sendLines(source, withWarning(warning,
                success("Voice changer " + (allowed ? "allowed" : "denied")
                        + " for everyone on this server.")));
    }

    /**
     * Changing this is a policy change like any other, so every client is told
     * at once rather than finding out the next time they reconnect.
     */
    private void setRestriction(McCommandSource source, String[] arguments) {
        if (arguments.length < 2) {
            source.sendMessage(error("Usage: /voicechanger restrict <"
                    + String.join(" | ", ruleNames()) + ">"));
            return;
        }

        VoiceSourceRule rule = VoiceSourceRule.byConfigValue(arguments[1], null);
        if (rule == null) {
            source.sendMessage(error("Unknown value " + arguments[1] + ". Expected one of: "
                    + String.join(", ", ruleNames())));
            return;
        }

        McTextComponent warning = null;
        try {
            this.addon.config().setAllowedVoices(rule);
        } catch (IOException exception) {
            // The change is live either way; what failed is remembering it.
            warning = error("Applied, but could not write the config: "
                    + describe(exception) + ". It will revert on restart.");
        }

        this.addon.broadcastPolicy();
        sendLines(source, withWarning(warning,
                success("Voices allowed: " + rule.configValue() + "."),
                info(describeRule(rule))));
    }

    private static String describeRule(VoiceSourceRule rule) {
        return switch (rule) {
            case ALL -> "Players may tune their own voice freely.";
            case READY_MADE -> "Players may only pick a ready-made voice: built-in, "
                    + "contributed by another mod, or shared by this server.";
            case SERVER_ONLY -> "Players may only pick a voice this server shares.";
        };
    }

    private static List<String> ruleNames() {
        return Arrays.stream(VoiceSourceRule.values())
                .map(VoiceSourceRule::configValue)
                .toList();
    }

    private void setMuted(McCommandSource source, String[] arguments, boolean muted) {
        if (arguments.length < 2) {
            source.sendMessage(error("Usage: /voicechanger " + arguments[0] + " <player>"));
            return;
        }

        String name = arguments[1];
        McGameProfile profile = this.addon.server().getGameProfile(name);
        if (profile == null) {
            source.sendMessage(error("No player known by the name " + name + "."));
            return;
        }

        UUID playerId = profile.getId();
        McTextComponent warning = null;
        try {
            boolean changed = muted
                    ? this.addon.mutes().mute(playerId, profile.getName())
                    : this.addon.mutes().unmute(playerId);

            if (!changed) {
                source.sendMessage(info(profile.getName() + " was already "
                        + (muted ? "muted" : "not muted") + "."));
                return;
            }
        } catch (IOException exception) {
            warning = error("Applied, but could not write the mute list: "
                    + describe(exception) + ". It will revert on restart.");
        }

        this.addon.sendPolicyIfPresent(playerId);
        sendLines(source, withWarning(warning,
                success("Voice changer " + (muted ? "muted" : "unmuted")
                        + " for " + profile.getName() + ".")));
    }

    private void muteList(McCommandSource source) {
        List<String> names = this.addon.mutes().mutedNames();
        if (names.isEmpty()) {
            source.sendMessage(info("Nobody is muted."));
            return;
        }

        source.sendMessage(info("Muted (" + names.size() + "): " + String.join(", ", names)));
    }

    private void voices(McCommandSource source) {
        if (!this.addon.config().isSharePresets()) {
            source.sendMessage(info("Sharing voices is switched off in the config."));
            return;
        }

        List<SharedPreset> presets = this.addon.presetLibrary().presets();
        if (presets.isEmpty()) {
            source.sendMessage(info("No shared voices. Put preset files in "
                    + this.addon.presetLibrary().directory() + " and run /voicechanger reload."));
            return;
        }

        List<McTextComponent> lines = new ArrayList<>();
        lines.add(info("Shared voices (" + presets.size() + "):"));
        for (SharedPreset preset : presets) {
            lines.add(info("  " + preset.id() + " - " + preset.name()));
        }
        sendLines(source, lines.toArray(new McTextComponent[0]));
    }

    private void reload(McCommandSource source) {
        try {
            this.addon.reload();
            source.sendMessage(success("Reloaded. "
                    + this.addon.presetLibrary().presets().size() + " shared voice(s), "
                    + this.addon.mutes().size() + " muted."));
        } catch (IOException exception) {
            source.sendMessage(error("Reload failed: " + describe(exception)));
        }
    }

    private void usage(McCommandSource source) {
        source.sendMessage(info("/voicechanger " + String.join(" | ", SUBCOMMANDS)));
    }

    /** Puts a warning, if there is one, in front of what the command has to say. */
    private static McTextComponent[] withWarning(McTextComponent warning, McTextComponent... lines) {
        if (warning == null) {
            return lines;
        }

        McTextComponent[] combined = new McTextComponent[lines.length + 1];
        combined[0] = warning;
        System.arraycopy(lines, 0, combined, 1, lines.length);
        return combined;
    }

    /**
     * Sends several lines as one message.
     *
     * <p>RCON is the reason: a server console prints each message on its own
     * line, but the RCON reply joins whatever the command sent into a single
     * string with nothing between the parts, so multi-message output arrives at
     * a web panel run together into one unreadable blob. One message carrying
     * its own line breaks reads correctly everywhere.</p>
     */
    private static void sendLines(McCommandSource source, McTextComponent... lines) {
        McTextComponent message = lines[0];
        for (int index = 1; index < lines.length; index++) {
            message = message.append(McTextComponent.literal("\n")).append(lines[index]);
        }
        source.sendMessage(message);
    }

    // --- Helpers -------------------------------------------------------------

    private static boolean isPlayerSubcommand(String subcommand) {
        String lowered = subcommand.toLowerCase(Locale.ROOT);
        return lowered.equals("mute") || lowered.equals("unmute");
    }

    /**
     * Unmute suggests the muted names rather than the online ones, since the
     * player being unmuted is often not there to be listed.
     */
    private List<String> playerNames(String subcommand) {
        if (subcommand.equalsIgnoreCase("unmute")) {
            return this.addon.mutes().mutedNames();
        }

        List<String> names = new ArrayList<>();
        for (McServerPlayer player : this.addon.server().getPlayers()) {
            names.add(player.getName());
        }
        return names;
    }

    private static List<String> prefixed(List<String> candidates, String prefix) {
        String lowered = prefix.toLowerCase(Locale.ROOT);
        return candidates.stream()
                .filter(candidate -> candidate.toLowerCase(Locale.ROOT).startsWith(lowered))
                .toList();
    }

    private static McTextComponent info(String text) {
        return McTextComponent.literal(text).withStyle(McTextStyle.GRAY);
    }

    private static McTextComponent success(String text) {
        return McTextComponent.literal(text).withStyle(McTextStyle.GREEN);
    }

    private static McTextComponent error(String text) {
        return McTextComponent.literal(text).withStyle(McTextStyle.RED);
    }

    private static String describe(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }
}
