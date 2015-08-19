package com.github.pfichtner.ardulink;

import java.util.regex.Pattern;

public abstract class Config {

	public static final Config DEFAULT = new Config() {

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

		@Override
		public Pattern getTopicPatternDigitalWrite() {
			return Pattern.compile(this.brokerTopic + DEFAULT_DIGITAL_WRITE);
		}

		@Override
		public String getTopicPatternDigitalRead() {
			return this.brokerTopic + DEFAULT_DIGITAL_READ;
		}

		@Override
		public Pattern getTopicPatternAnalogWrite() {
			return Pattern.compile(this.brokerTopic + DEFAULT_ANALOG_WRITE);
		}

		@Override
		public String getTopicPatternAnalogRead() {
			return this.brokerTopic + DEFAULT_ANALOG_READ;
		}
	};

	public abstract Pattern getTopicPatternDigitalWrite();

	public abstract Pattern getTopicPatternAnalogWrite();

	public abstract String getTopicPatternDigitalRead();

	public abstract String getTopicPatternAnalogRead();

}
