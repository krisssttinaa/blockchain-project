package main;

public class Logger {
    public static void log(String message) {
        System.out.println(message);
    }

    public static void logError(String message) {
        System.err.println(message);
    }

    public static void logError(Exception e) {
        System.err.println(e);
    }

    public static void logError(String message, Exception e) {
        System.err.println(message);
        System.err.println(e);
    }

    public static void logError(String message, Error e) {
        System.err.println(message);
        System.err.println(e);
    }

    public static void logError(Error e) {
        System.err.println(e);
    }

    public static void logError(Throwable e) {
        System.err.println(e);
    }

    public static void log(Throwable e) {
        System.out.println(e);
    }
}
