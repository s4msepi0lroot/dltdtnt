package dev.vitalstages.health;
import java.util.List;
/** Read-only capability. Мутации выполняет серверный слой, а не потребитель API. */
public interface HealthView {
    float health();
    float bloodLevel();
    float bleedingRate();
    float consciousness();
    float pain();
    float bodyTemperature();
    boolean unconscious();
    int unconsciousTicks();
    List<Wound> wounds();
    LimbStatus limbStatus(BodyPart part);
}
