package com.github.pfichtner.ardulink;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.zu.ardulink.Link;
import org.zu.ardulink.connection.Connection;
import org.zu.ardulink.connection.ConnectionContact;
import org.zu.ardulink.connection.serial.AbstractSerialConnection;

public class MqttTest {

	private static final String LINKNAME = "testlink";

	private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
	private final Connection connection = createConnection(outputStream);
	private final Link link = Link.createInstance(LINKNAME, connection);
	private final MqttClient mqttClient = new MqttClient(link);

	@Before
	public void setup() {
		link.connect();
	}

	@After
	public void tearDown() {
		link.disconnect();
		Link.destroyInstance(LINKNAME);
	}

	private static Connection createConnection(final OutputStream outputStream) {
		ConnectionContact connectionContact = mock(ConnectionContact.class);
		AbstractSerialConnection connection = new AbstractSerialConnection(
				connectionContact) {

			{
				setOutputStream(outputStream);
			}

			@Override
			public List<String> getPortList() {
				throw new IllegalStateException();
			}

			@Override
			public boolean disconnect() {
				setConnected(false);
				return isConnected();
			}

			@Override
			public boolean connect(Object... params) {
				setConnected(true);
				return isConnected();
			}
		};
		connection.setConnectionContact(connectionContact);
		return connection;
	}

	@Test
	public void canPowerOnDigitalPin() throws IOException {
		mqttClient.messageArrived("home/devices/ardulink/digital0/value/set",
				mqttMessage("true"));
		assertThat(getMessage(), is("alp://ppsw/0/1\n"));
	}

	@Test
	public void canHandleInvalidTopics() {
		mqttClient.messageArrived("home/devices/ardulink/invalidTopic",
				mqttMessage("true"));
		assertThat(getMessage(), is(""));
	}

	@Test
	public void canHandleInvalidBooleanPayloads() {
		mqttClient.messageArrived("home/devices/ardulink/digital0/value/set",
				mqttMessage("xxxxxxxxxxxxxxxx"));
		assertThat(getMessage(), is("alp://ppsw/0/0\n"));
	}

	@Test
	public void canSetPowerAtAnalogPin() {
		String pin = "3";
		String value = "127";
		mqttClient.messageArrived("home/devices/ardulink/analog" + pin
				+ "/value/set", mqttMessage(value));
		assertThat(getMessage(), is("alp://ppin/" + pin + "/" + value + "\n"));
	}

	@Test
	public void canHandleInvalidDigitalPayloads() {
		String pin = "3";
		String value = "NaN";
		mqttClient.messageArrived("home/devices/ardulink/analog" + pin
				+ "/value/set", mqttMessage(value));
	}

	@Test
	@Ignore
	public void doesPublishDigitalPinChanges() {
		mqttClient.publishDigitalPinOnStateChanges(0);
		// verify(connection).addDigitalReadChangeListener(null);
	}

	private MqttMessage mqttMessage(String message) {
		return new MqttMessage(message.getBytes());
	}

	private String getMessage() {
		try {
			outputStream.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return new String(outputStream.toByteArray());
	}

}
