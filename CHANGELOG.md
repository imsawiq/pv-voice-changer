# Changelog

## 1.7

Rewrite of the audio engine. The old chain shifted pitch with a two-tap delay
line, which is where the warble, the metallic ring and the comb filtering came
from, and it had no real formant control at all, so raising a voice also made
it a chipmunk.

### Added
- **Minecraft 26.3**, on Fabric and NeoForge. Mojang replaced GLFW with SDL in
  that release, which renamed the key-type constant and changed every key code
  behind it, so the toggle hotkey now takes its code from Minecraft's own table
  instead of from GLFW — the right place for it on every version.
- **An API for other mods** (`org.sawiq.client.api`, inside the mod jar). A
  radio, a mask or a machine can now apply a voice for as long as it needs one
  and hand it back, without touching what the player chose and without having
  to save and restore their settings. Mods can also add their own voices to the
  studio and follow what the player is doing. See `docs/API.md`.
- **Server side.** The mod now installs on a server as well. A config decides
  whether the voice changer may be used there, `/voicechanger` mutes and unmutes
  individual players, and permissions go through Plasmo Voice's own permission
  layer, so LuckPerms works on both loaders. Worth being clear: the voice is
  changed on the speaker's machine before it is sent, so this is a policy an
  unmodified client obeys, not something a server can enforce.
- **Servers can limit hand tuning.** `allowed-voices` in the server config, or
  `/voicechanger restrict`, narrows players to ready-made voices — built-in,
  added by another mod, or shared by the server — or to the server's own voices
  only. Asked for by operators who wanted people picking a voice rather than
  running around with every effect at maximum. The studio's tuning controls grey
  out with the reason, and a voice another mod is holding, such as a radio, is
  not affected.
- **Servers can share voices.** Preset files dropped into the server's
  `shared-voices` folder appear in every player's studio while they are connected.
  Only numbers are read from those files and every value is clamped to its own
  range, so a server cannot use this to make a client load or run anything.
- **Batman voice** - dropped pitch and vocal tract, a sub-octave chest layer,
  pushed low mids for the rasp and a tight room instead of a hall.
- **Simple mode** - the studio now opens on a grid of voices, one strength
  control and a live microphone meter. Everything else moved behind an
  Advanced button.
- **Separate Formant control** - changes how big the speaker sounds without
  moving the note, and vice versa. This is what makes Woman sound like a woman
  rather than a sped-up recording.
- **Match My Voice** - the presets now adapt to your own speaking pitch instead
  of blindly multiplying it, so they work whoever is speaking. Previously every
  preset assumed a roughly 120 Hz male voice: a woman picking the female voice
  was pushed to about 290 Hz, well above any real speaking voice, and the male
  voice left her still sounding female. The mod now measures your own speaking
  pitch and adjusts. Radio deliberately keeps your pitch, since it is a
  transmission effect rather than a different person.
- **Growl** - sub-octave layer for chest weight, used by Batman, Titan and
  Demon.
- **Noise gate and de-esser** - keyboard, fan and room noise no longer reach
  the effect or your listeners, and raised voices no longer hiss.
- **Radio control** - the telephone band is now its own knob. It used to be
  inferred from the EQ settings, so cutting bass and treble for unrelated
  reasons silently turned you into a walkie-talkie.
- **Real reverb** - eight comb filters and four allpass stages, with Room Size
  and Reverb Tail controls, replacing the single delay tap.
- **Output limiter** - the heavy voices no longer crackle.

### Changed
- **All presets rebuilt** on the new engine, including Man, Woman, Titan, Kid,
  Demon and Radio.
- **Less reverb and hiss in the presets.** The voice goes out through a speech
  codec at a low bitrate, which spends its bits on whatever it is given; a
  reverberant or noisy signal leaves fewer for the speech itself. Measured
  against a speech-like signal, reverb costs more waveform regularity than any
  other stage, so the roomy presets were dried out.
- **Every preset now matches your natural speaking level**, so switching voice
  no longer changes how loud you are to everyone else.
- **Autotune folded into the main pitch shifter.** It used to be a second,
  independent pitch shifter whose output was crossfaded with your voice, which
  doubled the latency and combed the spectrum whenever both were in use.
- **Equaliser rebuilt** on proper shelving and peaking filters, so Bass, Mids
  and Treble are independent of one another.
- **Self listen now always uses the microphone Plasmo Voice is set to.** It
  opens its own handle on that device, or plays back the microphone filter's
  output when Plasmo Voice is capturing from it. It never falls back to the
  system default microphone, which is what made the old preview sound like a
  different device.
- **Sliders show real units** - semitones, decibels, hertz, percent.
- **The studio shows what the chain is doing** - the pitch it hears, where the
  preset is taking it, and whether microphone audio is arriving at all. A chain
  that is running but inaudible and one that never ran sound the same
  otherwise.
- Total added latency is about 21 ms, and the vocoder runs at eight-fold
  overlap to keep speech free of the roughness a lighter overlap leaves behind.

### Fixed
- **The pitch shifter now stands aside when it has nothing to do.** It ran on
  every sample regardless, and resynthesising a voice through an STFT is never
  free even at unity ratio. Presets that do not move the pitch, Radio among
  them, are now left completely untouched, and the shifter fades in and out
  against the delay-matched dry path so engaging it stays inaudible.
- **Strength no longer doubles your voice.** It scaled the wet/dry blend down
  along with everything else, so at 50% half the output was your dry voice and
  half was a copy detuned by a few percent. Two near-identical voices slightly
  apart in pitch is a chorus, which is exactly the doubling people reported.
  Strength now weakens the shift itself and leaves the blend alone; with every
  other control at neutral the processed path already is the original voice, so
  nothing is lost.
- **Presets no longer cancel themselves out.** Voice matching aimed at an
  absolute target, so a speaker whose pitch already sat on a preset's target
  heard nothing happen at all. It now adapts most of the way rather than all of
  it, which keeps a raised voice out of chipmunk territory while leaving every
  preset audible whoever is speaking.
- **Self listen no longer doubles the effect or ignores the on/off switch.**
  It read the microphone as if it were always mono. Plasmo Voice can be set to
  capture in stereo, and a stereo read returns interleaved frames, so every
  sample was effectively duplicated: the spectrum combed and the pitch dropped.
  It also processed the audio whether or not the voice changer was switched on,
  so turning the effect off left the preview sounding processed.
- **Formants no longer follow the pitch.** The spectral envelope was smoothed
  with a cepstral cut-off carried over from a lower-sample-rate example, which
  at 48 kHz could only resolve detail wider than 1600 Hz. The first two formants
  of a voice sit about 500 Hz apart, so they were merged into a single bump:
  the envelope described nothing, the Formant control did nothing measurable,
  and the formants rode along with the pitch. That was the chipmunk.
- **Blend no longer hollows out the voice.** The dry signal was mixed in
  without being delayed to match the processed one, which comb-filtered the
  result at any setting between fully dry and fully wet.
- **Distortion no longer works as a second volume control.** Its make-up gain
  was normalised against a value that is within a hair of 1, so quiet passages
  came out roughly four times louder at moderate settings.
- **Reverb no longer raises your volume** as you turn it up; it crossfades
  against the direct sound instead of being added on top.
- **No more clicks** when switching preset, changing device or dragging a
  slider. Every control is ramped and the filter state is cleared when a
  stream starts.
- **Effect no longer double-processes** the signal when autotune and pitch were
  both active.
- Saved presets from 1.6 and earlier still load; the old echo settings are
  converted to the new reverb controls.

## 1.6

### Added
- **CurseForge download button** - The update screen now offers a CurseForge download link in addition to Modrinth. Update checks still run against Modrinth.

## 1.5

### Added
- **NeoForge port** - The mod is now ported to NeoForge in addition to Fabric, so it can be used on NeoForge-based setups.

### Changed
- **License updated** - Switched the project to a new license.

### Fixed
- **Studio menu scaling** - Fixed a bug where, on small GUI scale settings, the interface did not fit on the screen.
