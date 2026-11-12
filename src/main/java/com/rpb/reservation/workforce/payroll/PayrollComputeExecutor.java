package com.rpb.reservation.workforce.payroll;

import com.rpb.reservation.workforce.domain.WorkforceException;
import jakarta.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Bounded CPU-only payroll work. Workers never capture Spring transactions or access repositories. */
@Component
public final class PayrollComputeExecutor implements AutoCloseable {
 private final int parallelism;
 private final ThreadPoolExecutor workers;
 private final Semaphore batches=new Semaphore(2);

 @Autowired
 public PayrollComputeExecutor(){this(Math.max(1,Math.min(4,Runtime.getRuntime().availableProcessors())));}

 public PayrollComputeExecutor(int parallelism) {
  if(parallelism<1||parallelism>4)throw new IllegalArgumentException("payroll parallelism must be between 1 and 4");
  this.parallelism=parallelism;
  var sequence=new AtomicInteger();
  workers=new ThreadPoolExecutor(parallelism,parallelism,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(parallelism*2),r->{
   var thread=new Thread(r,"workforce-payroll-compute-"+sequence.incrementAndGet());thread.setDaemon(true);return thread;
  },new ThreadPoolExecutor.AbortPolicy());
 }

 public int parallelism(){return parallelism;}

 /** At most one chunk per worker per batch; results and caller-side reductions retain input order. */
 public <T,R> List<R> orderedMap(List<T> input,Function<? super T,? extends R> operation) {
  Objects.requireNonNull(input);Objects.requireNonNull(operation);
  if(workers.isShutdown()||!batches.tryAcquire())throw busy();
  try {
   var sources=List.copyOf(input);
   if(sources.isEmpty())return List.of();
   int count=Math.min(parallelism,sources.size());
   var batch=new Batch(count);var results=new Object[sources.size()];
   int submitted=0;
   try {
    for(int chunk=0;chunk<count;chunk++) {
     int from=chunk*sources.size()/count,to=(chunk+1)*sources.size()/count;
     workers.execute(()->{
      var thread=Thread.currentThread();
      try {
       if(batch.enter(thread))for(int i=from;i<to&&!batch.cancelled();i++)results[i]=operation.apply(sources.get(i));
      }catch(Throwable failure){batch.fail(failure,thread);}
      finally{batch.leave(thread);Thread.interrupted();batch.finished.countDown();}
     });
     submitted++;
    }
   }catch(RuntimeException | Error failure){batch.fail(failure instanceof RejectedExecutionException?busy():failure,null);}
   finally {for(int i=submitted;i<count;i++)batch.finished.countDown();}
   boolean interrupted=false;
   for(;;)try{batch.finished.await();break;}catch(InterruptedException failure){interrupted=true;batch.fail(busy(),null);}
   if(interrupted)Thread.currentThread().interrupt();
   var failure=batch.failure();
   if(failure instanceof Error error)throw error;
   if(failure instanceof RuntimeException runtime)throw runtime;
   if(failure!=null)throw new IllegalStateException("payroll computation failed",failure);
   var ordered=new ArrayList<R>(results.length);
   for(var result:results){@SuppressWarnings("unchecked") R value=(R)result;ordered.add(value);}
   return List.copyOf(ordered);
  }finally{batches.release();}
 }

 private static WorkforceException busy(){return new WorkforceException("WORKFORCE_PAYROLL_BUSY",503);}

 /** Graceful close retains submitted tasks so callers can always drain their bounded batches. */
 @Override @PreDestroy public void close(){workers.shutdown();}

 private static final class Batch {
  private final CountDownLatch finished;
  private final Set<Thread> running=new HashSet<>();
  private volatile boolean cancelled;
  private Throwable failure;
  private Batch(int chunks){finished=new CountDownLatch(chunks);}
  private synchronized boolean enter(Thread thread){if(cancelled)return false;running.add(thread);return true;}
  private synchronized void leave(Thread thread){running.remove(thread);}
  private boolean cancelled(){return cancelled;}
  private synchronized Throwable failure(){return failure;}
  private synchronized void fail(Throwable error,Thread current){
   if(failure==null)failure=error;
   cancelled=true;
   for(var thread:running)if(thread!=current)thread.interrupt();
  }
 }
}
