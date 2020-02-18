package com.example.iot;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.PoisonPill;
import akka.testkit.TestKit;
import org.junit.BeforeClass;
import org.junit.Test;
import scala.concurrent.duration.FiniteDuration;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class DeviceGroupQueryTest {

    private static ActorSystem system;

    @BeforeClass
    public static void setUp() {
        system = ActorSystem.create("test-system");
    }

    @Test
    public void testReturnTemperatureValueForWorkingDevices() {
        TestKit requester = new TestKit(system);

        TestKit device1 = new TestKit(system);
        TestKit device2 = new TestKit(system);

        Map<ActorRef, String> actorToDeviceId = new HashMap<>();
        actorToDeviceId.put(device1.testActor(), "device1");
        actorToDeviceId.put(device2.testActor(), "device2");

        ActorRef queryActor =
                system.actorOf(
                        DeviceGroupQuery.props(
                                actorToDeviceId, 1L, requester.testActor(), new FiniteDuration(3, TimeUnit.SECONDS)));

        assertEquals(0L, device1.expectMsgClass(Device.ReadTemperature.class).requestId);
        assertEquals(0L, device2.expectMsgClass(Device.ReadTemperature.class).requestId);

        queryActor.tell(new Device.RespondTemperature(0L, Optional.of(1.0)), device1.testActor());
        queryActor.tell(new Device.RespondTemperature(0L, Optional.of(2.0)), device2.testActor());

        DeviceGroup.RespondAllTemperatures response =
                requester.expectMsgClass(DeviceGroup.RespondAllTemperatures.class);
        assertEquals(1L, response.requestId);

        Map<String, DeviceGroup.TemperatureReading> expectedTemperatures = new HashMap<>();
        expectedTemperatures.put("device1", new DeviceGroup.Temperature(1.0));
        expectedTemperatures.put("device2", new DeviceGroup.Temperature(2.0));

        assertEquals(expectedTemperatures, response.temperatures);
    }

    @Test
    public void testReturnTemperatureNotAvailableForDevicesWithNoReadings() {
        TestKit requester = new TestKit(system);

        TestKit device1 = new TestKit(system);
        TestKit device2 = new TestKit(system);

        Map<ActorRef, String> actorToDeviceId = new HashMap<>();
        actorToDeviceId.put(device1.testActor(), "device1");
        actorToDeviceId.put(device2.testActor(), "device2");

        ActorRef queryActor =
                system.actorOf(
                        DeviceGroupQuery.props(
                                actorToDeviceId, 1L, requester.testActor(), new FiniteDuration(3, TimeUnit.SECONDS)));

        assertEquals(0L, device1.expectMsgClass(Device.ReadTemperature.class).requestId);
        assertEquals(0L, device2.expectMsgClass(Device.ReadTemperature.class).requestId);

        queryActor.tell(new Device.RespondTemperature(0L, Optional.empty()), device1.testActor());
        queryActor.tell(new Device.RespondTemperature(0L, Optional.of(2.0)), device2.testActor());

        DeviceGroup.RespondAllTemperatures response =
                requester.expectMsgClass(DeviceGroup.RespondAllTemperatures.class);
        assertEquals(1L, response.requestId);

        Map<String, DeviceGroup.TemperatureReading> expectedTemperatures = new HashMap<>();
        expectedTemperatures.put("device1", DeviceGroup.TemperatureNotAvailable.INSTANCE);
        expectedTemperatures.put("device2", new DeviceGroup.Temperature(2.0));

        assertEquals(expectedTemperatures, response.temperatures);
    }

    @Test
    public void testReturnTemperatureReadingEvenIfDeviceStopsAfterAnswering() {
        TestKit requester = new TestKit(system);

        TestKit device1 = new TestKit(system);
        TestKit device2 = new TestKit(system);

        Map<ActorRef, String> actorToDeviceId = new HashMap<>();
        actorToDeviceId.put(device1.testActor(), "device1");
        actorToDeviceId.put(device2.testActor(), "device2");

        ActorRef queryActor =
                system.actorOf(
                        DeviceGroupQuery.props(
                                actorToDeviceId, 1L, requester.testActor(), new FiniteDuration(3, TimeUnit.SECONDS)));

        assertEquals(0L, device1.expectMsgClass(Device.ReadTemperature.class).requestId);
        assertEquals(0L, device2.expectMsgClass(Device.ReadTemperature.class).requestId);

        queryActor.tell(new Device.RespondTemperature(0L, Optional.of(1.0)), device1.testActor());
        queryActor.tell(new Device.RespondTemperature(0L, Optional.of(2.0)), device2.testActor());
        device2.testActor().tell(PoisonPill.getInstance(), ActorRef.noSender());

        DeviceGroup.RespondAllTemperatures response =
                requester.expectMsgClass(DeviceGroup.RespondAllTemperatures.class);
        assertEquals(1L, response.requestId);

        Map<String, DeviceGroup.TemperatureReading> expectedTemperatures = new HashMap<>();
        expectedTemperatures.put("device1", new DeviceGroup.Temperature(1.0));
        expectedTemperatures.put("device2", new DeviceGroup.Temperature(2.0));

        assertEquals(expectedTemperatures, response.temperatures);
    }

    @Test
    public void testReturnDeviceTimedOutIfDeviceDoesNotAnswerInTime() {
        TestKit requester = new TestKit(system);

        TestKit device1 = new TestKit(system);
        TestKit device2 = new TestKit(system);

        Map<ActorRef, String> actorToDeviceId = new HashMap<>();
        actorToDeviceId.put(device1.testActor(), "device1");
        actorToDeviceId.put(device2.testActor(), "device2");

        ActorRef queryActor =
                system.actorOf(
                        DeviceGroupQuery.props(
                                actorToDeviceId, 1L, requester.testActor(), new FiniteDuration(1, TimeUnit.SECONDS)));

        assertEquals(0L, device1.expectMsgClass(Device.ReadTemperature.class).requestId);
        assertEquals(0L, device2.expectMsgClass(Device.ReadTemperature.class).requestId);

        queryActor.tell(new Device.RespondTemperature(0L, Optional.of(1.0)), device1.testActor());

        DeviceGroup.RespondAllTemperatures response =
                requester.expectMsgClass(
                        FiniteDuration.apply(5, TimeUnit.SECONDS), DeviceGroup.RespondAllTemperatures.class);
        assertEquals(1L, response.requestId);

        Map<String, DeviceGroup.TemperatureReading> expectedTemperatures = new HashMap<>();
        expectedTemperatures.put("device1", new DeviceGroup.Temperature(1.0));
        expectedTemperatures.put("device2", DeviceGroup.DeviceTimedOut.INSTANCE);

        assertEquals(expectedTemperatures, response.temperatures);
    }
}