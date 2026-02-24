import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A single-class Java program that demonstrates major threading and executor framework concepts.
 */
public class ThreadingConceptsSingleClass {

    private static final Object MONITOR = new Object();
    private static volatile boolean ready = false;
    private static int synchronizedCounter = 0;
    private static final Lock explicitLock = new ReentrantLock();
    private static final AtomicInteger atomicCounter = new AtomicInteger(0);
    private static final ThreadLocal<String> threadLocal = ThreadLocal.withInitial(() -> "unset");

    private static synchronized void incrementSynchronizedCounter() {
        synchronizedCounter++;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== Threading and Executor Framework Concepts in One Class ===");

        threadCreationAndLifecycleDemo();
        synchronizedAndLockDemo();
        waitNotifyDemo();
        volatileAndAtomicDemo();
        threadLocalDemo();
        producerConsumerDemo();
        countDownLatchAndCyclicBarrierDemo();
        semaphoreDemo();
        executorServiceDemo();
        scheduledExecutorDemo();
        completableFutureDemo();
        forkJoinDemo();

        System.out.println("=== Demo Completed ===");
    }

    private static void threadCreationAndLifecycleDemo() throws InterruptedException {
        System.out.println("\n1) Thread creation, start(), join(), interrupt() and states");

        Thread extendingThread = new Thread() {
            @Override
            public void run() {
                System.out.println("[Extending Thread] Running on: " + Thread.currentThread().getName());
                try {
                    Thread.sleep(120);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.out.println("[Extending Thread] Interrupted while sleeping");
                }
            }
        };

        Runnable runnableTask = () -> {
            System.out.println("[Runnable] Running on: " + Thread.currentThread().getName());
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            System.out.println("[Runnable] Stopped after interrupt");
        };

        Thread runnableThread = new Thread(runnableTask, "Runnable-Worker");

        System.out.println("Before start state: " + runnableThread.getState());
        extendingThread.start();
        runnableThread.start();

        Thread.sleep(140);
        runnableThread.interrupt();

        extendingThread.join();
        runnableThread.join();
        System.out.println("After join state: " + runnableThread.getState());
    }

    private static void synchronizedAndLockDemo() throws InterruptedException {
        System.out.println("\n2) synchronized keyword and ReentrantLock");

        Runnable syncTask = () -> {
            for (int i = 0; i < 500; i++) {
                incrementSynchronizedCounter();
                explicitLock.lock();
                try {
                    atomicCounter.incrementAndGet();
                } finally {
                    explicitLock.unlock();
                }
            }
        };

        Thread t1 = new Thread(syncTask, "Sync-1");
        Thread t2 = new Thread(syncTask, "Sync-2");
        t1.start();
        t2.start();
        t1.join();
        t2.join();

        System.out.println("synchronizedCounter = " + synchronizedCounter + " (expected 1000)");
        System.out.println("atomicCounter (with lock section) = " + atomicCounter.get() + " (expected 1000)");
    }

    private static void waitNotifyDemo() throws InterruptedException {
        System.out.println("\n3) wait() / notifyAll() using monitor lock");

        Thread waitingThread = new Thread(() -> {
            synchronized (MONITOR) {
                while (!ready) {
                    try {
                        MONITOR.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                System.out.println("Waiting thread resumed after notifyAll.");
            }
        }, "Waiting-Thread");

        Thread notifierThread = new Thread(() -> {
            synchronized (MONITOR) {
                ready = true;
                MONITOR.notifyAll();
                System.out.println("Notifier thread called notifyAll.");
            }
        }, "Notifier-Thread");

        waitingThread.start();
        Thread.sleep(120);
        notifierThread.start();

        waitingThread.join();
        notifierThread.join();
    }

    private static void volatileAndAtomicDemo() throws InterruptedException {
        System.out.println("\n4) volatile visibility + AtomicInteger lock-free increment");

        ready = false;
        AtomicInteger localAtomic = new AtomicInteger(0);

        Thread reader = new Thread(() -> {
            while (!ready) {
                // busy wait only for demo
            }
            System.out.println("Reader observed ready=true (volatile visibility works)");
        }, "Volatile-Reader");

        Thread writer = new Thread(() -> {
            for (int i = 0; i < 1000; i++) {
                localAtomic.incrementAndGet();
            }
            ready = true;
        }, "Volatile-Writer");

        reader.start();
        writer.start();
        reader.join();
        writer.join();

        System.out.println("Local atomic value = " + localAtomic.get() + " (expected 1000)");
    }

    private static void threadLocalDemo() throws InterruptedException {
        System.out.println("\n5) ThreadLocal for per-thread data");

        Runnable task = () -> {
            threadLocal.set("value-for-" + Thread.currentThread().getName());
            try {
                Thread.sleep(ThreadLocalRandom.current().nextInt(20, 80));
                System.out.println(Thread.currentThread().getName() + " -> " + threadLocal.get());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                threadLocal.remove();
            }
        };

        Thread t1 = new Thread(task, "TL-1");
        Thread t2 = new Thread(task, "TL-2");
        t1.start();
        t2.start();
        t1.join();
        t2.join();
    }

    private static void producerConsumerDemo() throws InterruptedException {
        System.out.println("\n6) Producer/Consumer with BlockingQueue");

        ArrayBlockingQueue<Integer> queue = new ArrayBlockingQueue<>(3);

        Thread producer = new Thread(() -> {
            try {
                for (int i = 1; i <= 5; i++) {
                    queue.put(i);
                    System.out.println("Produced: " + i);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "Producer");

        Thread consumer = new Thread(() -> {
            try {
                for (int i = 1; i <= 5; i++) {
                    int value = queue.take();
                    System.out.println("Consumed: " + value);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "Consumer");

        producer.start();
        consumer.start();
        producer.join();
        consumer.join();
    }

    private static void countDownLatchAndCyclicBarrierDemo() throws InterruptedException {
        System.out.println("\n7) CountDownLatch + CyclicBarrier coordination");

        CountDownLatch latch = new CountDownLatch(3);
        CyclicBarrier barrier = new CyclicBarrier(3, () -> System.out.println("Barrier action: all workers reached barrier."));

        Runnable worker = () -> {
            try {
                Thread.sleep(ThreadLocalRandom.current().nextInt(30, 120));
                System.out.println(Thread.currentThread().getName() + " finished phase-1");
                latch.countDown();
                barrier.await();
                System.out.println(Thread.currentThread().getName() + " passed barrier for phase-2");
            } catch (Exception e) {
                Thread.currentThread().interrupt();
            }
        };

        Thread w1 = new Thread(worker, "Worker-1");
        Thread w2 = new Thread(worker, "Worker-2");
        Thread w3 = new Thread(worker, "Worker-3");
        w1.start();
        w2.start();
        w3.start();

        latch.await();
        System.out.println("Main thread: all workers reported phase-1 done.");
        w1.join();
        w2.join();
        w3.join();
    }

    private static void semaphoreDemo() throws InterruptedException {
        System.out.println("\n8) Semaphore to limit concurrent access");

        Semaphore semaphore = new Semaphore(2);
        List<Thread> threads = new ArrayList<>();

        for (int i = 1; i <= 4; i++) {
            Thread t = new Thread(() -> {
                boolean permitAcquired = false;
                try {
                    semaphore.acquire();
                    permitAcquired = true;
                    System.out.println(Thread.currentThread().getName() + " entered critical section");
                    Thread.sleep(80);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    if (permitAcquired) {
                        semaphore.release();
                        System.out.println(Thread.currentThread().getName() + " exited critical section");
                    }
                }
            }, "Sem-" + i);
            threads.add(t);
        }

        for (Thread t : threads) {
            t.start();
        }
        for (Thread t : threads) {
            t.join();
        }
    }

    private static void executorServiceDemo() throws InterruptedException, ExecutionException, TimeoutException {
        System.out.println("\n9) Executor framework: fixed, single, cached pools + submit/invokeAll/Future");

        ExecutorService fixedPool = Executors.newFixedThreadPool(3);
        ExecutorService singlePool = Executors.newSingleThreadExecutor();
        ExecutorService cachedPool = Executors.newCachedThreadPool();

        try {
            Future<Integer> futureFromCallable = fixedPool.submit(() -> 21 * 2);
            fixedPool.execute(() -> System.out.println("fixedPool execute() task on " + Thread.currentThread().getName()));

            List<Callable<String>> callables = List.of(
                    () -> "Task-A on " + Thread.currentThread().getName(),
                    () -> "Task-B on " + Thread.currentThread().getName(),
                    () -> "Task-C on " + Thread.currentThread().getName());

            List<Future<String>> futures = singlePool.invokeAll(callables);
            for (Future<String> f : futures) {
                System.out.println("invokeAll result: " + f.get());
            }

            Queue<String> queue = new ConcurrentLinkedQueue<>();
            CountDownLatch latch = new CountDownLatch(3);
            for (int i = 1; i <= 3; i++) {
                int id = i;
                cachedPool.submit(() -> {
                    queue.add("CachedTask-" + id + " on " + Thread.currentThread().getName());
                    latch.countDown();
                });
            }
            latch.await();
            queue.forEach(System.out::println);

            Integer value = futureFromCallable.get(1, TimeUnit.SECONDS);
            System.out.println("Future from callable = " + value);
        } finally {
            shutdownExecutor(fixedPool);
            shutdownExecutor(singlePool);
            shutdownExecutor(cachedPool);
        }
    }

    private static void scheduledExecutorDemo() throws InterruptedException {
        System.out.println("\n10) ScheduledExecutorService: delayed and periodic tasks");

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
        try {
            scheduler.schedule(() -> System.out.println("Delayed task executed"), 200, TimeUnit.MILLISECONDS);

            Future<?> periodic = scheduler.scheduleAtFixedRate(
                    () -> System.out.println("Periodic task tick @ " + System.currentTimeMillis()),
                    0,
                    150,
                    TimeUnit.MILLISECONDS);

            Thread.sleep(500);
            periodic.cancel(true);
        } finally {
            shutdownExecutor(scheduler);
        }
    }

    private static void completableFutureDemo() {
        System.out.println("\n11) CompletableFuture async pipeline");

        CompletableFuture<String> pipeline = CompletableFuture
                .supplyAsync(() -> "executor")
                .thenApply(word -> word + " framework")
                .thenApply(String::toUpperCase)
                .thenCompose(text -> CompletableFuture.completedFuture("Result: " + text));

        System.out.println(pipeline.join());
    }

    private static void forkJoinDemo() {
        System.out.println("\n12) ForkJoinPool with RecursiveTask (sum array)");

        int[] values = new int[20];
        for (int i = 0; i < values.length; i++) {
            values[i] = i + 1;
        }

        ForkJoinPool pool = ForkJoinPool.commonPool();
        int sum = pool.invoke(new SumTask(values, 0, values.length));
        System.out.println("ForkJoin computed sum = " + sum + " (expected 210)");
    }

    private static void shutdownExecutor(ExecutorService service) throws InterruptedException {
        service.shutdown();
        if (!service.awaitTermination(2, TimeUnit.SECONDS)) {
            service.shutdownNow();
            service.awaitTermination(1, TimeUnit.SECONDS);
        }
    }

    private static class SumTask extends RecursiveTask<Integer> {
        private static final int THRESHOLD = 5;
        private final int[] data;
        private final int start;
        private final int end;

        SumTask(int[] data, int start, int end) {
            this.data = data;
            this.start = start;
            this.end = end;
        }

        @Override
        protected Integer compute() {
            int length = end - start;
            if (length <= THRESHOLD) {
                int sum = 0;
                for (int i = start; i < end; i++) {
                    sum += data[i];
                }
                return sum;
            }

            int mid = start + length / 2;
            SumTask left = new SumTask(data, start, mid);
            SumTask right = new SumTask(data, mid, end);
            left.fork();
            int rightResult = right.compute();
            int leftResult = left.join();
            return leftResult + rightResult;
        }
    }
}
