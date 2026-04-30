package com.aaroneline.hegeljava;


import org.junit.jupiter.api.extension.*;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.concurrent.BlockingQueue;

public final class HegelInvocationStream extends Spliterators.AbstractSpliterator<TestTemplateInvocationContext> {
    private final BlockingQueue<Events.InvocationEvent> queue;

    public HegelInvocationStream(BlockingQueue<Events.InvocationEvent> queue) {
       super(Long.MAX_VALUE, Spliterator.ORDERED);
       this.queue = queue;
    }

    @Override
    public boolean tryAdvance(Consumer<? super TestTemplateInvocationContext> consumer) {
        final Events.InvocationEvent event;

        try {
            event = queue.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }

        return switch (event) {
            case Events.DoneEvent e -> false;
            case Events.ErrorEvent e -> throw new RuntimeException("Hegel driver error", e.cause());
            case Events.TestCaseEvent(var testCase) ->  {
                consumer.accept(new HegelInvocationContext(testCase));
                yield true;
            }
        };
    }

}
