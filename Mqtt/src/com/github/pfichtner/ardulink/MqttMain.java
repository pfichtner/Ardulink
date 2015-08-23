package com.github.pfichtner.ardulink;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static org.zu.ardulink.connection.proxy.NetworkProxyServer.DEFAULT_LISTENING_PORT;

import java.io.IOException;
import java.util.List;
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
	private int[] digitals = new int[0];

	@Option(name = "-a", aliases = "--analog", usage = "Analog pins to listen to")
	private int[] analogs = new int[0];

	@Option(name = "-remote", usage = "Host and port of a remote arduino")
	private String remote;

	private MqttClient mqttClient;

	private Link link;

	private class MqttClient extends AbstractMqttAdapter {

		private static final boolean RETAINED = true;

		private org.eclipse.paho.client.mqttv3.MqttClient client;

		private MqttClient(Link link, Config config)
				throws MqttSecurityException, MqttException {
			super(link, config);
			for (int analogPin : analogs) {
				enableAnalogPinChangeEvents(analogPin);
			}
			for (int digitalPin : digitals) {
				enableDigitalPinChangeEvents(digitalPin);
			}
			this.client = newClient(brokerHost, brokerPort, clientId);
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
					} while (!MqttClient.this.client.isConnected());
				}

				public void messageArrived(String topic, MqttMessage message)
						throws IOException {
					MqttClient.this.toArduino(topic,
							new String(message.getPayload()));
				}

				public void deliveryComplete(IMqttDeliveryToken token) {
					// nothing to do
				}
			});
			connect();
			subscribe();
		}

		@Override
		public void fromArduino(String topic, String message) {
			try {
				publish(topic, message);
			} catch (MqttPersistenceException e) {
				throw new RuntimeException(e);
			} catch (MqttException e) {
				throw new RuntimeException(e);
			}
		}

		private void connect() throws MqttSecurityException, MqttException {
			this.client.connect(mqttConnectOptions());
			publishClientStatus(TRUE);
		}

		public void subscribe() throws MqttException {
			client.subscribe(brokerTopic + '#');
		}

		public void close() throws MqttException {
			client.disconnect();
			client.close();
		}

		private MqttConnectOptions mqttConnectOptions() {
			MqttConnectOptions options = new MqttConnectOptions();
			String clientInfoTopic = publishClientInfoTopic;
			if (!nullOrEmpty(clientInfoTopic)) {
				options.setWill(clientInfoTopic, FALSE.toString().getBytes(),
						0, RETAINED);
			}
			return options;
		}

		private void publish(String topic, String message)
				throws MqttException, MqttPersistenceException {
			client.publish(topic, new MqttMessage(message.getBytes()));
		}

		private void publishClientStatus(Boolean state) throws MqttException,
				MqttPersistenceException {
			if (!nullOrEmpty(publishClientInfoTopic)) {
				client.publish(publishClientInfoTopic, state.toString()
						.getBytes(), 0, RETAINED);
			}
		}

		public boolean isConnected() {
			return client.isConnected();
		}

		private boolean nullOrEmpty(String string) {
			return string == null || string.isEmpty();
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

		createClient();
		try {
			wait4ever();
		} finally {
			close();
		}

	}

	public void close() throws MqttException {
		link.disconnect();
		mqttClient.close();
	}

	protected void createClient() throws MqttSecurityException, MqttException,
			InterruptedException {
		link = connect(createLink());
		mqttClient = new MqttClient(link, Config.withTopic(this.brokerTopic));
	}

	private Link connect(Link link) throws InterruptedException {
		List<String> portList = link.getPortList();
		if (portList == null || portList.isEmpty()) {
			throw new RuntimeException("No port found!");
		}
		if (!link.connect(portList.get(0), 115200)) {
			throw new RuntimeException("Connection failed!");
		}
		TimeUnit.SECONDS.sleep(1);
		return link;
	}

	private org.eclipse.paho.client.mqttv3.MqttClient newClient(String host,
			int port, String clientId) throws MqttException,
			MqttSecurityException {
		return new org.eclipse.paho.client.mqttv3.MqttClient("tcp://" + host
				+ ":" + port, clientId);
	}

	protected Link createLink() {
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

	public void setAnalogs(int... analogs) {
		this.analogs = analogs == null ? new int[0] : analogs.clone();
	}

	public void setDigitals(int... digitals) {
		this.digitals = digitals == null ? new int[0] : digitals.clone();
	}

	public boolean isConnected() {
		return mqttClient != null && mqttClient.isConnected();
	}

	private static void wait4ever() throws InterruptedException {
		Object blocker = new Object();
		synchronized (blocker) {
			blocker.wait();
		}
	}

}
