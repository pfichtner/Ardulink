package com.github.pfichtner.ardulink;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttPersistenceException;
import org.eclipse.paho.client.mqttv3.MqttSecurityException;

import com.github.pfichtner.ardulink.util.Message;
import com.github.pfichtner.ardulink.util.MqttMessageBuilder;

public class AnotherMqttClient {

	private final String topic;
	private final MqttClient mqttClient;

	public AnotherMqttClient(String topic) throws MqttSecurityException,
			MqttException {
		this.topic = topic;
		mqttClient = mqttClient();
	}

	private static MqttClient mqttClient() throws MqttException,
			MqttSecurityException {
		return new MqttClient("tcp://localhost:1883", "anotherMqttClient");
	}

	public AnotherMqttClient connect() throws MqttSecurityException,
			MqttException {
		mqttClient.connect();
		return this;
	}

	public void switchDigitalPin(int pin, Object value)
			throws MqttPersistenceException, MqttException {
		sendMessage(createSetMessage(newMsgBuilder().forDigitalPin(pin), value));
	}

	public void switchAnalogPin(int pin, Object value)
			throws MqttPersistenceException, MqttException {
		sendMessage(createSetMessage(newMsgBuilder().forAnalogPin(pin), value));
	}

	private Message createSetMessage(MqttMessageBuilder msgBuilder, Object value) {
		return msgBuilder.withValue(value).createSetMessage();
	}

	private MqttMessageBuilder newMsgBuilder() {
		return MqttMessageBuilder.messageWithBasicTopic(topic);
	}

	private void sendMessage(Message msg) throws MqttException,
			MqttPersistenceException {
		mqttClient.publish(msg.getTopic(), new MqttMessage(msg.getMessage()
				.getBytes()));
	}

	public void disconnect() throws MqttException {
		this.mqttClient.disconnect();
	}

}
