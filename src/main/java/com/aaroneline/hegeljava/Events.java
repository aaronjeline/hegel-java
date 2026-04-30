package com.aaroneline.hegeljava;


public class Events {
    public sealed interface InvocationEvent permits TestCaseEvent, DoneEvent, ErrorEvent {}

    public record TestCaseEvent(TestCase testCase) implements InvocationEvent {}

    public record DoneEvent() implements InvocationEvent {}

    public record ErrorEvent(Throwable cause) implements InvocationEvent {}
}
