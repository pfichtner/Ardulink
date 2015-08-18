package com.github.pfichtner.ardulink;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.zu.ardulink.protocol.IProtocol.POWER_HIGH;
import static org.zu.ardulink.protocol.IProtocol.POWER_LOW;

import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.Test;
import org.zu.ardulink.Link;

public class MqttTest {

	private final Link link = mock(Link.class);
	private final MqttClient mqttClient = new MqttClient(link);

	@Test
	public void canPowerOnDigitalPin() {
		mqttClient.messageArrived("home/devices/ardulink/digital0/value/set",
				mqttMessage("true"));
		verify(link).sendPowerPinSwitch(0, POWER_HIGH);
		verifyNoMoreInteractions(link);
	}

	@Test
	public void canHandleInvalidTopics() {
		mqttClient.messageArrived("home/devices/ardulink/invalidTopic",
				mqttMessage("true"));
		verifyNoMoreInteractions(link);
	}

	@Test
	public void canHandleInvalidBooleanPayloads() {
		mqttClient.messageArrived("home/devices/ardulink/digital0/value/set",
				mqttMessage("xxxxxxxxxxxxxxxx"));
		verify(link).sendPowerPinSwitch(0, POWER_LOW);
		verifyNoMoreInteractions(link);
	}

	@Test
	public void canSetPowerAtAnalogPin() {
		mqttClient.messageArrived("home/devices/ardulink/analog3/value/set",
				mqttMessage("127"));
		verify(link).sendPowerPinIntensity(3, 127);
		verifyNoMoreInteractions(link);
	}

	@Test
	public void canHandleInvalidDigitalPayloads() {
		mqttClient.messageArrived("home/devices/ardulink/analog3/value/set",
				mqttMessage("NaN"));
		verifyNoMoreInteractions(link);
	}

	private MqttMessage mqttMessage(String message) {
		return new MqttMessage(message.getBytes());
	}

}
