package com.github.pfichtner.ardulink;

import static com.github.pfichtner.ardulink.util.ProtoBuilder.arduinoCommand;
import static com.github.pfichtner.ardulink.util.TestUtil.createConnection;
import static com.github.pfichtner.ardulink.util.TestUtil.getField;
import static com.github.pfichtner.ardulink.util.TestUtil.set;
import static com.github.pfichtner.ardulink.util.TestUtil.toCodepoints;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.dna.mqtt.moquette.server.Server;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttSecurityException;
import org.junit.After;
import org.junit.Test;
import org.zu.ardulink.ConnectionContactImpl;
import org.zu.ardulink.Link;
import org.zu.ardulink.connection.Connection;
import org.zu.ardulink.connection.ConnectionContact;

import com.github.pfichtner.ardulink.util.AnotherMqttClient;
import com.github.pfichtner.ardulink.util.MqttMessageBuilder;
import com.github.pfichtner.ardulink.util.StopWatch;

public class MqttClientIntegrationSend {

	private static final long TIMEOUT = 10 * 1000;;

	private static final String LINKNAME = "testlink";

	private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

	private final ConnectionContact connectionContact = new ConnectionContactImpl(
			null);
	private final Connection connection = createConnection(outputStream,
			connectionContact);
	private final Link link = Link.createInstance(LINKNAME, connection);

	{
		// there is an extremely high coupling of ConnectionContactImpl and Link
		// which can not be solved other than injecting the variables through
		// reflection
		set(connectionContact, getField(connectionContact, "link"), link);
		set(link, getField(link, "connectionContact"), connectionContact);

	}

	@After
	public void tearDown() throws InterruptedException, MqttException {
		Link.destroyInstance(LINKNAME);
	}

	private MqttMain client = new MqttMain() {
		@Override
		protected Link createLink() {
			return link;
		}
	};

	private static final String TOPIC = "foo/bar";

	@Test(timeout = TIMEOUT)
	public void generatesBrockerEventOnDigitalPinChange()
			throws InterruptedException, MqttSecurityException, MqttException,
			IOException {

		int pin = 1;
		client.setAnalogs();
		client.setDigitals(pin);

		Server broker = startBroker();
		final List<Exception> exceptions = new ArrayList<Exception>();
		AnotherMqttClient amc = new AnotherMqttClient(TOPIC);
		try {
			amc.connect();
			try {
				startClientInBackground(exceptions, client, TOPIC);
				simulateArduinoToMqtt(arduinoCommand("dred").forPin(pin)
						.withValue(1));
			} finally {
				client.close();
				amc.disconnect();
			}
		} finally {
			broker.stopServer();
		}

		assertThat(exceptions.isEmpty(), is(true));
		assertThat(
				amc.hasReceived(),
				is(Collections.singletonList(MqttMessageBuilder
						.messageWithBasicTopic(TOPIC).forDigitalPin(pin)
						.withValue(1).createGetMessage())));
	}

	@Test(timeout = TIMEOUT)
	public void generatesBrockerEventOnAnalogPinChange()
			throws InterruptedException, MqttSecurityException, MqttException,
			IOException {

		int pin = 1;
		int value = 45;
		client.setAnalogs(pin);
		client.setDigitals();

		Server broker = startBroker();
		final List<Exception> exceptions = new ArrayList<Exception>();
		AnotherMqttClient amc = new AnotherMqttClient(TOPIC);
		try {
			amc.connect();
			try {
				startClientInBackground(exceptions, client, TOPIC);
				simulateArduinoToMqtt(arduinoCommand("ared").forPin(pin)
						.withValue(value));
			} finally {
				client.close();
				amc.disconnect();
			}
		} finally {
			broker.stopServer();
		}

		assertThat(exceptions.isEmpty(), is(true));
		assertThat(
				amc.hasReceived(),
				is(Collections.singletonList(MqttMessageBuilder
						.messageWithBasicTopic(TOPIC).forAnalogPin(pin)
						.withValue(value).createGetMessage())));
	}

	private void simulateArduinoToMqtt(String message) {
		int[] codepoints = toCodepoints(message);
		connectionContact.parseInput("someId", codepoints.length, codepoints);
	}

	private Server startBroker() throws IOException, InterruptedException {
		Server broker = new Server();
		broker.startServer();
		return broker;
	}

	private void startClientInBackground(final List<Exception> exceptions,
			final MqttMain client, final String brokerTopic)
			throws InterruptedException {
		new Thread() {
			{
				setDaemon(true);
				start();
			}

			@Override
			public void run() {
				try {
					client.setBrokerTopic(brokerTopic);
					client.doMain();
				} catch (MqttException e) {
					exceptions.add(e);
				} catch (InterruptedException e) {
					exceptions.add(e);
				}
			}
		};
		StopWatch stopWatch = new StopWatch().start();
		while (!client.isConnected()) {
			MILLISECONDS.sleep(250);
			int secs = 5;
			if (stopWatch.getTime() > SECONDS.toMillis(secs)) {
				throw new IllegalStateException("Could not connect within "
						+ secs + " seconds");
			}
		}
	}

}
