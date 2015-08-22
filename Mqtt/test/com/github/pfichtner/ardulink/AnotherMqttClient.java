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
		MqttClient mqttClient = new MqttClient("tcp://localhost:1883",
				"anotherMqttClient");
		mqttClient.connect();
		return mqttClient;
	}

	public void switchDigitalPin(int pin, Object value)
			throws MqttPersistenceException, MqttException {
		Message msg = MqttMessageBuilder.messageWithBasicTopic(topic)
				.forDigitalPin(pin).withValue(value).createSetMessage();
		mqttClient.publish(msg.getTopic(), new MqttMessage(msg.getMessage()
				.getBytes()));

	}

	public void disconnect() throws MqttException {
		this.mqttClient.disconnect();
	}

}
