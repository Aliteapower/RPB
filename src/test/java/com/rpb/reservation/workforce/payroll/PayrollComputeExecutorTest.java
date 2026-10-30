package com.rpb.reservation.workforce.payroll;

import static org.assertj.core.api.Assertions.*;
import com.rpb.reservation.workforce.domain.WorkforceException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(15)
class PayrollComputeExecutorTest {
 @Test void preservesOrderAndBoundsActiveWorkers() throws Exception {
  try(var compute=new PayrollComputeExecutor(3)) {
   var active=new AtomicInteger();var peak=new AtomicInteger();var entered=new CountDownLatch(3);var release=new CountDownLatch(1);
   try(var callers=Executors.newSingleThreadExecutor()) {
    var result=callers.submit(()->compute.orderedMap(IntStream.range(0,90).boxed().toList(),i->{
     int current=active.incrementAndGet();peak.accumulateAndGet(current,Math::max);entered.countDown();
     try {await(release);return i*2;} finally {active.decrementAndGet();}
    }));
    try {assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();assertThat(peak.get()).isEqualTo(3);} finally {release.countDown();}
    assertThat(result.get(5,TimeUnit.SECONDS)).containsExactlyElementsOf(IntStream.range(0,90).map(i->i*2).boxed().toList());
    assertThat(active.get()).isZero();assertThat(peak.get()).isEqualTo(compute.parallelism());
   }
  }
 }
 @Test void rejectsThirdBatchWith503ThenRecovers() throws Exception {
  try(var compute=new PayrollComputeExecutor(2);var callers=Executors.newFixedThreadPool(2)) {
   var entered=new CountDownLatch(2);var release=new CountDownLatch(1);
   java.util.function.Function<Integer,Integer> blocked=i->{entered.countDown();await(release);return i;};
   var first=callers.submit(()->compute.orderedMap(List.of(1),blocked));
   var second=callers.submit(()->compute.orderedMap(List.of(2),blocked));
   try {
    assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();
    assertThatThrownBy(()->compute.orderedMap(List.of(3),i->i)).isInstanceOfSatisfying(WorkforceException.class,e->{assertThat(e.code()).isEqualTo("WORKFORCE_PAYROLL_BUSY");assertThat(e.status()).isEqualTo(503);});
   } finally {release.countDown();}
   assertThat(first.get(5,TimeUnit.SECONDS)).containsExactly(1);assertThat(second.get(5,TimeUnit.SECONDS)).containsExactly(2);
   assertThat(compute.orderedMap(List.of(3),i->i)).containsExactly(3);
  }
 }
 @Test void failureDrainsOtherChunksAndNextBatchCanRun() {
  try(var compute=new PayrollComputeExecutor(2)) {
   var entered=new CountDownLatch(2);var active=new AtomicInteger();
   assertThatThrownBy(()->compute.orderedMap(List.of(0,1),i->{
    active.incrementAndGet();entered.countDown();
    try {await(entered);if(i==0)throw new IllegalArgumentException("invalid computation");await(new CountDownLatch(1));return i;}
    finally {active.decrementAndGet();}
   })).isInstanceOf(IllegalArgumentException.class).hasMessage("invalid computation");
   assertThat(active.get()).isZero();assertThat(compute.orderedMap(List.of(4,5),i->i+1)).containsExactly(5,6);
  }
 }
 @Test void interruptedCallerDrainsWorkersAndRetainsInterruptFlag() throws Exception {
  try(var compute=new PayrollComputeExecutor(2)) {
   var entered=new CountDownLatch(2);var active=new AtomicInteger();var failure=new java.util.concurrent.atomic.AtomicReference<Throwable>();var interrupted=new java.util.concurrent.atomic.AtomicBoolean();
   var caller=new Thread(()->{try {compute.orderedMap(List.of(1,2),i->{active.incrementAndGet();entered.countDown();try{await(new CountDownLatch(1));return i;}finally{active.decrementAndGet();}});}catch(Throwable e){failure.set(e);interrupted.set(Thread.currentThread().isInterrupted());}});
   caller.start();assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();caller.interrupt();caller.join(5000);
   assertThat(caller.isAlive()).isFalse();assertThat(failure.get()).isInstanceOf(WorkforceException.class);assertThat(interrupted).isTrue();assertThat(active.get()).isZero();
   assertThat(compute.orderedMap(List.of(4),i->i)).containsExactly(4);
  }
 }
 @Test void rejectsInvalidParallelismAndCapsProductionWorkers() {
  assertThatThrownBy(()->new PayrollComputeExecutor(0)).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->new PayrollComputeExecutor(5)).isInstanceOf(IllegalArgumentException.class);
  try(var compute=new PayrollComputeExecutor()){assertThat(compute.parallelism()).isBetween(1,4);}
 }
 @Test void closeRejectsNewWork() {var compute=new PayrollComputeExecutor(1);compute.close();assertThatThrownBy(()->compute.orderedMap(List.of(1),i->i)).isInstanceOf(WorkforceException.class);}
 private static void await(CountDownLatch latch) {try{if(!latch.await(5,TimeUnit.SECONDS))throw new AssertionError("worker latch timed out");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("worker interrupted",e);}}
}
