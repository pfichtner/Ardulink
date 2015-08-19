package com.github.pfichtner.ardulink;

import static java.lang.Boolean.parseBoolean;
import static java.lang.String.format;
import static org.zu.ardulink.protocol.IProtocol.POWER_HIGH;
import static org.zu.ardulink.protocol.IProtocol.POWER_LOW;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.zu.ardulink.Link;
import org.zu.ardulink.event.AnalogReadChangeEvent;
import org.zu.ardulink.event.AnalogReadChangeListener;
import org.zu.ardulink.event.DigitalReadChangeEvent;
import org.zu.ardulink.event.DigitalReadChangeListener;

public abstract class MqttClient {

	private static final String DIGITAL_PIN = "D";
	private static final String ANALOG_PIN = "A";

	public static final String DEFAULT_ANALOG_WRITE = ANALOG_PIN
			+ "(\\w+)/value/set";
	public static final String DEFAULT_DIGITAL_WRITE = DIGITAL_PIN
			+ "(\\w+)/value/set";
	public static final String DEFAULT_ANALOG_READ = ANALOG_PIN
			+ "%s/value/get";
	public static final String DEFAULT_DIGITAL_READ = DIGITAL_PIN
			+ "%s/value/get";

	private String brokerTopic = "home/devices/ardulink/";
	private final Link link;

	private final Pattern topicPatternDigitalWrite;
	private final Pattern topicPatternAnalogWrite;
	private final String topicPatternDigitalRead;
	private final String topicPatternAnalogRead;

	public MqttClient(Link link) {
		this.link = link;
		this.topicPatternAnalogWrite = Pattern.compile(this.brokerTopic
				+ DEFAULT_ANALOG_WRITE);
		this.topicPatternDigitalWrite = Pattern.compile(this.brokerTopic
				+ DEFAULT_DIGITAL_WRITE);
		this.topicPatternDigitalRead = this.brokerTopic + DEFAULT_DIGITAL_READ;
		this.topicPatternAnalogRead = this.brokerTopic + DEFAULT_ANALOG_READ;
	}

	public void messageArrived(String topic, MqttMessage message) {
		if (!handleDigital(topic, message)) {
			handleAnalog(topic, message);
		}
	}

	private boolean handleDigital(String topic, MqttMessage message) {
		Matcher matcher = this.topicPatternDigitalWrite.matcher(topic);
		if (matcher.matches()) {
			Integer pin = tryParse(matcher.group(1));
			if (pin != null) {
				boolean state = parseBoolean(new String(message.getPayload()));
				this.link.sendPowerPinSwitch(pin.intValue(), state ? POWER_HIGH
						: POWER_LOW);
				return true;
			}
		}
		return false;
	}

	private boolean handleAnalog(String topic, MqttMessage message) {
		Matcher matcher = this.topicPatternAnalogWrite.matcher(topic);
		if (matcher.matches()) {
			Integer pin = tryParse(matcher.group(1));
			Integer intensity = tryParse(new String(message.getPayload()));
			if (pin != null && intensity != null) {
				this.link.sendPowerPinIntensity(pin.intValue(),
						intensity.intValue());
				return true;
			}
		}
		return false;
	}

	private static Integer tryParse(String string) {
		try {
			return Integer.valueOf(string);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	public void publishDigitalPinOnStateChanges(final int pin) {
		link.addDigitalReadChangeListener(new DigitalReadChangeListener() {
			@Override
			public void stateChanged(DigitalReadChangeEvent e) {
				publish(format(topicPatternDigitalRead, e.getPin()),
						String.valueOf(e.getValue()));
			}

			@Override
			public int getPinListening() {
				return pin;
			}
		});
	}

	public void publishAnalogPinOnStateChanges(final int pin) {
		link.addAnalogReadChangeListener(new AnalogReadChangeListener() {
			@Override
			public void stateChanged(AnalogReadChangeEvent e) {
				publish(format(topicPatternAnalogRead, e.getPin()),
						String.valueOf(e.getValue()));
			}

			@Override
			public int getPinListening() {
				return pin;
			}
		});
	}

	protected abstract void publish(String topic, String message);

}
