package com.s4fmer.boozecraft.booze;

import com.google.gson.JsonObject;

/** Per player state. Persisted as JSON in the world folder. */
public class PlayerBoozeData {

	/** abstract 0..cap scale */
	public double alcohol;
	/** highest alcohol level of the current session, used for hangovers */
	public double peak;
	/** 0..100 */
	public double addiction;
	/** 0..200 */
	public double caffeine;

	public long lastAlcoholTime;
	public long lastDrinkTime;
	public long hangoverUntil;
	public long passedOutUntil;
	public long lastWithdrawalMessage;

	public int drinksTotal;
	public int drinksAlcohol;
	public int passOuts;

	/** not persisted */
	public transient boolean wasAddicted;

	public JsonObject toJson() {
		JsonObject o = new JsonObject();
		o.addProperty("alcohol", this.alcohol);
		o.addProperty("peak", this.peak);
		o.addProperty("addiction", this.addiction);
		o.addProperty("caffeine", this.caffeine);
		o.addProperty("lastAlcoholTime", this.lastAlcoholTime);
		o.addProperty("lastDrinkTime", this.lastDrinkTime);
		o.addProperty("hangoverUntil", this.hangoverUntil);
		o.addProperty("passedOutUntil", this.passedOutUntil);
		o.addProperty("drinksTotal", this.drinksTotal);
		o.addProperty("drinksAlcohol", this.drinksAlcohol);
		o.addProperty("passOuts", this.passOuts);
		return o;
	}

	public static PlayerBoozeData fromJson(JsonObject o) {
		PlayerBoozeData d = new PlayerBoozeData();
		d.alcohol = getDouble(o, "alcohol");
		d.peak = getDouble(o, "peak");
		d.addiction = getDouble(o, "addiction");
		d.caffeine = getDouble(o, "caffeine");
		d.lastAlcoholTime = getLong(o, "lastAlcoholTime");
		d.lastDrinkTime = getLong(o, "lastDrinkTime");
		d.hangoverUntil = getLong(o, "hangoverUntil");
		d.passedOutUntil = getLong(o, "passedOutUntil");
		d.drinksTotal = (int) getLong(o, "drinksTotal");
		d.drinksAlcohol = (int) getLong(o, "drinksAlcohol");
		d.passOuts = (int) getLong(o, "passOuts");
		return d;
	}

	private static double getDouble(JsonObject o, String key) {
		return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsDouble() : 0.0D;
	}

	private static long getLong(JsonObject o, String key) {
		return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsLong() : 0L;
	}
}
