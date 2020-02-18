package com.example.iot;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import akka.actor.ActorSelection;
import akka.actor.ActorSystem;
import akka.actor.ActorRef;
import akka.actor.Inbox;

public class IotMain {

    public static void main(String[] args) throws IOException, ExecutionException, InterruptedException, TimeoutException {
        ActorSystem system = ActorSystem.create("iot-system");
        final Inbox inbox = Inbox.create(system);

        try {
            // Create top level supervisor
            ActorRef supervisor = system.actorOf(IotSupervisor.props(), "iot-supervisor");

            //example of use
            ActorRef deviceManager = system.actorSelection("/user/iot-supervisor/device-manager")
                    .resolveOne(Duration.ofSeconds(1)).toCompletableFuture().get();
            inbox.send(deviceManager, new DeviceManager.RequestTrackDevice("temperature", "1"));
            Object deviceRegisterObject = inbox.receive(Duration.ofSeconds(2));

            ActorRef temperatureGroup = system.actorSelection("/user/iot-supervisor/device-manager/group-temperature")
                    .resolveOne(Duration.ofSeconds(1)).toCompletableFuture().get();
            inbox.send(temperatureGroup, new DeviceGroup.RequestDeviceList(234));
            Object devicesObject = inbox.receive(Duration.ofSeconds(2));
            ActorRef device = null;
            if (devicesObject instanceof DeviceGroup.ReplyDeviceList) {
                Set<String> ids = ((DeviceGroup.ReplyDeviceList) devicesObject).ids;
                device = system.actorSelection("/user/iot-supervisor/device-manager/group-temperature/device-" + ids.iterator().next())
                        .resolveOne(Duration.ofSeconds(1)).toCompletableFuture().get();
            }

            if (device != null) {
                inbox.send(device, new Device.RecordTemperature(443, 4.6));
                Object receive = inbox.receive(Duration.ofSeconds(1));
                inbox.send(device, new Device.ReadTemperature(453));
                Object temperatureObject = inbox.receive(Duration.ofSeconds(1));
                System.out.println("current value: " + ((Device.RespondTemperature) temperatureObject).value.get());
            }


            System.out.println("Press ENTER to exit the system");
            System.in.read();
        } finally {
            system.terminate();
        }
    }
}
