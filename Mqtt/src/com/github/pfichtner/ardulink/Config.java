package com.github.pfichtner.ardulink;

import java.util.regex.Pattern;

public abstract class Config {

	public static final Config DEFAULT = new Config() {

		private Pattern topicPatternDigitalWrite;
		private String topicPatternDigitalRead;
		private Pattern topicPatternAnalogWrite;
		private String topicPatternAnalogRead;

		{
			setTopic("home/devices/ardulink/");
		}

		private void setTopic(String brokerTopic) {
			this.topicPatternDigitalWrite = Pattern.compile(write(brokerTopic,
					"D"));
			this.topicPatternDigitalRead = read(brokerTopic, "D");
			this.topicPatternAnalogWrite = Pattern.compile(write(brokerTopic,
					"A"));
			this.topicPatternAnalogRead = read(brokerTopic, "A");
		}

		private String read(String brokerTopic, String prefix) {
			return format(brokerTopic, prefix, "%s", "/get");
		}

		private String write(String brokerTopic, String prefix) {
			return format(brokerTopic, prefix, "(\\w+)", "/set");
		}

		private String format(String brokerTopic, String prefix,
				String numerated, String appendix) {
			return brokerTopic + prefix + String.format("%s/value", numerated)
					+ appendix;
		}

		@Override
		public Pattern getTopicPatternDigitalWrite() {
			return topicPatternDigitalWrite;
		}

		@Override
		public String getTopicPatternDigitalRead() {
			return topicPatternDigitalRead;
		}

		@Override
		public Pattern getTopicPatternAnalogWrite() {
			return topicPatternAnalogWrite;
		}

		@Override
		public String getTopicPatternAnalogRead() {
			return topicPatternAnalogRead;
		}
	};

	public abstract Pattern getTopicPatternDigitalWrite();

	public abstract Pattern getTopicPatternAnalogWrite();

	public abstract String getTopicPatternDigitalRead();

	public abstract String getTopicPatternAnalogRead();

}
