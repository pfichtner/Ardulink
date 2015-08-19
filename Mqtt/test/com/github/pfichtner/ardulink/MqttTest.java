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

import com.github.pfichtner.ardulink.MqttClient.LinkMessageCallback;

public class MqttTest {

	private static final String TOPIC = "home/devices/ardulink/";

	public static class Message {

		private final String topic;
		private final String message;

		public Message(String topic, Object message) {
			this(topic, String.valueOf(message));
		}

		public Message(String topic, String message) {
			this.topic = topic;
			this.message = message;
		}

		public String getTopic() {
			return topic;
		}

		public String getMessage() {
			return message;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result
					+ ((message == null) ? 0 : message.hashCode());
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
			} else if (!message.equals(other.message))
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

	private static final String LINKNAME = "testlink";

	private final List<Message> published = new ArrayList<Message>();

	private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
	private final ConnectionContact connectionContact = new ConnectionContactImpl(
			null);
	private final Connection connection = createConnection(outputStream,
			connectionContact);
	private final Link link = Link.createInstance(LINKNAME, connection);

	private final MqttClient mqttClient = new MqttClient(link,
			new LinkMessageCallback() {
				@Override
				public void publish(String topic, String message) {
					published.add(new Message(topic, message));
				}

			});

	{
		// this is an extremely high coupling of ConnectionContactImpl and Link
		// which can not be solved other than injecting the variables through
		// reflection
		set(connectionContact, getField(connectionContact, "link"), link);
		set(link, getField(link, "connectionContact"), connectionContact);

	}

	private static Field getField(Object target, String fieldName) {
		try {
			return target.getClass().getDeclaredField(fieldName);
		} catch (NoSuchFieldException e) {
			throw new IllegalStateException(e);
		} catch (SecurityException e) {
			throw new IllegalStateException(e);
		}
	}

	private static void set(Object target, Field field, Object instance) {
		field.setAccessible(true);
		try {
			field.set(target, instance);
		} catch (IllegalArgumentException e) {
			throw new IllegalStateException(e);
		} catch (IllegalAccessException e) {
			throw new IllegalStateException(e);
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
		mqttClient.messageArrived(TOPIC + "D0/value/set", mqttMessage("true"));
		assertThat(getMessage(), is("alp://ppsw/0/1\n"));
	}

	@Test
	public void canHandleInvalidTopics() {
		mqttClient.messageArrived(TOPIC + "invalidTopic", mqttMessage("true"));
		assertThat(getMessage(), is(""));
	}

	@Test
	public void canHandleInvalidBooleanPayloads() {
		mqttClient.messageArrived(TOPIC + "D0/value/set",
				mqttMessage("xxxxxxxxxxxxxxxx"));
		assertThat(getMessage(), is("alp://ppsw/0/0\n"));
	}

	@Test
	public void canSetPowerAtAnalogPin() {
		String pin = "3";
		String value = "127";
		mqttClient.messageArrived(TOPIC + "A" + pin + "/value/set",
				mqttMessage(value));
		assertThat(getMessage(), is("alp://ppin/" + pin + "/" + value + "\n"));
	}

	@Test
	public void canHandleInvalidDigitalPayloads() {
		String pin = "3";
		String value = "NaN";
		mqttClient.messageArrived(TOPIC + "A" + pin + "/value/set",
				mqttMessage(value));
	}

	@Test
	public void doesPublishDigitalPinChanges() {
		int pin = 0;
		int value = 1;
		mqttClient.publishDigitalPinOnStateChanges(pin);
		simulateArduinoWrite("alp://dred/" + pin + "/" + value);
		assertThat(published, is(singletonList(new Message(TOPIC + "D" + pin
				+ "/value/get", value))));
	}

	@Test
	public void doesNotPublishDigitalPinChangesOnUnobservedPins() {
		int pin = 0;
		int value = 1;
		mqttClient.publishDigitalPinOnStateChanges(pin);
		simulateArduinoWrite("alp://dred/" + anyOtherThan(pin) + "/" + value);
		assertThat(published, is(Collections.<Message> emptyList()));
	}

	@Test
	public void doesPublishAnalogPinChanges() {
		int pin = 9;
		int value = 123;
		mqttClient.publishAnalogPinOnStateChanges(pin);
		simulateArduinoWrite("alp://ared/" + pin + "/" + value);
		assertThat(published, is(singletonList(new Message(TOPIC + "A" + pin
				+ "/value/get", value))));
	}

	@Test
	public void doesNotPublishAnalogPinChangesOnUnobservedPins() {
		int pin = 0;
		int value = 1;
		mqttClient.publishAnalogPinOnStateChanges(pin);
		simulateArduinoWrite("alp://dred/" + anyOtherThan(pin) + "/" + value);
		assertThat(published, is(Collections.<Message> emptyList()));
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
