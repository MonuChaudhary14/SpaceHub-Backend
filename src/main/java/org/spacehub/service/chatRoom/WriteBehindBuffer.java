package org.spacehub.service.chatRoom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class WriteBehindBuffer<T> {

  private static final Logger logger = LoggerFactory.getLogger(WriteBehindBuffer.class);

  private final String bufferName;
  private final int batchSize;
  private final Consumer<List<T>> batchConsumer;
  private final ConcurrentLinkedQueue<T> queue = new ConcurrentLinkedQueue<>();
  private final AtomicInteger currentSize = new AtomicInteger(0);
  private final AtomicBoolean isFlushing = new AtomicBoolean(false);
  private final ExecutorService workerExecutor;

  public WriteBehindBuffer(String bufferName, int batchSize, Consumer<List<T>> batchConsumer) {
    this.bufferName = bufferName;
    this.batchSize = Math.max(1, batchSize);
    this.batchConsumer = batchConsumer;
    this.workerExecutor = Executors.newSingleThreadExecutor(r -> {
      Thread t = new Thread(r, "write-behind-" + bufferName.toLowerCase());
      t.setDaemon(true);
      return t;
    });
  }

  public void enqueue(T item) {
    if (item == null) {
      return;
    }
    queue.offer(item);
    int size = currentSize.incrementAndGet();

    if (size >= batchSize && !isFlushing.get()) {
      triggerAsyncFlush();
    }
  }

  public void triggerAsyncFlush() {
    workerExecutor.submit(this::flushBatch);
  }

  public void flushBatch() {
    if (queue.isEmpty() || !isFlushing.compareAndSet(false, true)) {
      return;
    }

    try {
      List<T> batch = new ArrayList<>(batchSize);
      T item;
      while (batch.size() < batchSize && (item = queue.poll()) != null) {
        batch.add(item);
        currentSize.decrementAndGet();
      }

      if (!batch.isEmpty()) {
        long startTime = System.currentTimeMillis();
        batchConsumer.accept(batch);
        long duration = System.currentTimeMillis() - startTime;
        logger.debug("[{}] Flushed batch of {} items to database in {}ms (Remaining: {})",
          bufferName, batch.size(), duration, currentSize.get());
      }
    } catch (Exception e) {
      logger.error("[{}] Error persisting write-behind batch: {}", bufferName, e.getMessage(), e);
    } finally {
      isFlushing.set(false);
      // If items accumulated while flushing, trigger another round
      if (currentSize.get() >= batchSize) {
        triggerAsyncFlush();
      }
    }
  }

  public void flushAll() {
    while (!queue.isEmpty()) {
      List<T> batch = new ArrayList<>(batchSize);
      T item;
      while (batch.size() < batchSize && (item = queue.poll()) != null) {
        batch.add(item);
        currentSize.decrementAndGet();
      }

      if (!batch.isEmpty()) {
        try {
          batchConsumer.accept(batch);
          logger.info("[{}] Flushed remaining {} items during flushAll", bufferName, batch.size());
        } catch (Exception e) {
          logger.error("[{}] Error persisting final batch: {}", bufferName, e.getMessage(), e);
        }
      }
    }
  }

  public List<T> getPendingSnapshot() {
    return new ArrayList<>(queue);
  }

  public boolean removeIf(java.util.function.Predicate<T> filter) {
    boolean removed = queue.removeIf(filter);
    if (removed) {
      currentSize.set(queue.size());
    }
    return removed;
  }

  public int size() {
    return currentSize.get();
  }

  public boolean isEmpty() {
    return queue.isEmpty();
  }

  public void shutdown() {
    logger.info("[{}] Shutting down write-behind buffer. Draining remaining items...", bufferName);
    flushAll();
    workerExecutor.shutdown();
    try {
      if (!workerExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
        workerExecutor.shutdownNow();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      workerExecutor.shutdownNow();
    }
  }
}
