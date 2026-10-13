package dev.crystalclient.core;

import com.google.gson.*;
import dev.crystalclient.CrystalClient;
import dev.crystalclient.modules.*;
import dev.crystalclient.setting.Setting;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

public final class Config {
    public static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("crystalclient");
    private static final Path PROFILES = DIR.resolve("profiles");
    private static final Path CURRENT = DIR.resolve("current.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final Set<String> friends = new HashSet<>();
    public static final String CRYSTAL = "crystal-pvp", ANCHOR = "anchor-pvp";

    private Config() {}

    public static boolean isFriend(String name) {
        for (String f : friends) if (f.equalsIgnoreCase(name)) return true;
        return false;
    }

    // ---------- snapshot / apply ----------
    public static JsonObject snapshot() {
        JsonObject root = new JsonObject(), mods = new JsonObject();
        for (Module m : ModuleManager.all()) {
            JsonObject o = new JsonObject(), s = new JsonObject();
            o.addProperty("enabled", m.isEnabled());
            for (Setting<?> st : m.settings) s.add(st.name, st.toJson());
            o.add("settings", s);
            mods.add(m.name, o);
        }
        root.addProperty("version", 1);
        root.add("modules", mods);
        JsonArray f = new JsonArray();
        friends.forEach(f::add);
        root.add("friends", f);
        return root;
    }

    public static void apply(JsonObject root) {
        JsonObject mods = root.has("modules") ? root.getAsJsonObject("modules") : new JsonObject();
        for (Module m : ModuleManager.all()) {
            if (!mods.has(m.name)) continue;
            JsonObject o = mods.getAsJsonObject(m.name);
            if (o.has("settings")) {
                JsonObject s = o.getAsJsonObject("settings");
                for (Setting<?> st : m.settings) {
                    if (!s.has(st.name)) continue;
                    try { st.fromJson(s.get(st.name)); }
                    catch (Exception e) { CrystalClient.LOG.warn("Bad value for {}.{}: {}", m.name, st.name, e.toString()); }
                }
            }
            if (o.has("enabled")) m.setEnabled(o.get("enabled").getAsBoolean());
        }
        if (root.has("friends")) { friends.clear(); root.getAsJsonArray("friends").forEach(e -> friends.add(e.getAsString())); }
    }

    // ---------- files ----------
    private static void write(Path p, JsonObject o) {
        try { Files.createDirectories(p.getParent()); Files.writeString(p, GSON.toJson(o)); }
        catch (IOException e) { CrystalClient.LOG.error("Could not write {}", p, e); }
    }

    private static boolean read(Path p) {
        if (!Files.exists(p)) return false;
        try { apply(JsonParser.parseString(Files.readString(p)).getAsJsonObject()); return true; }
        catch (Exception e) { CrystalClient.LOG.error("Could not read {} (ignored)", p, e); return false; }
    }

    public static void saveCurrent() { write(CURRENT, snapshot()); }
    public static void loadCurrent() { read(CURRENT); }
    public static void saveProfile(String name) { write(PROFILES.resolve(safe(name) + ".json"), snapshot()); }
    public static boolean loadProfile(String name) { return read(PROFILES.resolve(safe(name) + ".json")); }
    private static String safe(String n) { return n.replaceAll("[^a-zA-Z0-9_-]", "_"); }

    public static List<String> listProfiles() {
        List<String> out = new ArrayList<>();
        try (Stream<Path> s = Files.exists(PROFILES) ? Files.list(PROFILES) : Stream.empty()) {
            s.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".json"))
                    .map(n -> n.substring(0, n.length() - 5)).sorted().forEach(out::add);
        } catch (IOException ignored) {}
        return out;
    }

    // ---------- built-in presets ----------
    private static void resetAll() {
        for (Module m : ModuleManager.all()) {
            if (m instanceof CoreSettings) continue;
            for (Setting<?> s : m.settings) s.reset();
            if (!(m instanceof HudModule)) m.setEnabled(false);
        }
    }

    /** Writes crystal-pvp.json / anchor-pvp.json on first run without disturbing the live settings. */
    public static void ensurePresets() {
        if (Files.exists(PROFILES.resolve(CRYSTAL + ".json")) && Files.exists(PROFILES.resolve(ANCHOR + ".json"))) return;
        JsonObject live = snapshot();

        resetAll();
        ModuleManager.get(AutoTotem.class).setEnabled(true);
        ModuleManager.get(AutoSwap.class).setEnabled(true);
        ModuleManager.get(CrystalAssist.class).setEnabled(true);
        ModuleManager.get(TargetHud.class).setEnabled(true);
        ModuleManager.get(ModuleListHud.class).setEnabled(true);
        if (!Files.exists(PROFILES.resolve(CRYSTAL + ".json"))) saveProfile(CRYSTAL);

        resetAll();
        ModuleManager.get(AutoTotem.class).setEnabled(true);
        ModuleManager.get(AutoSwap.class).setEnabled(true);
        ModuleManager.get(AutoAnchor.class).setEnabled(true);
        ModuleManager.get(TargetHud.class).setEnabled(true);
        ModuleManager.get(ModuleListHud.class).setEnabled(true);
        if (!Files.exists(PROFILES.resolve(ANCHOR + ".json"))) saveProfile(ANCHOR);

        apply(live);
    }
}
