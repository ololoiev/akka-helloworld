package com.example.iot;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.PoisonPill;
import akka.testkit.TestKit;
import org.junit.BeforeClass;
import org.junit.Test;
import scala.concurrent.duration.Duration;

import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class DeviceTest {

    private static ActorSystem system;

    @BeforeClass
    public static void setUp() {
        system = ActorSystem.create("test-system");
    }

    @Test
    public void testReplyWithEmptyReadingIfNoTemperatureIsKnown() {
        TestKit probe = new TestKit(system);
        ActorRef deviceActor = system.actorOf(Device.props("group", "device"));
        deviceActor.tell(new Device.ReadTemperature(42L), probe.testActor());
        Device.RespondTemperature response = probe.expectMsgClass(Device.RespondTemperature.class);
        assertEquals(42L, response.requestId);
        assertEquals(Optional.empty(), response.value);
    }

    @Test
    public void testReplyWithLatestTemperatureReading() {
        TestKit probe = new TestKit(system);
        ActorRef deviceActor = system.actorOf(Device.props("group", "device"));

        deviceActor.tell(new Device.RecordTemperature(1L, 24.0), probe.testActor());
        assertEquals(1L, probe.expectMsgClass(Device.TemperatureRecorded.class).requestId);

        deviceActor.tell(new Device.ReadTemperature(2L), probe.testActor());
        Device.RespondTemperature response1 = probe.expectMsgClass(Device.RespondTemperature.class);
        assertEquals(2L, response1.requestId);
        assertEquals(Optional.of(24.0), response1.value);

        deviceActor.tell(new Device.RecordTemperature(3L, 55.0), probe.testActor());
        assertEquals(3L, probe.expectMsgClass(Device.TemperatureRecorded.class).requestId);

        deviceActor.tell(new Device.ReadTemperature(4L), probe.testActor());
        Device.RespondTemperature response2 = probe.expectMsgClass(Device.RespondTemperature.class);
        assertEquals(4L, response2.requestId);
        assertEquals(Optional.of(55.0), response2.value);
    }

    @Test
    public void testReplyToRegistrationRequests() {
        TestKit probe = new TestKit(system);
        ActorRef deviceActor = system.actorOf(Device.props("group", "device"));

        deviceActor.tell(new DeviceManager.RequestTrackDevice("group", "device"), probe.testActor());
        probe.expectMsgClass(DeviceManager.DeviceRegistered.class);
        assertEquals(deviceActor, probe.lastSender());
    }

    @Test
    public void testIgnoreWrongRegistrationRequests() {
        TestKit probe = new TestKit(system);
        ActorRef deviceActor = system.actorOf(Device.props("group", "device"));

        deviceActor.tell(new DeviceManager.RequestTrackDevice("wrongGroup", "device"), probe.testActor());
        probe.expectNoMessage();

        deviceActor.tell(new DeviceManager.RequestTrackDevice("group", "wrongDevice"), probe.testActor());
        probe.expectNoMessage();
    }

    @Test
    public void testRegisterDeviceActor() {
        TestKit probe = new TestKit(system);
        ActorRef groupActor = system.actorOf(DeviceGroup.props("group"));

        groupActor.tell(new DeviceManager.RequestTrackDevice("group", "device1"), probe.testActor());
        probe.expectMsgClass(DeviceManager.DeviceRegistered.class);
        ActorRef deviceActor1 = probe.lastSender();

        groupActor.tell(new DeviceManager.RequestTrackDevice("group", "device2"), probe.testActor());
        probe.expectMsgClass(DeviceManager.DeviceRegistered.class);
        ActorRef deviceActor2 = probe.lastSender();
        assertNotEquals(deviceActor1, deviceActor2);

        // Check that the device actors are working
        deviceActor1.tell(new Device.RecordTemperature(0L, 1.0), probe.testActor());
        assertEquals(0L, probe.expectMsgClass(Device.TemperatureRecorded.class).requestId);
        deviceActor2.tell(new Device.RecordTemperature(1L, 2.0), probe.testActor());
        assertEquals(1L, probe.expectMsgClass(Device.TemperatureRecorded.class).requestId);
    }

    @Test
    public void testIgnoreRequestsForWrongGroupId() {
        TestKit probe = new TestKit(system);
        ActorRef groupActor = system.actorOf(DeviceGroup.props("group"));

        groupActor.tell(new DeviceManager.RequestTrackDevice("wrongGroup", "device1"), probe.testActor());
        probe.expectNoMessage();
    }

    @Test
    public void testReturnSameActorForSameDeviceId() {
        TestKit probe = new TestKit(system);
        ActorRef groupActor = system.actorOf(DeviceGroup.props("group"));

        groupActor.tell(new DeviceManager.RequestTrackDevice("group", "device1"), probe.testActor());
        probe.expectMsgClass(DeviceManager.DeviceRegistered.class);
        ActorRef deviceActor1 = probe.lastSender();

        groupActor.tell(new DeviceManager.RequestTrackDevice("group", "device1"), probe.testActor());
        probe.expectMsgClass(DeviceManager.DeviceRegistered.class);
        ActorRef deviceActor2 = probe.lastSender();
        assertEquals(deviceActor1, deviceActor2);
    }

    @Test
    public void testListActiveDevices() {
        TestKit probe = new TestKit(system);
        ActorRef groupActor = system.actorOf(DeviceGroup.props("group"));

        groupActor.tell(new DeviceManager.RequestTrackDevice("group", "device1"), probe.testActor());
        probe.expectMsgClass(DeviceManager.DeviceRegistered.class);

        groupActor.tell(new DeviceManager.RequestTrackDevice("group", "device2"), probe.testActor());
        probe.expectMsgClass(DeviceManager.DeviceRegistered.class);

        groupActor.tell(new DeviceGroup.RequestDeviceList(0L), probe.testActor());
        DeviceGroup.ReplyDeviceList reply = probe.expectMsgClass(DeviceGroup.ReplyDeviceList.class);
        assertEquals(0L, reply.requestId);
        assertEquals(Stream.of("device1", "device2").collect(Collectors.toSet()), reply.ids);
    }

    @Test
    public void testListActiveDevicesAfterOneShutsDown() {
        TestKit probe = new TestKit(system);
        ActorRef groupActor = system.actorOf(DeviceGroup.props("group"));

        groupActor.tell(new DeviceManager.RequestTrackDevice("group", "device1"), probe.testActor());
        probe.expectMsgClass(DeviceManager.DeviceRegistered.class);
        ActorRef toShutDown = probe.lastSender();

        groupActor.tell(new DeviceManager.RequestTrackDevice("group", "device2"), probe.testActor());
        probe.expectMsgClass(DeviceManager.DeviceRegistered.class);

        groupActor.tell(new DeviceGroup.RequestDeviceList(0L), probe.testActor());
        DeviceGroup.ReplyDeviceList reply = probe.expectMsgClass(DeviceGroup.ReplyDeviceList.class);
        assertEquals(0L, reply.requestId);
        assertEquals(Stream.of("device1", "device2").collect(Collectors.toSet()), reply.ids);

        probe.watch(toShutDown);
        toShutDown.tell(PoisonPill.getInstance(), ActorRef.noSender());
        probe.expectTerminated(toShutDown, Duration.fromNanos(100_000_000));

        // using awaitAssert to retry because it might take longer for the groupActor
        // to see the Terminated, that order is undefined
        probe.awaitAssert(
                () -> {
                    groupActor.tell(new DeviceGroup.RequestDeviceList(1L), probe.testActor());
                    DeviceGroup.ReplyDeviceList r = probe.expectMsgClass(DeviceGroup.ReplyDeviceList.class);
                    assertEquals(1L, r.requestId);
                    assertEquals(Stream.of("device2").collect(Collectors.toSet()), r.ids);
                    return null;
                }, Duration.fromNanos(100_000_000), Duration.fromNanos(30));
    }
}
