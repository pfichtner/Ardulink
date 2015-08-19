package com.github.pfichtner.ardulink;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttPersistenceException;
import org.eclipse.paho.client.mqttv3.MqttSecurityException;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.zu.ardulink.Link;

public class MqttMain {

	@Option(name = "-brokerHost", usage = "Hostname of the broker to connect to")
	private String brokerHost = "localhost";

	@Option(name = "-brokerPort", usage = "Port of the broker to connect to")
	private int brokerPort = 1883;

	@Option(name = "-clientId", usage = "This client's name")
	private String clientId = "ardulink";

	@Option(name = "-publishClientInfo", usage = "When set, publish messages on connect/disconnect under this topic")
	private String publishClientInfoTopic;

	@Option(name = "-d", aliases = "--digital", usage = "Digital pins to listen to")
	private int[] digitals = new int[] { 2 };

	@Option(name = "-a", aliases = "--analog", usage = "Analog pins to listen to")
	private int[] analogs = new int[0];

	private static final boolean retained = true;

	private EMqttClient mqttClient;

	private class EMqttClient extends MqttClient {

		private org.eclipse.paho.client.mqttv3.MqttClient client;

		public EMqttClient(Link link) {
			super(link, new LinkMessageCallback() {
				@Override
				public void publish(String topic, String message) {
					// TODO implement
					throw new UnsupportedOperationException(
							"not yet implemented");
				}
			});
			for (int analogPin : analogs) {
				publishAnalogPinOnStateChanges(analogPin);
			}
			for (int digitalPin : digitals) {
				publishDigitalPinOnStateChanges(digitalPin);
			}
			this.client.setCallback(new MqttCallback() {
				public void connectionLost(Throwable cause) {
					do {
						try {
							TimeUnit.SECONDS.sleep(1);
						} catch (InterruptedException e1) {
							Thread.currentThread().interrupt();
						}
						try {
							connect();
							subscribe();
						} catch (Exception e) {
							e.printStackTrace();
						}
					} while (!EMqttClient.this.client.isConnected());
				}

				public void messageArrived(String topic, MqttMessage message)
						throws IOException {
					EMqttClient.this.messageArrived(topic, message);
				}

				public void deliveryComplete(IMqttDeliveryToken token) {
					// nothing to do
				}

			});
		}

		private void connect() throws MqttSecurityException, MqttException {
			this.client.connect(mqttConnectOptions());
			publishClientStatus(TRUE);
		}

		public void subscribe() throws MqttException {
			// TODO topic
			client.subscribe("FIXME" + '#');
		}

		public void publish(String topic, byte[] bytes, int i, boolean retained)
				throws MqttPersistenceException, MqttException {
			client.publish(topic, bytes, i, retained);
		}

		public void close() throws MqttException {
			client.disconnect();
			client.close();
		}

	}

	public static void main(String[] args) throws MqttSecurityException,
			MqttException, InterruptedException {
		new MqttMain().doMain(args);
	}

	public void doMain(String... args) throws MqttSecurityException,
			MqttException, InterruptedException {
		CmdLineParser cmdLineParser = new CmdLineParser(this);
		try {
			cmdLineParser.parseArgument(args);
		} catch (CmdLineException e) {
			System.err.println(e.getMessage());
			cmdLineParser.printUsage(System.err);
			return;
		}

		mqttClient = new EMqttClient(Link.getDefaultInstance());

		// ensure brokerTopic is normalized
		// TODO make configurable
		// mqttClient.setBrokerTopic(this.brokerTopic);
		mqttClient.connect();
		try {
			mqttClient.subscribe();
			wait4ever();
		} finally {
			mqttClient.close();
		}

	}

	private static void wait4ever() throws InterruptedException {
		Object blocker = new Object();
		synchronized (blocker) {
			blocker.wait();
		}
	}

	private MqttConnectOptions mqttConnectOptions() {
		MqttConnectOptions options = new MqttConnectOptions();
		String topic = this.publishClientInfoTopic;
		if (publishClientStatus()) {
			options.setWill(topic, FALSE.toString().getBytes(), 0, retained);
		}
		return options;
	}

	private void publishClientStatus(Boolean state) throws MqttException,
			MqttPersistenceException {
		if (publishClientStatus()) {
			this.mqttClient.publish(this.publishClientInfoTopic, state
					.toString().getBytes(), 0, retained);
		}
	}

	private boolean publishClientStatus() {
		return this.publishClientInfoTopic != null
				&& !this.publishClientInfoTopic.isEmpty();
	}

}
