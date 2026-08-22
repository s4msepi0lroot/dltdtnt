package com.s4fmer.boozecraft.drink;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;

/** Immutable description of one drink. Built by {@code Drinks}. */
public final class DrinkDef {

	private final String id;
	private final DrinkCategory category;
	private final Vessel vessel;
	private final double abv;
	private final int nutrition;
	private final double addiction;
	private final double caffeine;
	private final double soberBonus;
	private final double addictionRelief;
	private final boolean curesHangover;
	private final int color;
	private final List<EffectSpec> effects;

	private DrinkDef(Builder b) {
		this.id = b.id;
		this.category = b.category;
		this.vessel = b.vessel;
		this.abv = b.abv;
		this.nutrition = b.nutrition;
		this.addiction = b.addiction;
		this.caffeine = b.caffeine;
		this.soberBonus = b.soberBonus;
		this.addictionRelief = b.addictionRelief;
		this.curesHangover = b.curesHangover;
		this.color = b.color;
		this.effects = Collections.unmodifiableList(new ArrayList<>(b.effects));
	}

	public static Builder builder(String id) {
		return new Builder(id);
	}

	public String id() {
		return this.id;
	}

	public DrinkCategory category() {
		return this.category;
	}

	public Vessel vessel() {
		return this.vessel;
	}

	/** 0.0 - 1.0 */
	public double abv() {
		return this.abv;
	}

	public boolean alcoholic() {
		return this.abv > 0.0D;
	}

	public int nutrition() {
		return this.nutrition;
	}

	public double addiction() {
		return this.addiction;
	}

	public double caffeine() {
		return this.caffeine;
	}

	public double soberBonus() {
		return this.soberBonus;
	}

	public double addictionRelief() {
		return this.addictionRelief;
	}

	public boolean curesHangover() {
		return this.curesHangover;
	}

	public int color() {
		return this.color;
	}

	public List<EffectSpec> effects() {
		return this.effects;
	}

	public static final class Builder {
		private final String id;
		private DrinkCategory category = DrinkCategory.SPIRIT;
		private Vessel vessel = Vessel.BOTTLE;
		private double abv;
		private int nutrition;
		private double addiction;
		private double caffeine;
		private double soberBonus;
		private double addictionRelief;
		private boolean curesHangover;
		private int color = 0xFFFFFF;
		private final List<EffectSpec> effects = new ArrayList<>();

		private Builder(String id) {
			this.id = id;
		}

		public Builder cat(DrinkCategory category) {
			this.category = category;
			return this;
		}

		public Builder vessel(Vessel vessel) {
			this.vessel = vessel;
			return this;
		}

		public Builder abv(double abv) {
			this.abv = abv;
			return this;
		}

		public Builder nutrition(int nutrition) {
			this.nutrition = nutrition;
			return this;
		}

		public Builder addiction(double addiction) {
			this.addiction = addiction;
			return this;
		}

		public Builder caffeine(double caffeine) {
			this.caffeine = caffeine;
			return this;
		}

		public Builder sober(double soberBonus) {
			this.soberBonus = soberBonus;
			return this;
		}

		public Builder relief(double addictionRelief) {
			this.addictionRelief = addictionRelief;
			return this;
		}

		public Builder cure() {
			this.curesHangover = true;
			return this;
		}

		public Builder color(int color) {
			this.color = color;
			return this;
		}

		public Builder fx(Holder<MobEffect> effect, int seconds, int amplifier) {
			this.effects.add(new EffectSpec(effect, seconds, amplifier));
			return this;
		}

		public DrinkDef build() {
			return new DrinkDef(this);
		}
	}
}
