package com.github.pfichtner.ardulink;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.dna.mqtt.moquette.server.Server;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttSecurityException;
import org.junit.Test;
import org.zu.ardulink.Link;

public class MqttClientIntegration {

	private static final long TIMEOUT = 10 * 1000;;

	private final Link mock = mock(Link.class);
	private MqttMain client = new MqttMain() {
		@Override
		protected Link createLink() {
			return mock;
		}
	};

	private static final String TOPIC = "foo/bar";

	@Test(timeout = TIMEOUT)
	public void processesBrockerEventPowerOnDigitalPin()
			throws InterruptedException, MqttSecurityException, MqttException,
			IOException {

		int pin = 1;
		client.setAnalogs();
		client.setDigitals();

		Server broker = startBroker();
		final List<Exception> exceptions = new ArrayList<Exception>();
		try {
			AnotherMqttClient amc = new AnotherMqttClient(TOPIC).connect();
			try {
				startClientInBackground(exceptions, client, TOPIC);
				amc.switchDigitalPin(pin, true);
			} finally {
				amc.disconnect();
			}
		} finally {
			broker.stopServer();
		}

		assertThat(exceptions.isEmpty(), is(true));
		// verify(mock).addDigitalReadChangeListener(
		// Mockito.<DigitalReadChangeListener> any());
		verify(mock).sendPowerPinSwitch(pin, 1);
		verifyNoMoreInteractions(mock);
	}

	@Test(timeout = TIMEOUT)
	public void processesBrockerEventPowerOnAnalogPin()
			throws InterruptedException, MqttSecurityException, MqttException,
			IOException {

		int pin = 1;
		client.setAnalogs();
		client.setDigitals();

		Server broker = startBroker();
		final List<Exception> exceptions = new ArrayList<Exception>();
		int value = 123;
		try {
			AnotherMqttClient amc = new AnotherMqttClient(TOPIC).connect();
			try {
				startClientInBackground(exceptions, client, TOPIC);
				amc.switchAnalogPin(pin, value);
			} finally {
				amc.disconnect();
			}
		} finally {
			broker.stopServer();
		}

		assertThat(exceptions.isEmpty(), is(true));
		// verify(mock).addAnalogReadChangeListener(
		// Mockito.<AnalogReadChangeListener> any());
		verify(mock).sendPowerPinIntensity(pin, value);
		verifyNoMoreInteractions(mock);
	}

	private Server startBroker() throws IOException {
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
