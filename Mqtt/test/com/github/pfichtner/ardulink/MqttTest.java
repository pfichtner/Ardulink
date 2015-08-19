package com.github.pfichtner.ardulink;

import static java.util.Collections.singletonList;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.zu.ardulink.ConnectionContactImpl;
import org.zu.ardulink.Link;
import org.zu.ardulink.connection.Connection;
import org.zu.ardulink.connection.ConnectionContact;
import org.zu.ardulink.connection.serial.AbstractSerialConnection;

public class MqttTest {

	private static final String LINKNAME = "testlink";

	private final List<String> published = new ArrayList<String>();

	private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
	private final ConnectionContact connectionContact = new ConnectionContactImpl(
			null);
	private final Connection connection = createConnection(outputStream,
			connectionContact);
	private final Link link = Link.createInstance(LINKNAME, connection);
	private final MqttClient mqttClient = new MqttClient(link) {
		protected void publish(String message) {
			published.add(message);
		};
	};
	{
		try {
			Field field = connectionContact.getClass().getDeclaredField("link");
			field.setAccessible(true);
			field.set(connectionContact, link);
		} catch (NoSuchFieldException e) {
			throw new RuntimeException(e);
		} catch (SecurityException e) {
			throw new RuntimeException(e);
		} catch (IllegalArgumentException e) {
			throw new RuntimeException(e);
		} catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}
	{
		try {
			Field field = link.getClass().getDeclaredField("connectionContact");
			field.setAccessible(true);
			field.set(link, connectionContact);
		} catch (NoSuchFieldException e) {
			throw new RuntimeException(e);
		} catch (SecurityException e) {
			throw new RuntimeException(e);
		} catch (IllegalArgumentException e) {
			throw new RuntimeException(e);
		} catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}

	@Before
	public void setup() {
		link.connect();
	}

	@After
	public void tearDown() {
		link.disconnect();
		Link.destroyInstance(LINKNAME);
	}

	private static Connection createConnection(final OutputStream outputStream,
			ConnectionContact connectionContact) {
		return new AbstractSerialConnection(connectionContact) {

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
	public void doesPublishDigitalPinChanges() {
		int pin = 0;
		int value = 1;
		mqttClient.publishDigitalPinOnStateChanges(pin);
		simulateArduinoWrite("alp://dred/" + pin + "/" + value);
		assertThat(published, is(singletonList("D" + pin + "=" + value)));
	}

	@Test
	public void doesNotPublishDigitalPinChangesOnUnobservedPins() {
		int pin = 0;
		int value = 1;
		mqttClient.publishDigitalPinOnStateChanges(pin);
		simulateArduinoWrite("alp://dred/" + anyOtherThan(pin) + "/" + value);
		assertThat(published, is(Collections.<String> emptyList()));
	}

	@Test
	public void doesPublishAnalogPinChanges() {
		int pin = 9;
		int value = 123;
		mqttClient.publishAnalogPinOnStateChanges(pin);
		simulateArduinoWrite("alp://ared/" + pin + "/" + value);
		assertThat(published, is(singletonList("A" + pin + "=" + value)));
	}

	@Test
	public void doesNotPublishAnalogPinChangesOnUnobservedPins() {
		int pin = 0;
		int value = 1;
		mqttClient.publishAnalogPinOnStateChanges(pin);
		simulateArduinoWrite("alp://dred/" + anyOtherThan(pin) + "/" + value);
		assertThat(published, is(Collections.<String> emptyList()));
	}

	private int anyOtherThan(int pin) {
		return ++pin;
	}

	private void simulateArduinoWrite(String message) {
		int[] codepoints = toCodepoints(message);
		connectionContact.parseInput(anyId(), codepoints.length, codepoints);
	}

	private String anyId() {
		return "randomId";
	}

	private int[] toCodepoints(String message) {
		int[] codepoints = new int[message.length()];
		for (int i = 0; i < message.length(); i++) {
			codepoints[i] = message.codePointAt(i);
		}
		return codepoints;
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
