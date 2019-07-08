package com.concuurent.learn.juc;


import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 上节介绍的CountDownLatch在解决多个线程同步方面相对于调用线程的join方法己经有了不少优化
 * 但是CountDownLatch的计数器是一次性的，也就是等到计数器值变为0，再调用CuntDownLatch的await
 * 和countdown方法都会立刻返回，这就起不到线程同步的效果了。所以为了满足计数器可以重置的需要，
 * JDK开发组提供了CyclicBarrier类，并且CyclicBarrier类的功能并不限于CountDownLatch的功能。
 * 从字面意思理解，CyclicBarrier是回环屏障的意思，它可以让一组线程全部达到一个状态后再全部同时执行。
 * 这里之所以叫作回环是因为当所有等待线程执行完毕，并重置CyclicBarrier的状态后它可以被重用。
 * 之所以叫作屏障是因为线程调用await方法后就会被阻塞，这个阻塞点就称为屏障点，等所有线程都调用了
 * await方法后，线程们就会冲破屏障，继续向下运行。
 * @author Wesley
 *
 * 2019年7月8日上午10:21:42
 * @Version 1.0
 */
public class CyclicBarrier {
    /**
     * Each use of the barrier is represented as a generation instance.
     * The generation changes whenever the barrier is tripped, or
     * is reset. There can be many generations associated with threads
     * using the barrier - due to the non-deterministic way the lock
     * may be allocated to waiting threads - but only one of these
     * can be active at a time (the one to which {@code count} applies)
     * and all the rest are either broken or tripped.
     * There need not be an active generation if there has been a break
     * but no subsequent reset.
     */
    private static class Generation {
        boolean broken = false;
    }

    /** The lock for guarding barrier entry */
    private final ReentrantLock lock = new ReentrantLock();
    /** Condition to wait on until tripped */
    private final Condition trip = lock.newCondition();
    /** The number of parties */
    private final int parties;
    /* The command to run when tripped */
    private final Runnable barrierCommand;
    /** The current generation */
    private Generation generation = new Generation();

    /**
     * Number of parties still waiting. Counts down from parties to 0
     * on each generation.  It is reset to parties on each new
     * generation or when broken.
     */
    private int count;

    /**
     * Updates state on barrier trip and wakes up everyone.
     * Called only while holding lock.
     */
    private void nextGeneration() {
        // signal completion of last generation
    	//7.唤醒条件队列里面阻塞线程
        trip.signalAll();
        // set up next generation
        //8.重置CyclicBarrier
        count = parties;
        generation = new Generation();
    }

    /**
     * Sets current barrier generation as broken and wakes up everyone.
     * Called only while holding lock.
     */
    private void breakBarrier() {
        generation.broken = true;
        count = parties;
        trip.signalAll();
    }

    /**
     * 以上是dowait方法的主干代码。当一个线程调用了dowait方法后，
     * 首先会获取独占锁lock，如果创建CyclicBarrier时传递的参数为10，那么后面9个调用线程会被阻塞。
     * 然后当前获取到锁的线程会对计数器count进行递减操作，递减后count=index=9，
     * 因为index!=0所以当前线程会执行代码（4）。如果当前线程调用的是无参数的await（）方法，
     * 则这里timed=false，所以当前线程会被放入条件变量的p的条件阻塞队列，
     * 当前线程会被挂起并释放获取的lock锁。如果调用的是有数的await法则timed=true，
     * 然后当前线程也会被放入条件变量的条件队列并释放锁资源，不同的是当前线程会在指定时间超时后自动被激活。
     * 
     * 当第一个获取锁的线程由于被阻塞释放锁后，
     * 被阻塞的9个线程中有一个会竞争到lock，然后执行与第一个程同样的操作，
     * 直到最后一个线程获取到lock锁，此时己经有9个线程被放入了条件变量trip的条件队列里面。
     * 最后count=index等于0，所以执行代码（2），如果创建CyclicBarrier时传递了任务，
     * 在其他线程被唤醒前先执行任务，任务执行完毕后再执行代码（3），唤醒其他9个线程，
     * 并重置CyclicBarrier，然后这10个线程就可以继续向下运了。
     * 
     */
    private int dowait(boolean timed, long nanos)
        throws InterruptedException, BrokenBarrierException,
               TimeoutException {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            final Generation g = generation;

            if (g.broken)
                throw new BrokenBarrierException();

            if (Thread.interrupted()) {
                breakBarrier();
                throw new InterruptedException();
            }
            //（1）如果index=0则说明所有线程都到了屏障点，此时执行初始化时传递的任务
            int index = --count;
            if (index == 0) {  // tripped
                boolean ranAction = false;
                try {
                    final Runnable command = barrierCommand;
                    //（2）执行任务
                    if (command != null)
                        command.run();
                    ranAction = true;
                    //（3）激活其他因调用await方法而被阻塞的线程，并重置CyclicBarrier
                    nextGeneration();
                    //返回
                    return 0;
                } finally {
                    if (!ranAction)
                        breakBarrier();
                }
            }

            // loop until tripped, broken, interrupted, or timed out
            //4.如果index!=0
            for (;;) {
                try {
                	//5.没有设置超时时间
                    if (!timed)
                        trip.await();
                    //6.设置了超时时间
                    else if (nanos > 0L)
                        nanos = trip.awaitNanos(nanos);
                } catch (InterruptedException ie) {
                    if (g == generation && ! g.broken) {
                        breakBarrier();
                        throw ie;
                    } else {
                        // We're about to finish waiting even if we had not
                        // been interrupted, so this interrupt is deemed to
                        // "belong" to subsequent execution.
                        Thread.currentThread().interrupt();
                    }
                }

                if (g.broken)
                    throw new BrokenBarrierException();

                if (g != generation)
                    return index;

                if (timed && nanos <= 0L) {
                    breakBarrier();
                    throw new TimeoutException();
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Creates a new {@code CyclicBarrier} that will trip when the
     * given number of parties (threads) are waiting upon it, and which
     * will execute the given barrier action when the barrier is tripped,
     * performed by the last thread entering the barrier.
     *
     * @param parties the number of threads that must invoke {@link #await}
     *        before the barrier is tripped
     * @param barrierAction the command to execute when the barrier is
     *        tripped, or {@code null} if there is no action
     * @throws IllegalArgumentException if {@code parties} is less than 1
     */
    /**
     * CyclicBanier基于独占锁实现，本质底层还是基于AQS的。parties用来记录线程个数，
     * 这里表示多少线程调用await后，所有线程才会冲破屏障继续往下运行。
     * 而count一开始等于parties，每当有线程调用await方法就递减1，
     * 当count为0时就示所有线程都到了屏障点。你可能会疑惑，
     * 为何维护parties和count两个变量，只使用count不就可以了？
     * 别忘了CycleBarier是可以被复用的，使用两个变量的原因是，
     * parties始终用来记录总的线程个数，当count计数器值变为0后，
     * 会将parties的值赋给count,从而进行复用。
     * 这两个变量是在构造CyclicBarrier对象时传递的，如下所示。
     * @param parties
     * @param barrierAction
     */
    public CyclicBarrier(int parties, Runnable barrierAction) {
        if (parties <= 0) throw new IllegalArgumentException();
        this.parties = parties;
        this.count = parties;
        //还有一个变量barrierCommand也通过构造函数传递，这是一个任务，
        //这个任务的执行时机是当所有线程都到达屏障点后。使用lock首先保证了更新计数器count的原子性。
        //另外使用lock的条件变量trip支持线程间使用await和signal操作进行同步。
         // 最后，在变量generation内部有一个量broken其用来记录当前屏障是否被打破。
         //注意，这里的broken并没有被声明为volatile的，因为是在锁内使用变量，所以不需要声明。
        this.barrierCommand = barrierAction;
    }

    /**
     * Creates a new {@code CyclicBarrier} that will trip when the
     * given number of parties (threads) are waiting upon it, and
     * does not perform a predefined action when the barrier is tripped.
     *
     * @param parties the number of threads that must invoke {@link #await}
     *        before the barrier is tripped
     * @throws IllegalArgumentException if {@code parties} is less than 1
     */
    public CyclicBarrier(int parties) {
        this(parties, null);
    }

    /**
     * Returns the number of parties required to trip this barrier.
     *
     * @return the number of parties required to trip this barrier
     */
    public int getParties() {
        return parties;
    }

    /**
     * 当前线程调用CyclicBarrier的该方法时会被阻塞，直到满足面条件之一才会返回：
     * parties个线程都调用了await（）方法，也就是线程都到了屏障点；
     * 其他线程调用了当前线程的interrupt()法中断了当前线程，
     * 则当前线程会抛出InterruptedException异常而返回；
     * 与当前屏障点关联的Generation对象的broken标志被设置为true时，
     * 会抛出BrokenBarrierException异常，然后返回。
     * 由如下代码可知，在内部调用了dowait方法。
     * 第一个参数为false则说明不设置超时时间，
     * 这时候第二个参数没有意义。
     * @return
     * @throws InterruptedException
     * @throws BrokenBarrierException
     */
    public int await() throws InterruptedException, BrokenBarrierException {
        try {
            return dowait(false, 0L);
        } catch (TimeoutException toe) {
            throw new Error(toe); // cannot happen
        }
    }

    /**
     *当前线程调用CyclicBarrier的该方法时会被阻塞，
     *直到满足下面条件之一才会返回：parties个线程都调用了await（）方法，
     *也就是线程都到了屏障点，这时候返回true；设置的超时时间到了后返回false;
     *其他线程调用当前线程的interrupt(）方法中断了当前线程，
     *则当前线程会抛出InterruptedException异常然后返回：
     *与当前屏障点关联的Generation象的broken标志被设置为true时，
     *会抛出BrokenBarrierException异常，然后返回。
     *由如下代码可知，在内部调用了dowait方法。
     *第一个参数为true则说明设置了超时时间，
     *这时候第二个参数是超时时间。
     */
    public int await(long timeout, TimeUnit unit)
        throws InterruptedException,
               BrokenBarrierException,
               TimeoutException {
        return dowait(true, unit.toNanos(timeout));
    }

    /**
     * Queries if this barrier is in a broken state.
     *
     * @return {@code true} if one or more parties broke out of this
     *         barrier due to interruption or timeout since
     *         construction or the last reset, or a barrier action
     *         failed due to an exception; {@code false} otherwise.
     */
    public boolean isBroken() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return generation.broken;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Resets the barrier to its initial state.  If any parties are
     * currently waiting at the barrier, they will return with a
     * {@link BrokenBarrierException}. Note that resets <em>after</em>
     * a breakage has occurred for other reasons can be complicated to
     * carry out; threads need to re-synchronize in some other way,
     * and choose one to perform the reset.  It may be preferable to
     * instead create a new barrier for subsequent use.
     */
    public void reset() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            breakBarrier();   // break the current generation
            nextGeneration(); // start a new generation
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the number of parties currently waiting at the barrier.
     * This method is primarily useful for debugging and assertions.
     *
     * @return the number of parties currently blocked in {@link #await}
     */
    public int getNumberWaiting() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return parties - count;
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * 本节首先通过案例说明了CyclicBarrier与CountDownLatch的不同在于，
     * 前者是可以复用的并且前者特别适合分段任务有序执行的场景。
     * 然后分析了CyclicBarrier，其通过独占锁ReentrantLock实现计数器原子性更新，
     * 并使用条件变量队列来实现线程同步。
     */
}

