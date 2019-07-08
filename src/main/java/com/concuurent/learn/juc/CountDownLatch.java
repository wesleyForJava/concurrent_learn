package com.concuurent.learn.juc;


import java.util.concurrent.TimeUnit;

/**
 * 在日常开发中经常会遇到需要在主线程中开启多个线程去并行执行任务，
 * 并且主线程需要等待所有子线程执行完毕后再进行汇总的场景。
 * 在CountDownLtch出现之前一般都使用线程的join（）方法来实现这一点，
 * 但是join方法不够灵活，不能够满足不同场景的需要，所以JDK开发组提供了CountDownLatch这个类，
 * 我们前面介绍的例子使用CountDownLatch会更优雅。使用CountDownLatch的代码如下
 * @author Wesley
 *
 * 2019年7月5日下午4:31:29
 * @Version 1.0
 */
public class CountDownLatch {
    /**
     * Synchronization control For CountDownLatch.
     * Uses AQS state to represent count.
     */
    private static final class Sync extends AbstractQueuedSynchronizer {
        private static final long serialVersionUID = 4982264981922014374L;

        Sync(int count) {
            setState(count);
        }

        int getCount() {
            return getState();
        }

        protected int tryAcquireShared(int acquires) {
            return (getState() == 0) ? 1 : -1;
        }
        /**
         * 首先获取当前状态值（计数器值）。代码（1）判断如果当前状态值为0则直接返回false，
         * 从而countDown(）方法直接返回：否则执行代码（2）使用CAS将计数器值减1,CAS失败则循环重试，
         * 否则如果当前计数器值为0则返回true，返回true说明是最后一个线程调用的countdown方法，
         * 那么该线程除了让计数器值减1外，还需要唤醒因调用CountDownLatch的await方法而被阻塞的线程，
         * 具体是调用AQS的doReleaseShared方法来激活阻塞的线程。这里代码（1）貌似是多余的，其实不然，
         * 之所以添加代码（1）是为了防止当计数器值为0后，其他线程又调用了countDown方法，
         * 如果没有代码（1）状态值就可能会变成负数。
         */
        protected boolean tryReleaseShared(int releases) {
            // Decrement count; signal when transition to zero
        	//循环进行CAS，直到当前线程成功完成使计数器值（状态值state）减1并更新到state
            for (;;) {
                int c = getState();
                //如果当前值为0，则直接返回（1）
                if (c == 0)
                    return false;
                //使用CAS让计数器值减1（2）
                int nextc = c-1;
                if (compareAndSetState(c, nextc))
                    return nextc == 0;
            }
        }
    }

    private final Sync sync;

    /**
     * Constructs a {@code CountDownLatch} initialized with the given count.
     * <ul>
     * <li>CountDownLatch是使用AQS实现的。
     * <li>通过下面的构造函数，你会发现，实际上是把计数器的值赋给了AQS的状态变量state，
     * <li>也就是这里使用AQS的状态值来表示计数器值。
     * @param count the number of times {@link #countDown} must be invoked
     *        before threads can pass through {@link #await}
     * @throws IllegalArgumentException if {@code count} is negative
     */
    public CountDownLatch(int count) {
        if (count < 0) throw new IllegalArgumentException("count < 0");
        this.sync = new Sync(count);
    }

    /**
     * 当线程调用CountDownLatch对象的await方法后，当前线程会被阻塞，
     * 直到下面的情况之一发生才会返回：当所有线程都调用了CountDownLatch对象的countDown方法后，
     * 也就是计数器的值为0时；其他线程调用了当前线程的interrupt（）方法中断了当前线程，
     * 当前线程就会抛出InterruptedException异常，然后返回。
     * @throws InterruptedException
     */
    public void await() throws InterruptedException {
        sync.acquireSharedInterruptibly(1);
    }

    /**
     * Causes the current thread to wait until the latch has counted down to
     * zero, unless the thread is {@linkplain Thread#interrupt interrupted},
     * or the specified waiting time elapses.
     *
     * <p>If the current count is zero then this method returns immediately
     * with the value {@code true}.
     *
     * <p>If the current count is greater than zero then the current
     * thread becomes disabled for thread scheduling purposes and lies
     * dormant until one of three things happen:
     * <ul>
     * <li>The count reaches zero due to invocations of the
     * {@link #countDown} method; or
     * <li>Some other thread {@linkplain Thread#interrupt interrupts}
     * the current thread; or
     * <li>The specified waiting time elapses.
     * </ul>
     *
     * <p>If the count reaches zero then the method returns with the
     * value {@code true}.
     *
     * <p>If the current thread:
     * <ul>
     * <li>has its interrupted status set on entry to this method; or
     * <li>is {@linkplain Thread#interrupt interrupted} while waiting,
     * </ul>
     * then {@link InterruptedException} is thrown and the current thread's
     * interrupted status is cleared.
     *
     * <p>If the specified waiting time elapses then the value {@code false}
     * is returned.  If the time is less than or equal to zero, the method
     * will not wait at all.
     *
     * @param timeout the maximum time to wait
     * @param unit the time unit of the {@code timeout} argument
     * @return {@code true} if the count reached zero and {@code false}
     *         if the waiting time elapsed before the count reached zero
     * @throws InterruptedException if the current thread is interrupted
     *         while waiting
     */
    /**
     * 线程调用了CountDownLatch对象的该方法后，当前线程会被阻塞，
     * 直到下面的情况之发生才会返回：当所有线程都调用了CountDownLatch对象的countDown方法后，
     * 也就是计数器值为0时，这时候会返回true；设置的timeout时间到了，因为超时而返回false；
     * 其他线程调用了当前线程的interrupt（）方法中断了当前线程，当前线程会出InterruptedException异常，然后返回。
     * @param timeout
     * @param unit
     * @return
     * @throws InterruptedException
     */
    public boolean await(long timeout, TimeUnit unit)
        throws InterruptedException {
        return sync.tryAcquireSharedNanos(1, unit.toNanos(timeout));
    }

    /**
     * Decrements the count of the latch, releasing all waiting threads if
     * the count reaches zero.
     *
     * <p>If the current count is greater than zero then it is decremented.
     * If the new count is zero then all waiting threads are re-enabled for
     * thread scheduling purposes.
     *
     * <p>If the current count equals zero then nothing happens.
     * <p>程调用该方法后，计数器的值递减，减后如果数器值为0则唤醒所有因调用await方而被阻塞的线程，
     * <p>否则什么都不做。下面看下countDown（）方法是如何调用AQS的方法。
     * 
     * 
     */
    public void countDown() {
    	//委托调用AQS的方法
        sync.releaseShared(1);
    }

    /**
     * Returns the current count.
     *
     * <p>This method is typically used for debugging and testing purposes.
     * <p>获取当前计数器的值，也就是AQS的state的值，一般在测试时使用该方法。下面看下代码。
     * <p>其内部还是调用了AQS的getState方法来获取state的值（计数器当前值）。
     * @return the current count
     */
    public long getCount() {
        return sync.getCount();
    }

    /**
     * Returns a string identifying this latch, as well as its state.
     * The state, in brackets, includes the String {@code "Count ="}
     * followed by the current count.
     *
     * @return a string identifying this latch, as well as its state
     */
    public String toString() {
        return super.toString() + "[Count = " + sync.getCount() + "]";
    }
    
    /**
     * 本节首先介绍了CountDownLatch的使用，相比使用join方法来实现线程间同步，
     * 前者更具有灵活性和方便性。另外还介绍了CountDownLatch原理，
     * CountDownLatch是使用AQS实现的。使用AQS的状态变量来存放计数器的值。
     * 首先在初始化CountDownLatch时设置状态值（计数器值），
     * 当多个线程调用countdown方法时实际是原子性递减AQS的状态值。
     * 当线程调用await方法后当前线程会被放入AQS的阻塞队列等待计数器为0再返回。
     * 其他线程调用countdown方法让计数器值递减，当计数器值变为0，
     * 当前线程还要调用AQS的doReleaseShared方法来激活由于调用await（）方法而被阻塞的线程。
     */
    
}

