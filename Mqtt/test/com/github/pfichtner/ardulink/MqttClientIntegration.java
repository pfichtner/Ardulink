package com.github.pfichtner.ardulink;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.dna.mqtt.moquette.server.Server;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttPersistenceException;
import org.eclipse.paho.client.mqttv3.MqttSecurityException;
import org.junit.Test;
import org.mockito.Mockito;
import org.zu.ardulink.Link;
import org.zu.ardulink.event.DigitalReadChangeListener;

import com.github.pfichtner.ardulink.util.Message;
import com.github.pfichtner.ardulink.util.MqttMessageBuilder;

public class MqttClientIntegration {

	private static final long TIMEOUT = 10 * 1000;;

	@Test(timeout = TIMEOUT)
	public void processesBrockerEventPowerOnDigitalPin()
			throws InterruptedException, MqttSecurityException, MqttException,
			IOException {

		final Link mock = mock(Link.class);
		MqttMain client = new MqttMain() {
			@Override
			protected Link createLink() {
				return mock;
			}
		};

		Server broker = startBroker();
		final List<Exception> exceptions = new ArrayList<Exception>();
		try {
			String topic = "foo/bar";
			org.eclipse.paho.client.mqttv3.MqttClient mqttClient = mqttClient();

			try {
				startClientInBackground(exceptions, client, topic);
				switchDigitalPin(mqttClient, topic, 1, true);
				TimeUnit.SECONDS.sleep(3);
			} finally {
				mqttClient.disconnect();
			}
		} finally {
			broker.stopServer();
		}

		assertTrue(exceptions.isEmpty());
		verify(mock).addDigitalReadChangeListener(
				Mockito.<DigitalReadChangeListener> any());
		verify(mock).sendPowerPinSwitch(1, 1);
		verifyNoMoreInteractions(mock);
	}

	private void switchDigitalPin(
			org.eclipse.paho.client.mqttv3.MqttClient mqttClient, String topic,
			int pin, Object value) throws MqttPersistenceException,
			MqttException {
		Message msg = MqttMessageBuilder.messageWithBasicTopic(topic)
				.forDigitalPin(pin).withValue(value).createSetMessage();
		mqttClient.publish(msg.getTopic(), new MqttMessage(msg.getMessage()
				.getBytes()));

	}

	private org.eclipse.paho.client.mqttv3.MqttClient mqttClient()
			throws MqttException, MqttSecurityException {
		org.eclipse.paho.client.mqttv3.MqttClient mqttClient = new org.eclipse.paho.client.mqttv3.MqttClient(
				"tcp://localhost:1883", "clientId");
		mqttClient.connect();
		return mqttClient;
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
		StopWatch stopWatch = new StopWatch();
		stopWatch.start();
		while (!client.isConnected()) {
			TimeUnit.MILLISECONDS.sleep(250);
			if (stopWatch.getTime() > TimeUnit.SECONDS.toMillis(5)) {
				throw new IllegalStateException(
						"Could not connect within 5 seconds");
			}
		}
	}
}
