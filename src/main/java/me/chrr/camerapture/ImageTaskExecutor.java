package me.chrr.camerapture;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadPoolExecutor.AbortPolicy;
import java.util.concurrent.atomic.AtomicInteger;

public class ImageTaskExecutor {
   private final ThreadPoolExecutor executor;

   public ImageTaskExecutor(int threads, int queueCapacity, String threadPrefix) {
      AtomicInteger counter = new AtomicInteger();
      this.executor = new ThreadPoolExecutor(threads, threads, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(queueCapacity), runnable -> {
         Thread thread = new Thread(runnable, threadPrefix + "-" + counter.incrementAndGet());
         thread.setDaemon(true);
         return thread;
      }, new AbortPolicy());
      this.executor.allowCoreThreadTimeOut(true);
   }

   public boolean trySubmit(Runnable task) {
      try {
         this.executor.execute(task);
         return true;
      } catch (RejectedExecutionException e) {
         return false;
      }
   }

   public Executor getExecutor() {
      return this.executor;
   }

   public void shutdown() {
      this.executor.shutdown();
   }
}
