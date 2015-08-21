package com.github.pfichtner.ardulink;

import static com.github.pfichtner.ardulink.util.MqttMessageBuilder.messageWithBasicTopic;
import static com.github.pfichtner.ardulink.util.ProtoBuilder.arduinoCommand;
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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.zu.ardulink.ConnectionContactImpl;
import org.zu.ardulink.Link;
import org.zu.ardulink.connection.Connection;
import org.zu.ardulink.connection.ConnectionContact;
import org.zu.ardulink.connection.serial.AbstractSerialConnection;

import com.github.pfichtner.ardulink.MqttClient.LinkMessageCallback;
import com.github.pfichtner.ardulink.util.Message;

public class MqttTest {

	private static final String TOPIC = "home/devices/ardulink/";

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
				public void fromArduino(String topic, String message) {
					published.add(new Message(topic, message));
				}

			}, Config.DEFAULT);

	{
		// there is an extremely high coupling of ConnectionContactImpl and Link
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
	public void canPowerOnDigitalPin() {
		int pin = 0;
		simulateMqttToArduino(messageWithBasicTopic(TOPIC).forDigitalPin(pin)
				.withValue(true).createSetMessage());
		assertThat(serialReceived(), is(arduinoCommand("ppsw").forPin(pin)
				.withValue(1)));
	}

	@Test
	public void canHandleInvalidTopics() {
		mqttClient.toArduino(TOPIC + "invalidTopic", String.valueOf("true"));
		assertThat(serialReceived(), is(""));
	}

	@Test
	public void canHandleInvalidBooleanPayloads() {
		int pin = 3;
		simulateMqttToArduino(messageWithBasicTopic(TOPIC).forDigitalPin(pin)
				.withValue("xxxxxxxxINVALIDxxxxxxxx").createSetMessage());
		assertThat(serialReceived(), is(arduinoCommand("ppsw").forPin(pin)
				.withValue(0)));
	}

	@Test
	public void canSetPowerAtAnalogPin() {
		int pin = 3;
		int value = 127;
		simulateMqttToArduino(messageWithBasicTopic(TOPIC).forAnalogPin(pin)
				.withValue(value).createSetMessage());
		assertThat(serialReceived(), is(arduinoCommand("ppin").forPin(pin)
				.withValue(value)));
	}

	@Test
	public void canHandleInvalidDigitalPayloads() {
		int pin = 3;
		String value = "NaN";
		simulateMqttToArduino(messageWithBasicTopic(TOPIC).forAnalogPin(pin)
				.withValue(value).createSetMessage());
		assertThat(serialReceived(), is(""));
	}

	@Test
	public void doesPublishDigitalPinChanges() {
		int pin = 0;
		int value = 1;
		mqttClient.enableDigitalPinChangeEvents(pin);
		simulateArduinoToMqtt(arduinoCommand("dred").forPin(pin).withValue(
				value));
		assertThat(published, is(singletonList(messageWithBasicTopic(TOPIC)
				.forDigitalPin(pin).withValue((Object) value)
				.createGetMessage())));
	}

	@Test
	public void doesNotPublishDigitalPinChangesOnUnobservedPins() {
		int pin = 0;
		int value = 1;
		mqttClient.enableDigitalPinChangeEvents(pin);
		simulateArduinoToMqtt(arduinoCommand("dred").forPin(anyOtherThan(pin))
				.withValue(value));
		assertThat(published, is(Collections.<Message> emptyList()));
	}

	@Test
	public void doesPublishAnalogPinChanges() {
		int pin = 9;
		int value = 123;
		mqttClient.enableAnalogPinChangeEvents(pin);
		simulateArduinoToMqtt(arduinoCommand("ared").forPin(pin).withValue(
				value));
		assertThat(published,
				is(singletonList(messageWithBasicTopic(TOPIC).forAnalogPin(pin)
						.withValue((Object) value).createGetMessage())));
	}

	@Test
	public void doesNotPublishAnalogPinChangesOnUnobservedPins() {
		int pin = 0;
		int value = 1;
		mqttClient.enableAnalogPinChangeEvents(pin);
		simulateArduinoToMqtt(arduinoCommand("ared").forPin(anyOtherThan(pin))
				.withValue(value));
		assertThat(published, is(Collections.<Message> emptyList()));
	}

	private int anyOtherThan(int pin) {
		return ++pin;
	}

	private void simulateArduinoToMqtt(String message) {
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

	private void simulateMqttToArduino(Message message) {
		mqttClient.toArduino(message.getTopic(), message.getMessage());
	}

	private String serialReceived() {
		try {
			outputStream.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return new String(outputStream.toByteArray());
	}

}
