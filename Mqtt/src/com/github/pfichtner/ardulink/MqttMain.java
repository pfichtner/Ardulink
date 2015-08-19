package com.github.pfichtner.ardulink;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static org.zu.ardulink.connection.proxy.NetworkProxyServer.DEFAULT_LISTENING_PORT;

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
import org.zu.ardulink.connection.proxy.NetworkProxyConnection;

public class MqttMain {

	@Option(name = "-brokerTopic", usage = "Topic to register. To switch outlets a message of the form $brokerTopic/$enumeratedName$NUM/value/set must be sent")
	private String brokerTopic = Config.DEFAULT_TOPIC;

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

	@Option(name = "-remote", usage = "Host and port of a remote arduino")
	private String remote;

	private static final boolean retained = true;

	private EMqttClient mqttClient;

	private class EMqttClient extends MqttClient {

		private org.eclipse.paho.client.mqttv3.MqttClient client;

		public EMqttClient(Link link, Config config) {
			super(link, new LinkMessageCallback() {
				@Override
				public void publish(String topic, MqttMessage message) {
					try {
						mqttClient.publish(topic, message);
					} catch (MqttPersistenceException e) {
						throw new RuntimeException(e);
					} catch (MqttException e) {
						throw new RuntimeException(e);
					}
				}
			}, config);
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
			client.subscribe(brokerTopic + '#');
		}

		public void publish(String topic, byte[] bytes, int i, boolean retained)
				throws MqttPersistenceException, MqttException {
			client.publish(topic, bytes, i, retained);
		}

		public void publish(String topic, MqttMessage message)
				throws MqttPersistenceException, MqttException {
			client.publish(topic, message);
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

		// ensure brokerTopic is normalized
		setBrokerTopic(this.brokerTopic);

		mqttClient = new EMqttClient(createLink(),
				Config.withTopic(this.brokerTopic));
		mqttClient.connect();
		try {
			mqttClient.subscribe();
			wait4ever();
		} finally {
			mqttClient.close();
		}

	}

	private Link createLink() {
		if (remote == null || remote.isEmpty()) {
			return Link.getDefaultInstance();
		}

		String[] hostAndPort = remote.split("\\:");
		try {
			int port = hostAndPort.length == 1 ? DEFAULT_LISTENING_PORT
					: Integer.parseInt(hostAndPort[1]);
			return Link.createInstance("network", new NetworkProxyConnection(
					hostAndPort[0], port));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public void setBrokerTopic(String brokerTopic) {
		this.brokerTopic = brokerTopic.endsWith("/") ? brokerTopic
				: brokerTopic + '/';
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