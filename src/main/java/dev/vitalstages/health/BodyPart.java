package dev.vitalstages.health;

public enum BodyPart {
    HEAD(1.5f, 2.2f), TORSO(1.4f, 1.5f),
    LEFT_ARM(1.0f, 1.0f), RIGHT_ARM(1.0f, 1.0f),
    LEFT_LEG(1.1f, 1.0f), RIGHT_LEG(1.1f, 1.0f);
    public final float bleedFactor, shockFactor;
    BodyPart(float bleedFactor, float shockFactor) {
        this.bleedFactor = bleedFactor; this.shockFactor = shockFactor;
    }
}
