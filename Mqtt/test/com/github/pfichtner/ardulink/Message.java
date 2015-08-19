package com.github.pfichtner.ardulink;

import java.util.Arrays;

import org.eclipse.paho.client.mqttv3.MqttMessage;

public class Message {

	private final String topic;
	private final MqttMessage message;

	public Message(String topic, MqttMessage message) {
		this.topic = topic;
		this.message = message;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((message == null) ? 0 : Arrays.hashCode(message.getPayload()));
		result = prime * result + ((topic == null) ? 0 : topic.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Message other = (Message) obj;
		if (message == null) {
			if (other.message != null)
				return false;
		} else if (!Arrays.equals(message.getPayload(),
				other.message.getPayload()))
			return false;
		if (topic == null) {
			if (other.topic != null)
				return false;
		} else if (!topic.equals(other.topic))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "Message [topic=" + topic + ", message=" + message + "]";
	}

}