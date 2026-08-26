package ru.sepiolsmp.finale.phase;

/**
 * Фазы финала. Порядок жёсткий: каждая следующая делает мир менее пригодным для жизни.
 */
public enum Phase {
	IDLE("idle", "Ожидание"),
	OMENS("omens", "Знамения"),
	RIFT("rift", "Разлом"),
	CONVERGENCE("convergence", "Схождение"),
	ASH("ash", "Пепел"),
	SILENCE("silence", "Тишина"),
	DONE("done", "Конец");

	private final String key;
	private final String title;

	Phase(String key, String title) {
		this.key = key;
		this.title = title;
	}

	/** Ключ фазы в config.yml (phases.<key>). */
	public String key() {
		return key;
	}

	/** Человеческое название для чата и /finale status. */
	public String title() {
		return title;
	}

	/** Идёт ли сценарий прямо сейчас. */
	public boolean running() {
		return this != IDLE && this != DONE;
	}

	/** Фаза, в которой мир уже нельзя вернуть, а смерть окончательна. */
	public boolean terminal() {
		return this == ASH || this == SILENCE || this == DONE;
	}

	public Phase next() {
		return switch (this) {
			case IDLE -> OMENS;
			case OMENS -> RIFT;
			case RIFT -> CONVERGENCE;
			case CONVERGENCE -> ASH;
			case ASH -> SILENCE;
			case SILENCE, DONE -> DONE;
		};
	}

	public static Phase fromKey(String raw) {
		if (raw == null) {
			return IDLE;
		}
		for (Phase phase : values()) {
			if (phase.key.equalsIgnoreCase(raw) || phase.name().equalsIgnoreCase(raw)) {
				return phase;
			}
		}
		return IDLE;
	}
}
