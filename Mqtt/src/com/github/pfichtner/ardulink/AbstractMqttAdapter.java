package com.github.pfichtner.ardulink;

import static java.lang.Boolean.parseBoolean;
import static java.lang.String.format;
import static org.zu.ardulink.protocol.IProtocol.POWER_HIGH;
import static org.zu.ardulink.protocol.IProtocol.POWER_LOW;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.zu.ardulink.Link;
import org.zu.ardulink.event.AnalogReadChangeEvent;
import org.zu.ardulink.event.AnalogReadChangeListener;
import org.zu.ardulink.event.DigitalReadChangeEvent;
import org.zu.ardulink.event.DigitalReadChangeListener;

public abstract class AbstractMqttAdapter {

	public interface Handler {
		boolean handle(String topic, String message);
	}

	private static class DigitalHandler implements Handler {

		private final Link link;
		private final Pattern pattern;

		public DigitalHandler(Link link, Config config) {
			this.link = link;
			this.pattern = config.getTopicPatternDigitalWrite();
		}

		@Override
		public boolean handle(String topic, String message) {
			Matcher matcher = pattern.matcher(topic);
			if (matcher.matches()) {
				Integer pin = tryParse(matcher.group(1));
				if (pin != null) {
					boolean state = parseBoolean(message);
					this.link.sendPowerPinSwitch(pin.intValue(),
							state ? POWER_HIGH : POWER_LOW);
					return true;
				}
			}
			return false;
		}

	}

	private static class AnalogHandler implements Handler {

		private final Link link;
		private final Pattern pattern;

		public AnalogHandler(Link link, Config config) {
			this.link = link;
			this.pattern = config.getTopicPatternAnalogWrite();
		}

		@Override
		public boolean handle(String topic, String message) {
			Matcher matcher = pattern.matcher(topic);
			if (matcher.matches()) {
				Integer pin = tryParse(matcher.group(1));
				Integer intensity = tryParse(message);
				if (pin != null && intensity != null) {
					this.link.sendPowerPinIntensity(pin.intValue(),
							intensity.intValue());
					return true;
				}
			}
			return false;
		}

	}

	private final Link link;

	private final Config config;

	private final Handler[] handlers;

	public AbstractMqttAdapter(Link link, Config config) {
		this.link = link;
		this.config = config;
		this.handlers = new Handler[] { new DigitalHandler(link, config),
				new AnalogHandler(link, config) };
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
	public void toArduino(String topic, String message) {
		for (Handler handler : handlers) {
			if (handler.handle(topic, message)) {
				return;
			}
		}
	}

	private static Integer tryParse(String string) {
		try {
			return Integer.valueOf(string);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	public void enableDigitalPinChangeEvents(final int pin) {
		link.addDigitalReadChangeListener(new DigitalReadChangeListener() {
			@Override
			public void stateChanged(DigitalReadChangeEvent e) {
				fromArduino(
						format(config.getTopicPatternDigitalRead(), e.getPin()),
						String.valueOf(e.getValue()));
			}

			@Override
			public int getPinListening() {
				return pin;
			}
		});
	}

	public void enableAnalogPinChangeEvents(final int pin) {
		link.addAnalogReadChangeListener(new AnalogReadChangeListener() {
			@Override
			public void stateChanged(AnalogReadChangeEvent e) {
				fromArduino(
						format(config.getTopicPatternAnalogRead(), e.getPin()),
						String.valueOf(e.getValue()));
			}

			@Override
			public int getPinListening() {
				return pin;
			}
		});
	}

	abstract void fromArduino(String topic, String message);

}
