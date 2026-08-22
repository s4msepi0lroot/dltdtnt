package com.s4fmer.boozecraft.block;

/** The three processing machines. */
public enum ProcessorType {
	FERMENTER("fermenter"),
	STILL("still"),
	AGING("aging_barrel");

	private final String id;

	ProcessorType(String id) {
		this.id = id;
	}

	public String id() {
		return this.id;
	}

	public String translationKey() {
		return "msg.boozecraft.machine." + this.id;
	}
}
