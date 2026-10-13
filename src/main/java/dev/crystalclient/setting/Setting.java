package dev.crystalclient.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import net.minecraft.client.util.InputUtil;

public abstract class Setting<T> {
    public final String name, description;
    protected T value;
    private final T def;

    protected Setting(String name, String description, T def) {
        this.name = name; this.description = description; this.value = def; this.def = def;
    }
    public T get() { return value; }
    public void set(T v) { value = v; }
    public void reset() { value = def; }
    public abstract JsonElement toJson();
    public abstract void fromJson(JsonElement e);
    public abstract String display();

    public static class Bool extends Setting<Boolean> {
        public Bool(String n, String d, boolean def) { super(n, d, def); }
        public void toggle() { value = !value; }
        @Override public JsonElement toJson() { return new JsonPrimitive(value); }
        @Override public void fromJson(JsonElement e) { value = e.getAsBoolean(); }
        @Override public String display() { return value ? "ON" : "OFF"; }
    }

    public static class Num extends Setting<Double> {
        public final double min, max, step;
        public Num(String n, String d, double def, double min, double max, double step) {
            super(n, d, def); this.min = min; this.max = max; this.step = step;
        }
        @Override public void set(Double v) {
            double c = Math.max(min, Math.min(max, v));
            if (step > 0) c = Math.round(c / step) * step;
            value = Math.round(c * 1000.0) / 1000.0;
        }
        public int i() { return (int) Math.round(value); }
        public float f() { return value.floatValue(); }
        public double d() { return value; }
        public double fraction() { return (value - min) / (max - min); }
        public void setFraction(double f) { set(min + (max - min) * Math.max(0, Math.min(1, f))); }
        @Override public JsonElement toJson() { return new JsonPrimitive(value); }
        @Override public void fromJson(JsonElement e) { set(e.getAsDouble()); }
        @Override public String display() { return step >= 1 ? String.valueOf(i()) : String.format("%.1f", value); }
    }

    public static class Choice extends Setting<String> {
        public final String[] options;
        public Choice(String n, String d, String def, String... options) { super(n, d, def); this.options = options; }
        public boolean is(String s) { return value.equalsIgnoreCase(s); }
        public int index() { for (int i = 0; i < options.length; i++) if (options[i].equals(value)) return i; return 0; }
        public void cycle(int dir) { value = options[Math.floorMod(index() + dir, options.length)]; }
        @Override public JsonElement toJson() { return new JsonPrimitive(value); }
        @Override public void fromJson(JsonElement e) {
            String s = e.getAsString();
            for (String o : options) if (o.equals(s)) { value = o; return; }
        }
        @Override public String display() { return value; }
    }

    /** GLFW key code, -1 = unbound. */
    public static class Key extends Setting<Integer> {
        public Key(String n, String d, int def) { super(n, d, def); }
        @Override public JsonElement toJson() { return new JsonPrimitive(value); }
        @Override public void fromJson(JsonElement e) { value = e.getAsInt(); }
        @Override public String display() {
            if (value < 0) return "NONE";
            try { return InputUtil.Type.KEYSYM.createFromCode(value).getLocalizedText().getString(); }
            catch (Throwable t) { return "KEY " + value; }
        }
    }
}
