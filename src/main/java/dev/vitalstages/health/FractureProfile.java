package dev.vitalstages.health;

/** Чистая математика штрафов: не трогаем base value атрибутов и чужие модификаторы. */
public record FractureProfile(double movement, double attackSpeed, double miningSpeed,
                              int untreatedLegs, int untreatedArms) {
    public static FractureProfile calculate(int fractures, int splinted, boolean enabled,
                                            double legFactor, double attackFactor, double miningFactor) {
        int untreated = enabled ? fractures & ~splinted & 0x3F : 0;
        int legs = Integer.bitCount(untreated & (BodyPart.LEFT_LEG.bit() | BodyPart.RIGHT_LEG.bit()));
        int arms = Integer.bitCount(untreated & (BodyPart.LEFT_ARM.bit() | BodyPart.RIGHT_ARM.bit()));
        return new FractureProfile(Math.pow(valid(legFactor), legs), Math.pow(valid(attackFactor), arms),
                Math.pow(valid(miningFactor), arms), legs, arms);
    }
    private static double valid(double value) {
        return Double.isFinite(value) ? Math.max(0.05, Math.min(1, value)) : 1;
    }
}
