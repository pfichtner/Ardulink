package com.github.pfichtner.ardulink;

import static java.lang.Boolean.parseBoolean;
import static org.zu.ardulink.protocol.IProtocol.POWER_HIGH;
import static org.zu.ardulink.protocol.IProtocol.POWER_LOW;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.zu.ardulink.Link;

public class MqttClient {

	private static final String DIGITAL_PIN = "digital";
	private static final String ANALOG_PIN = "analog";

	private String brokerTopic = "home/devices/ardulink/";
	private final Link link;

	private final Pattern topicPatternDigitalWrite;
	private final Pattern topicPatternAnalogWrite;

	public MqttClient(Link link) {
		this.link = link;
		this.topicPatternAnalogWrite = Pattern.compile(this.brokerTopic
				+ ANALOG_PIN + "(\\w+)/value/set");
		this.topicPatternDigitalWrite = Pattern.compile(this.brokerTopic
				+ DIGITAL_PIN + "(\\w+)/value/set");
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

}
