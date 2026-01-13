package com.miniide;

import java.util.concurrent.Callable;
import java.util.concurrent.Semaphore;

/**
 * Enforces serialized agent turns so only one agent runs at a time.
 */
public class AgentTurnGate {
    private final Semaphore semaphore = new Semaphore(1, true);

    public <T> T run(Callable<T> task) throws Exception {
        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        }
        try {
            return task.call();
        } finally {
            semaphore.release();
        }
    }
}
