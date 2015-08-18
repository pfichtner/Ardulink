package com.github.pfichtner.ardulink;

import static java.lang.Boolean.TRUE;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.zu.ardulink.Link;
import org.zu.ardulink.connection.Connection;

public class MqttTest {

	private final Connection connection = createConnection();
	private final Link link = Link.createInstance("testlink", connection);
	private final MqttClient mqttClient = new MqttClient(link);

	@Before
	public void setup() {
		link.connect();
	}

	@After
	public void tearDown() {
		link.disconnect();
		Link.destroyInstance("testlink");
	}

	private static Connection createConnection() {
		Connection connection = mock(Connection.class);
		doReturn(TRUE).when(connection).isConnected();
		return connection;
	}

	@Test
	public void canPowerOnDigitalPin() {
		mqttClient.messageArrived("home/devices/ardulink/digital0/value/set",
				mqttMessage("true"));
		verify(connection).writeSerial("alp://ppsw/0/1\n");
		// verifyNoMoreInteractions(connection);
	}

	@Test
	public void canHandleInvalidTopics() {
		mqttClient.messageArrived("home/devices/ardulink/invalidTopic",
				mqttMessage("true"));
		verify(connection, never()).writeSerial(anyString());
		// verifyNoMoreInteractions(connection);
	}

	@Test
	public void canHandleInvalidBooleanPayloads() {
		mqttClient.messageArrived("home/devices/ardulink/digital0/value/set",
				mqttMessage("xxxxxxxxxxxxxxxx"));
		verify(connection).writeSerial("alp://ppsw/0/0\n");
		// verifyNoMoreInteractions(connection);
	}

	@Test
	public void canSetPowerAtAnalogPin() {
		String pin = "3";
		String value = "127";
		mqttClient.messageArrived("home/devices/ardulink/analog" + pin
				+ "/value/set", mqttMessage(value));
		verify(connection)
				.writeSerial("alp://ppin/" + pin + "/" + value + "\n");
		// verifyNoMoreInteractions(connection);
	}

	@Test
	public void canHandleInvalidDigitalPayloads() {
		String pin = "3";
		String value = "NaN";
		mqttClient.messageArrived("home/devices/ardulink/analog" + pin
				+ "/value/set", mqttMessage(value));
		// verifyNoMoreInteractions(connection);
	}

	@Test
	@Ignore
	public void doesPublishDigitalPinChanges() {
		mqttClient.publishDigitalPinOnStateChanges(0);
		// verify(connection).addDigitalReadChangeListener(null);
		verify(connection).writeSerial("");
		// verifyNoMoreInteractions(connection);
	}

	private MqttMessage mqttMessage(String message) {
		return new MqttMessage(message.getBytes());
	}

}
