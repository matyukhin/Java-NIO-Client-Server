package server;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static java.lang.Thread.sleep;

class ServerReporter implements Runnable {
    private static final int REPORTING_INTERVAL_MILLIS = 5000;
    private int connectionCount;
    private int messageCount;
    private boolean running;

    ServerReporter() {
        connectionCount = 0;
        messageCount = 0;
        running = true;
    }

    void shutdown() {
        running = false;
    }

    synchronized void incrementConnectionCounter() {
        ++connectionCount;
    }

    synchronized void decrementConnectionCounter() {
        --connectionCount;
    }

    synchronized void incrementMessageCounter() {
        ++messageCount;
    }

    synchronized private void resetMessageCounter() {
        messageCount = 0;
    }

    private void printSummary() {
        LocalDateTime ldt = LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault());
        System.out.printf("[%02d:%02d:%02d] Throughput: %.2f messages/s, Number of active clients: %d\n",
                ldt.getHour(), ldt.getMinute(), ldt.getSecond(),
                (double) messageCount / (REPORTING_INTERVAL_MILLIS / 1000), connectionCount);
    }

    @Override
    public void run() {
        System.out.println("Starting reports.");
        while (running) {
            try {
                sleep(REPORTING_INTERVAL_MILLIS);
            }
            catch (InterruptedException e) {
                System.out.println(e.getMessage());
            }
            printSummary();
            resetMessageCounter();
        }
        System.out.println("The server has shutdown. Stopping reports.");
    }
}
