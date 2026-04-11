package net.asd.union.utils.client;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;

import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

public final class ServerPingController {
    private static final int CONNECT_TIMEOUT_MILLIS = 3000;

    private static final AtomicInteger REFRESH_GENERATION = new AtomicInteger();
    private static final Map<Future<?>, Integer> PING_TASKS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<ChannelFuture, Integer> CONNECT_FUTURES = new java.util.concurrent.ConcurrentHashMap<>();
    private static final ThreadLocal<Integer> THREAD_GENERATION = new ThreadLocal<>();

    private ServerPingController() {
    }

    public static Future<?> submitPingTask(ThreadPoolExecutor executor, Runnable task) {
        final int generation = REFRESH_GENERATION.get();

        FutureTask<Void> futureTask = new FutureTask<Void>(new Runnable() {
            @Override
            public void run() {
                runPingTask(generation, task);
            }
        }, null) {
            @Override
            protected void done() {
                PING_TASKS.remove(this);
            }
        };

        PING_TASKS.put(futureTask, generation);

        if (generation != REFRESH_GENERATION.get()) {
            futureTask.cancel(false);
            return futureTask;
        }

        executor.execute(futureTask);
        return futureTask;
    }

    public static void beginNewRefreshCycle() {
        int newGeneration = REFRESH_GENERATION.incrementAndGet();
        cancelObsoletePingTasks(newGeneration);
        cancelObsoleteConnects(newGeneration);
    }

    public static boolean isServerPingerThread() {
        return THREAD_GENERATION.get() != null || Thread.currentThread().getName().startsWith("Server Pinger");
    }

    public static boolean shouldCancelCurrentPing() {
        Integer generation = THREAD_GENERATION.get();
        return generation != null && (Thread.currentThread().isInterrupted() || generation.intValue() != REFRESH_GENERATION.get());
    }

    public static int getConnectTimeoutMillis() {
        return CONNECT_TIMEOUT_MILLIS;
    }

    public static void registerConnectFuture(ChannelFuture future) {
        final int generation = getCurrentTaskGeneration();

        CONNECT_FUTURES.put(future, generation);
        future.addListener(ignored -> {
            CONNECT_FUTURES.remove(future);

            if (generation != REFRESH_GENERATION.get()) {
                closeFutureChannel(future);
            }
        });

        if (generation != REFRESH_GENERATION.get() || Thread.currentThread().isInterrupted()) {
            cancelConnectFuture(future);
        }
    }

    private static void runPingTask(int generation, Runnable task) {
        if (generation != REFRESH_GENERATION.get() || Thread.currentThread().isInterrupted()) {
            return;
        }

        THREAD_GENERATION.set(generation);

        try {
            task.run();
        } finally {
            THREAD_GENERATION.remove();
        }
    }

    private static int getCurrentTaskGeneration() {
        Integer generation = THREAD_GENERATION.get();
        return generation != null ? generation : REFRESH_GENERATION.get();
    }

    private static void cancelObsoletePingTasks(int currentGeneration) {
        for (Map.Entry<Future<?>, Integer> entry : PING_TASKS.entrySet()) {
            if (entry.getValue() >= currentGeneration) {
                continue;
            }

            Future<?> future = entry.getKey();
            if (PING_TASKS.remove(future, entry.getValue())) {
                future.cancel(true);
            }
        }
    }

    private static void cancelObsoleteConnects(int currentGeneration) {
        for (Map.Entry<ChannelFuture, Integer> entry : CONNECT_FUTURES.entrySet()) {
            if (entry.getValue() >= currentGeneration) {
                continue;
            }

            ChannelFuture future = entry.getKey();
            if (CONNECT_FUTURES.remove(future, entry.getValue())) {
                cancelConnectFuture(future);
            }
        }
    }

    private static void cancelConnectFuture(ChannelFuture future) {
        future.cancel(true);
        closeFutureChannel(future);
    }

    private static void closeFutureChannel(ChannelFuture future) {
        Channel channel = future.channel();

        if (channel != null && channel.isOpen()) {
            channel.close();
        }
    }
}
