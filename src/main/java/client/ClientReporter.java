package client;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static java.lang.Thread.sleep;

class ClientReporter implements Runnable {
    private static final int REPORTING_INTERVAL_MILLIS = 10000;
    private int sentCount;
    private int receivedCount;
    private boolean running;

    ClientReporter() {
        sentCount = 0;
        receivedCount = 0;
        running = true;
    }

    void shutdown() {
        running = false;
    }

    synchronized void incrementSentCounter() {
        ++sentCount;
    }

    synchronized void incrementReceivedCounter() {
        ++receivedCount;
    }

    synchronized private void resetCounters() {
        sentCount = 0;
        receivedCount = 0;
    }

    private void printSummary() {
        LocalDateTime ldt = LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault());
        System.out.printf("[%02d:%02d:%02d] Sent count: %d, Received count: %d\n",
                ldt.getHour(), ldt.getMinute(), ldt.getSecond(), sentCount, receivedCount);
        resetCounters();
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
            resetCounters();
        }
        System.out.println("The connection to the server is lost. Stopping reports.");
    }
}
