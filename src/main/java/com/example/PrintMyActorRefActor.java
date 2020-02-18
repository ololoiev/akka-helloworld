package com.example;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import scala.Option;
import scala.PartialFunction;
import scala.runtime.BoxedUnit;

class PrintMyActorRefActor extends AbstractActor {
    private int i = 0;

    @Override
    public void aroundReceive(PartialFunction<Object, BoxedUnit> receive, Object msg) {
        System.out.println("test 1");
        super.aroundReceive(receive, msg);
        System.out.println("test 2");
    }

    @Override
    public void aroundPreStart() {
        System.out.println("test 3");
        super.aroundPreStart();
        System.out.println("test 4");
    }

    @Override
    public void aroundPostStop() {
        System.out.println("test 5");
        super.aroundPostStop();
        System.out.println("test 6");
    }

    @Override
    public void aroundPreRestart(Throwable reason, Option<Object> message) {
        System.out.println("test 7");
        super.aroundPreRestart(reason, message);
        System.out.println("test 8");
    }

    @Override
    public void aroundPostRestart(Throwable reason) {
        System.out.println("test 9");
        super.aroundPostRestart(reason);
        System.out.println("test 10");
    }

    @Override
    public void preStart() throws Exception {
        System.out.println("test 11");
        super.preStart();
        System.out.println("test 12");
    }

    @Override
    public void postStop() throws Exception {
        System.out.println("test 13");
        super.postStop();
        System.out.println("test 14");
    }

    static Props props() {
        return Props.create(PrintMyActorRefActor.class, PrintMyActorRefActor::new);
    }

    @Override
    public Receive createReceive() {
        System.out.println("Recieve");
        return receiveBuilder()
                .matchEquals(
                        "printit",
                        p -> {
                            System.out.println("Recieve internal");
                            ActorRef secondRef = getContext().actorOf(Props.empty(), "second-actor" + i++);
                            System.out.println("Second: " + secondRef);
                            //throw new Exception("I failed!");
                        })
                .build();
    }
}
