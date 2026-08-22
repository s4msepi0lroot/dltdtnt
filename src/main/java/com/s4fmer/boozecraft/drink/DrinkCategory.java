package com.s4fmer.boozecraft.drink;

public enum DrinkCategory {
	SPIRIT,
	BEER,
	WINE,
	COCKTAIL,
	MASH,
	SODA,
	ENERGY,
	COFFEE,
	JUICE,
	MEDICINE;

	public boolean isMixable() {
		return this != MASH;
	}
}
