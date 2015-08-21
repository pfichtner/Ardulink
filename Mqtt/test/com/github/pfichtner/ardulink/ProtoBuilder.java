package com.github.pfichtner.ardulink;

public class ProtoBuilder {

	private final String command;
	private int pin;

	public static ProtoBuilder arduinoCommand(String command) {
		return new ProtoBuilder(command);
	}

	private ProtoBuilder(String command) {
		this.command = command;
	}

	public String withValue(int value) {
		return "alp://" + command + "/" + pin + "/" + value + "\n";
	}

	public ProtoBuilder forPin(int pin) {
		this.pin = pin;
		return this;
	}

}
