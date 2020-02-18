package com.example.iot;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.testkit.TestKit;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class DeviceGroupTest {

    private static ActorSystem system;

    @BeforeClass
    public static void setUp() {
        system = ActorSystem.create("test-system");
    }
    
    @Test
    public void testCollectTemperaturesFromAllActiveDevices() {
        TestKit probe = new TestKit(system);
        ActorRef groupActor = system.actorOf(DeviceGroup.props("group"), "onlyTest");

        groupActor.tell(new DeviceManager.RequestTrackDevice("group", "device1"), probe.testActor());
        probe.expectMsgClass(DeviceManager.DeviceRegistered.class);
        ActorRef deviceActor1 = probe.lastSender();

        groupActor.tell(new DeviceManager.RequestTrackDevice("group", "device2"), probe.testActor());
        probe.expectMsgClass(DeviceManager.DeviceRegistered.class);
        ActorRef deviceActor2 = probe.lastSender();

        groupActor.tell(new DeviceManager.RequestTrackDevice("group", "device3"), probe.testActor());
        probe.expectMsgClass(DeviceManager.DeviceRegistered.class);
        ActorRef deviceActor3 = probe.lastSender();

        // Check that the device actors are working
        deviceActor1.tell(new Device.RecordTemperature(0L, 1.0), probe.testActor());
        assertEquals(0L, probe.expectMsgClass(Device.TemperatureRecorded.class).requestId);
        deviceActor2.tell(new Device.RecordTemperature(1L, 2.0), probe.testActor());
        assertEquals(1L, probe.expectMsgClass(Device.TemperatureRecorded.class).requestId);
        // No temperature for device 3

        groupActor.tell(new DeviceGroup.RequestAllTemperatures(0L), probe.testActor());
        DeviceGroup.RespondAllTemperatures response =
                probe.expectMsgClass(DeviceGroup.RespondAllTemperatures.class);
        assertEquals(0L, response.requestId);

        Map<String, DeviceGroup.TemperatureReading> expectedTemperatures = new HashMap<>();
        expectedTemperatures.put("device1", new DeviceGroup.Temperature(1.0));
        expectedTemperatures.put("device2", new DeviceGroup.Temperature(2.0));
        expectedTemperatures.put("device3", DeviceGroup.TemperatureNotAvailable.INSTANCE);

        assertEquals(expectedTemperatures, response.temperatures);
    }
}