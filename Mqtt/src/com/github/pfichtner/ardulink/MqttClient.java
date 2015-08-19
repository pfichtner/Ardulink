package com.github.pfichtner.ardulink;

import static java.lang.Boolean.parseBoolean;
import static java.lang.String.format;
import static org.zu.ardulink.protocol.IProtocol.POWER_HIGH;
import static org.zu.ardulink.protocol.IProtocol.POWER_LOW;

import java.util.regex.Matcher;

import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.zu.ardulink.Link;
import org.zu.ardulink.event.AnalogReadChangeEvent;
import org.zu.ardulink.event.AnalogReadChangeListener;
import org.zu.ardulink.event.DigitalReadChangeEvent;
import org.zu.ardulink.event.DigitalReadChangeListener;

public class MqttClient {

	public interface LinkMessageCallback {
		/**
		 * This method is called by Ardulink-Mqtt when an observed analog or
		 * digital pin changed its state.
		 * 
		 * @param topic
		 *            the topic to send to
		 * @param message
		 *            the payload to send
		 */
		void publish(String topic, MqttMessage message);
	}

	private final Link link;
	private final LinkMessageCallback linkMessageCallback;

	private final Config config;

	public MqttClient(Link link, LinkMessageCallback linkMessageCallback,
			Config config) {
		this.link = link;
		this.linkMessageCallback = linkMessageCallback;
		this.config = config;
	}

	/**
	 * This method should be called by the publisher when a new message has
	 * arrived.
	 * 
	 * @param topic
	 *            the message's topic
	 * @param message
	 *            the payload
	 */
	public void messageArrived(String topic, MqttMessage message) {
		if (!handleDigital(topic, message)) {
			handleAnalog(topic, message);
		}
	}

	private boolean handleDigital(String topic, MqttMessage message) {
		Matcher matcher = this.config.getTopicPatternDigitalWrite().matcher(
				topic);
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
		Matcher matcher = this.config.getTopicPatternAnalogWrite().matcher(
				topic);
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
				linkMessageCallback.publish(
						format(config.getTopicPatternDigitalRead(), e.getPin()),
						new MqttMessage(String.valueOf(e.getValue()).getBytes()));
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
				linkMessageCallback.publish(
						format(config.getTopicPatternAnalogRead(), e.getPin()),
						new MqttMessage(String.valueOf(e.getValue()).getBytes()));
			}

			@Override
			public int getPinListening() {
				return pin;
			}
		});
	}

}