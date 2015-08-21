package com.github.pfichtner.ardulink;

public class MqttMessageBuilder {

	private final String topic;
	private int pin;
	private String type;
	private Object value;

	public static MqttMessageBuilder messageWithBasicTopic(String topic) {
		return new MqttMessageBuilder(topic);
	}

	private MqttMessageBuilder(String topic) {
		this.topic = topic;
	}

	public MqttMessageBuilder forDigitalPin(int pin) {
		return forPin("D", pin);
	}

	public MqttMessageBuilder forAnalogPin(int pin) {
		return forPin("A", pin);
	}

	private MqttMessageBuilder forPin(String type, int pin) {
		this.type = type;
		this.pin = pin;
		return this;
	}

	public MqttMessageBuilder withValue(Object value) {
		this.value = value;
		return this;
	}

	public Message createGetMessage() {
		return createMessage("get");
	}

	public Message createSetMessage() {
		return createMessage("set");
	}

	private Message createMessage(String msgType) {
		return new Message(topic + type + pin + "/value/" + msgType,
				mqttMessage(value));
	}

	private String mqttMessage(Object message) {
		return String.valueOf(message);
	}

}
