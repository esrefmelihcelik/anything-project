package thread.scheduler;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class AdvancedControllableScheduledService {

  private final ScheduledExecutorService scheduler;
  private ScheduledFuture<?> scheduledTask;
  private final AtomicBoolean isRunning = new AtomicBoolean(false);
  private final Runnable task;
  private final long initialDelay;
  private final long period;
  private final TimeUnit timeUnit;

  public AdvancedControllableScheduledService(String name, Runnable task, long initialDelay, long period,
      TimeUnit timeUnit) {
    this.task = task;
    this.initialDelay = initialDelay;
    this.period = period;
    this.timeUnit = timeUnit;
    this.scheduler = Executors.newScheduledThreadPool(1, r -> new Thread(r, name + "-scheduled-service"));
  }

  public synchronized boolean start() {
    if (isRunning.get()) {
      System.out.println("Service is already running");
      return false;
    }
    // Wrap the user task with monitoring and error handling
    Runnable monitoredTask = () -> {
      long startTime = System.currentTimeMillis();
      try {
        task.run();
        long duration = System.currentTimeMillis() - startTime;
        System.out.printf("Task executed successfully in %d ms.", duration);
      } catch (Exception e) {
        System.err.printf("Task execution failed: %s.", e.getMessage());
        // Log the full exception in production
      }
    };
    scheduledTask = scheduler.scheduleAtFixedRate(monitoredTask, initialDelay, period, timeUnit);
    isRunning.set(true);
    System.out.println("Scheduled service started successfully");
    return true;
  }

  /**
   * Stop the scheduled service
   *
   * @param mayInterruptIfRunning whether to interrupt currently running task
   */
  public synchronized boolean stop(boolean mayInterruptIfRunning) {
    if (!isRunning.get()) {
      System.out.println("Service is not running");
      return false;
    }
    boolean cancelled = false;
    if (scheduledTask != null) {
      cancelled = scheduledTask.cancel(mayInterruptIfRunning);
      scheduledTask = null;
    }
    isRunning.set(false);
    System.out.println("Scheduled service stopped. Cancelled: " + cancelled);
    return cancelled;
  }

  /**
   * Stop without interrupting current execution
   */
  public boolean stop() {
    return stop(false);
  }

  public synchronized boolean restart() {
    System.out.println("Restarting scheduled service...");
    stop(true); // Stop and interrupt if necessary
    return start();
  }

  public boolean isRunning() {
    return isRunning.get();
  }


  public void shutdown() {
    System.out.println("Initiating shutdown...");
    stop(true);
    scheduler.shutdown();
    try {
      if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
        System.err.println("Service did not terminate gracefully, forcing shutdown...");
        scheduler.shutdownNow();
      }
    } catch (InterruptedException e) {
      scheduler.shutdownNow();
      Thread.currentThread().interrupt();
    }
    System.out.println("Shutdown completed");
  }

  // Example usage
  public static void main(String[] args) throws InterruptedException {
    // Create a simple task
    Runnable task = () -> {
      System.out.println("Business task executing at " + java.time.LocalTime.now());
      // Simulate work
      try {
        Thread.sleep(800);
      } catch (InterruptedException e) {
        System.out.println("Task was interrupted");
        Thread.currentThread().interrupt();
      }
    };
    AdvancedControllableScheduledService service = new AdvancedControllableScheduledService("TEST", task, 1, 3,
        TimeUnit.SECONDS);
    try {
      service.start();
      Thread.sleep(10000);
      service.stop();
      Thread.sleep(5000);
      service.restart();
      Thread.sleep(8000);
    } finally {
      service.shutdown();
    }
  }
}