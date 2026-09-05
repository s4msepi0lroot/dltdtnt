package dev.vitalstages.config;
import net.neoforged.neoforge.common.ModConfigSpec;
/** Только отображение: отключение HUD не меняет серверную физиологию/обморок. */
public final class HudConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue SHOW_HUD;
    public static final ModConfigSpec.DoubleValue SCALE;
    public static final ModConfigSpec.IntValue X, Y;
    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        SHOW_HUD = b.define("showAnatomyHud", true);
        SCALE = b.defineInRange("hudScale", 1.0, 0.5, 1.5);
        X = b.defineInRange("hudX", 8, 0, 4096);
        Y = b.defineInRange("hudY", 8, 0, 4096);
        SPEC = b.build();
    }
    private HudConfig() {}
}
