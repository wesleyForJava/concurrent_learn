package com.concuurent.learn.juc;



import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;

/**
 * Semaphore信号量也是Java中的一个同步器，
 * 与CountDownLatch和CycleBarrier不同的是，
 * 它内部的计数器是递增的，并且在一开始初始化Semaphore时可以指定一个初始值，
 * 但是并不需要知道需要同步的线程个数，
 * 而是在需要同步的地方调用acquire方法时指定需要同步的线程个数。
 * @author Wesley
 *
 * 2019年7月8日下午1:50:30
 * @Version 1.0
 */
public class Semaphore implements java.io.Serializable {
    private static final long serialVersionUID = -3222578661600680210L;
    /** All mechanics via AbstractQueuedSynchronizer subclass */
    private final Sync sync;

    /**
     * Synchronization implementation for semaphore.  Uses AQS state
     * to represent permits. Subclassed into fair and nonfair
     * versions.
     */
    abstract static class Sync extends AbstractQueuedSynchronizer2 {
        private static final long serialVersionUID = 1192457210091910933L;

        Sync(int permits) {
            setState(permits);
        }

        final int getPermits() {
            return getState();
        }

        final int nonfairTryAcquireShared(int acquires) {
            for (;;) {
            	//获取当前信号量值
                int available = getState();
                //计算当前剩余值
                int remaining = available - acquires;
                //如果当前剩余值小于0或者CAS设置成功则返回。
                if (remaining < 0 ||
                    compareAndSetState(available, remaining))
                    return remaining;
                /**
                 * 如上代码先获取当前信号量值（available），然后减去需要获取的值（acquires），
                 * 得到剩余的信号量个数（remaining），如果剩余值小于0则说明当前信号量个数满足不了需求，
                 * 那么直接返回负数，这时当前线程会被放入AQS的阻塞队列而被挂起。
                 * 如果剩余值大于0,则使用CAS操作设置当前信号量值为剩余值，然后返回剩余值。
                 * 
                 * 另外，由于NonFairSync是非公平获取的，
                 * 也就是说先调用aquire方法获取信号量的线程不一定比后来者先获取到信号量。
                 * 考虑下面场景，如果线程A先调用了aquire（）方法获取信号量，但是当前信号量个数为0，
                 * 那么线程A会被放入AQS阻塞队列。过一段时间后线程C调用了release（）方释放了一个信号量，
                 * 如果当前没有其他线程获取信号量，那么线程A就会被激活，然后获取该信号量，
                 * 但是假如线程C释放信号后，线程C调用了aquire方法，那么线程C就会和线程A去竞争个信号量资源。
                 * 如果采用非公平策略，由nonfairTryAcquireShared的代码可知，线程C完全可以在线程A被激活前，
                 * 或者激活后先于线程A获取到该信号量，也就是在这种模式下阻塞线程和当前请求的线程是竞争关系，
                 * 而不遵循先来先得的策略。下面看公平性的FairSync类是如何保证公平性的
                 */
            }
        }
        /**
         * 由代码release()->sync.releaseShared(1）可知，
         * release方法每次只会对信号量值增加1,tryReleaseShared方法是无限循环，
         * 使用CAS保证了release方法对信号量递增l的原子性操作。
         * tryeeaseShared方法增加信号量值成功后会执行代码（3），
         * 即调用AQS的方法来激活因为调用aquire方法被阻塞的线程。
         */
        protected final boolean tryReleaseShared(int releases) {
            for (;;) {
            	//4.尝试获取当前信号量值 
                int current = getState();
                //5.将当前信号量值增加为releases,这里为增加1
                int next = current + releases;
                if (next < current) // overflow 移除处理
                    throw new Error("Maximum permit count exceeded");
                //6.使用CAS保证更新信号量值的原子性
                if (compareAndSetState(current, next))
                    return true;
            }
        }

        final void reducePermits(int reductions) {
            for (;;) {
                int current = getState();
                int next = current - reductions;
                if (next > current) // underflow
                    throw new Error("Permit count underflow");
                if (compareAndSetState(current, next))
                    return;
            }
        }

        final int drainPermits() {
            for (;;) {
                int current = getState();
                if (current == 0 || compareAndSetState(current, 0))
                    return current;
            }
        }
    }

    /**
     * NonFair version
     */
    static final class NonfairSync extends Sync {
        private static final long serialVersionUID = -2694183684443567898L;

        NonfairSync(int permits) {
            super(permits);
        }

        protected int tryAcquireShared(int acquires) {
            return nonfairTryAcquireShared(acquires);
        }
    }

    /**
     * Fair version
     */
    static final class FairSync extends Sync {
        private static final long serialVersionUID = 2014338818796000944L;

        FairSync(int permits) {
            super(permits);
        }
        /**
         * 可见公平性还是靠hasQueuedPredecessors这个函数来保证的。
         * 前面章节讲过，公平策略是看当前线程节点的前驱节点是否也在等待获取该资源，
         * 如果是则自己放弃获取的权限，然后当前线程会被放入AQS阻塞队列，否则就去获取。
         */
        protected int tryAcquireShared(int acquires) {
            for (;;) {
                if (hasQueuedPredecessors())
                    return -1;
                int available = getState();
                int remaining = available - acquires;
                if (remaining < 0 ||
                    compareAndSetState(available, remaining))
                    return remaining;
            }
        }
    }

    /**
     * Semaphore还是使用AQS实现的。
     * Sync只是对AQS的一个修饰，并且Sync有两个实现类，
     * 用来指定获取信号量时是否采用公平策略。
     * 例如，下面的代码在创建Semaphore时会使用一个变量指定是否使用公平策略。
     */
    public Semaphore(int permits) {
        sync = new NonfairSync(permits);
    }

    /**
     * Creates a {@code Semaphore} with the given number of
     * permits and the given fairness setting.
     *
     * @param permits the initial number of permits available.
     *        This value may be negative, in which case releases
     *        must occur before any acquires will be granted.
     * @param fair {@code true} if this semaphore will guarantee
     *        first-in first-out granting of permits under contention,
     *        else {@code false}
     */
    public Semaphore(int permits, boolean fair) {
        sync = fair ? new FairSync(permits) : new NonfairSync(permits);
    }
//    如上代码中，Semaphore默认采用非公平策略，如果需要使用公平策略则可以使用带两个参数的构造函数来构造Semaphore对象。
//    另外，如CountDownLatch构造函数传递的初始化信号量个数permits被赋给了AQS的state状变量一样，
//    这里AQS的state值也表示当前持有的信号量个数
    
    
    /**
     *当前线程调用该方法的目的是希望获取一个信号量资源。如果当前信号量个数大于0,则当前信号量的计数会减1，
     *然后该方法直接返回。否则如果当前信号量个数等于0，则当前线程会被放入AQS的阻塞队列。
     *当其他线程调用了当前线程的interrupt（）方法中断了当前线程时，则当前线程会抛出InterruptedException异常返回。
     *下面看下代码实现。
     */
    public void acquire() throws InterruptedException {
    	//传递参数为1，说明要获取一个信号量资源 见AbstractQueuedSynchronizer2
        sync.acquireSharedInterruptibly(1);
    }

    /**
     * 方法与acquire（）类，不同之处在于该方法对中断不响应，
     * 也就是当 当前线程调用了acquireUninterruptibly获取资源时（包含被阻塞后），
     * 其他线程调用了当前线程的interrupt(）方法设置了当前线程的中断标志，
     * 此时当前线程并不会抛出InterruptedException常而返回。
     */
    public void acquireUninterruptibly() {
        sync.acquireShared(1);
    }

    /**
     * Acquires a permit from this semaphore, only if one is available at the
     * time of invocation.
     *
     * <p>Acquires a permit, if one is available and returns immediately,
     * with the value {@code true},
     * reducing the number of available permits by one.
     *
     * <p>If no permit is available then this method will return
     * immediately with the value {@code false}.
     *
     * <p>Even when this semaphore has been set to use a
     * fair ordering policy, a call to {@code tryAcquire()} <em>will</em>
     * immediately acquire a permit if one is available, whether or not
     * other threads are currently waiting.
     * This &quot;barging&quot; behavior can be useful in certain
     * circumstances, even though it breaks fairness. If you want to honor
     * the fairness setting, then use
     * {@link #tryAcquire(long, TimeUnit) tryAcquire(0, TimeUnit.SECONDS) }
     * which is almost equivalent (it also detects interruption).
     *
     * @return {@code true} if a permit was acquired and {@code false}
     *         otherwise
     */
    public boolean tryAcquire() {
        return sync.nonfairTryAcquireShared(1) >= 0;
    }

    /**
     * Acquires a permit from this semaphore, if one becomes available
     * within the given waiting time and the current thread has not
     * been {@linkplain Thread#interrupt interrupted}.
     *
     * <p>Acquires a permit, if one is available and returns immediately,
     * with the value {@code true},
     * reducing the number of available permits by one.
     *
     * <p>If no permit is available then the current thread becomes
     * disabled for thread scheduling purposes and lies dormant until
     * one of three things happens:
     * <ul>
     * <li>Some other thread invokes the {@link #release} method for this
     * semaphore and the current thread is next to be assigned a permit; or
     * <li>Some other thread {@linkplain Thread#interrupt interrupts}
     * the current thread; or
     * <li>The specified waiting time elapses.
     * </ul>
     *
     * <p>If a permit is acquired then the value {@code true} is returned.
     *
     * <p>If the current thread:
     * <ul>
     * <li>has its interrupted status set on entry to this method; or
     * <li>is {@linkplain Thread#interrupt interrupted} while waiting
     * to acquire a permit,
     * </ul>
     * then {@link InterruptedException} is thrown and the current thread's
     * interrupted status is cleared.
     *
     * <p>If the specified waiting time elapses then the value {@code false}
     * is returned.  If the time is less than or equal to zero, the method
     * will not wait at all.
     *
     * @param timeout the maximum time to wait for a permit
     * @param unit the time unit of the {@code timeout} argument
     * @return {@code true} if a permit was acquired and {@code false}
     *         if the waiting time elapsed before a permit was acquired
     * @throws InterruptedException if the current thread is interrupted
     */
    public boolean tryAcquire(long timeout, TimeUnit unit)
        throws InterruptedException {
        return sync.tryAcquireSharedNanos(1, unit.toNanos(timeout));
    }

    /**
     *方法的作用是把当前Semaphore对象信号量值增加1，
     *如果当前有线程因为调用aquire方法被阻塞而被放入了AQS的阻塞队列，
     *则会根据公平策略选择一个信号量个数能被满足的线程进行激活，
     *激活的线程会尝试获取刚增加的信号量，下面看代码实现。
     */
    public void release() {
    	//1.arg=1
        sync.releaseShared(1);
        
    }

    /**
     * Acquires the given number of permits from this semaphore,
     * blocking until all are available,
     * or the thread is {@linkplain Thread#interrupt interrupted}.
     *
     * <p>Acquires the given number of permits, if they are available,
     * and returns immediately, reducing the number of available permits
     * by the given amount.
     *
     * <p>If insufficient permits are available then the current thread becomes
     * disabled for thread scheduling purposes and lies dormant until
     * one of two things happens:
     * <ul>
     * <li>Some other thread invokes one of the {@link #release() release}
     * methods for this semaphore, the current thread is next to be assigned
     * permits and the number of available permits satisfies this request; or
     * <li>Some other thread {@linkplain Thread#interrupt interrupts}
     * the current thread.
     * </ul>
     *
     * <p>If the current thread:
     * <ul>
     * <li>has its interrupted status set on entry to this method; or
     * <li>is {@linkplain Thread#interrupt interrupted} while waiting
     * for a permit,
     * </ul>
     * then {@link InterruptedException} is thrown and the current thread's
     * interrupted status is cleared.
     * Any permits that were to be assigned to this thread are instead
     * assigned to other threads trying to acquire permits, as if
     * permits had been made available by a call to {@link #release()}.
     * 该方法与acquire（）方法不同，后者只需要获取一个信号量值，而前者则获取permits个。
     * @param permits the number of permits to acquire
     * @throws InterruptedException if the current thread is interrupted
     * @throws IllegalArgumentException if {@code permits} is negative
     */
    public void acquire(int permits) throws InterruptedException {
        if (permits < 0) throw new IllegalArgumentException();
        sync.acquireSharedInterruptibly(permits);
    }

    /**
     * 该方法与acquire（ int permits）方法的不同之处在于，该方法对中断不响应。
     */
    public void acquireUninterruptibly(int permits) {
        if (permits < 0) throw new IllegalArgumentException();
        sync.acquireShared(permits);
    }

    /**
     * Acquires the given number of permits from this semaphore, only
     * if all are available at the time of invocation.
     *
     * <p>Acquires the given number of permits, if they are available, and
     * returns immediately, with the value {@code true},
     * reducing the number of available permits by the given amount.
     *
     * <p>If insufficient permits are available then this method will return
     * immediately with the value {@code false} and the number of available
     * permits is unchanged.
     *
     * <p>Even when this semaphore has been set to use a fair ordering
     * policy, a call to {@code tryAcquire} <em>will</em>
     * immediately acquire a permit if one is available, whether or
     * not other threads are currently waiting.  This
     * &quot;barging&quot; behavior can be useful in certain
     * circumstances, even though it breaks fairness. If you want to
     * honor the fairness setting, then use {@link #tryAcquire(int,
     * long, TimeUnit) tryAcquire(permits, 0, TimeUnit.SECONDS) }
     * which is almost equivalent (it also detects interruption).
     *
     * @param permits the number of permits to acquire
     * @return {@code true} if the permits were acquired and
     *         {@code false} otherwise
     * @throws IllegalArgumentException if {@code permits} is negative
     */
    public boolean tryAcquire(int permits) {
        if (permits < 0) throw new IllegalArgumentException();
        return sync.nonfairTryAcquireShared(permits) >= 0;
    }

    /**
     * Acquires the given number of permits from this semaphore, if all
     * become available within the given waiting time and the current
     * thread has not been {@linkplain Thread#interrupt interrupted}.
     *
     * <p>Acquires the given number of permits, if they are available and
     * returns immediately, with the value {@code true},
     * reducing the number of available permits by the given amount.
     *
     * <p>If insufficient permits are available then
     * the current thread becomes disabled for thread scheduling
     * purposes and lies dormant until one of three things happens:
     * <ul>
     * <li>Some other thread invokes one of the {@link #release() release}
     * methods for this semaphore, the current thread is next to be assigned
     * permits and the number of available permits satisfies this request; or
     * <li>Some other thread {@linkplain Thread#interrupt interrupts}
     * the current thread; or
     * <li>The specified waiting time elapses.
     * </ul>
     *
     * <p>If the permits are acquired then the value {@code true} is returned.
     *
     * <p>If the current thread:
     * <ul>
     * <li>has its interrupted status set on entry to this method; or
     * <li>is {@linkplain Thread#interrupt interrupted} while waiting
     * to acquire the permits,
     * </ul>
     * then {@link InterruptedException} is thrown and the current thread's
     * interrupted status is cleared.
     * Any permits that were to be assigned to this thread, are instead
     * assigned to other threads trying to acquire permits, as if
     * the permits had been made available by a call to {@link #release()}.
     *
     * <p>If the specified waiting time elapses then the value {@code false}
     * is returned.  If the time is less than or equal to zero, the method
     * will not wait at all.  Any permits that were to be assigned to this
     * thread, are instead assigned to other threads trying to acquire
     * permits, as if the permits had been made available by a call to
     * {@link #release()}.
     *
     * @param permits the number of permits to acquire
     * @param timeout the maximum time to wait for the permits
     * @param unit the time unit of the {@code timeout} argument
     * @return {@code true} if all permits were acquired and {@code false}
     *         if the waiting time elapsed before all permits were acquired
     * @throws InterruptedException if the current thread is interrupted
     * @throws IllegalArgumentException if {@code permits} is negative
     */
    public boolean tryAcquire(int permits, long timeout, TimeUnit unit)
        throws InterruptedException {
        if (permits < 0) throw new IllegalArgumentException();
        return sync.tryAcquireSharedNanos(permits, unit.toNanos(timeout));
    }

    /**
     * Releases the given number of permits, returning them to the semaphore.
     *
     * <p>Releases the given number of permits, increasing the number of
     * available permits by that amount.
     * If any threads are trying to acquire permits, then one
     * is selected and given the permits that were just released.
     * If the number of available permits satisfies that thread's request
     * then that thread is (re)enabled for thread scheduling purposes;
     * otherwise the thread will wait until sufficient permits are available.
     * If there are still permits available
     * after this thread's request has been satisfied, then those permits
     * are assigned in turn to other threads trying to acquire permits.
     *
     * <p>There is no requirement that a thread that releases a permit must
     * have acquired that permit by calling {@link Semaphore#acquire acquire}.
     * Correct usage of a semaphore is established by programming convention
     * in the application.
     * 该方法与不带参数的release方法的不同之处在于，
     * 前者每次调用会在信号量值原来的基础上增加permits，而后者每次增加1。
     * @param permits the number of permits to release
     * @throws IllegalArgumentException if {@code permits} is negative
     */
    public void release(int permits) {
        if (permits < 0) throw new IllegalArgumentException();
        sync.releaseShared(permits);
    }

    /**
     * Returns the current number of permits available in this semaphore.
     *
     * <p>This method is typically used for debugging and testing purposes.
     *
     * @return the number of permits available in this semaphore
     */
    public int availablePermits() {
        return sync.getPermits();
    }

    /**
     * Acquires and returns all permits that are immediately available.
     *
     * @return the number of permits acquired
     */
    public int drainPermits() {
        return sync.drainPermits();
    }

    /**
     * Shrinks the number of available permits by the indicated
     * reduction. This method can be useful in subclasses that use
     * semaphores to track resources that become unavailable. This
     * method differs from {@code acquire} in that it does not block
     * waiting for permits to become available.
     *
     * @param reduction the number of permits to remove
     * @throws IllegalArgumentException if {@code reduction} is negative
     */
    protected void reducePermits(int reduction) {
        if (reduction < 0) throw new IllegalArgumentException();
        sync.reducePermits(reduction);
    }

    /**
     * Returns {@code true} if this semaphore has fairness set true.
     *
     * @return {@code true} if this semaphore has fairness set true
     */
    public boolean isFair() {
        return sync instanceof FairSync;
    }

    /**
     * Queries whether any threads are waiting to acquire. Note that
     * because cancellations may occur at any time, a {@code true}
     * return does not guarantee that any other thread will ever
     * acquire.  This method is designed primarily for use in
     * monitoring of the system state.
     *
     * @return {@code true} if there may be other threads waiting to
     *         acquire the lock
     */
    public final boolean hasQueuedThreads() {
        return sync.hasQueuedThreads();
    }

    /**
     * Returns an estimate of the number of threads waiting to acquire.
     * The value is only an estimate because the number of threads may
     * change dynamically while this method traverses internal data
     * structures.  This method is designed for use in monitoring of the
     * system state, not for synchronization control.
     *
     * @return the estimated number of threads waiting for this lock
     */
    public final int getQueueLength() {
        return sync.getQueueLength();
    }

    /**
     * Returns a collection containing threads that may be waiting to acquire.
     * Because the actual set of threads may change dynamically while
     * constructing this result, the returned collection is only a best-effort
     * estimate.  The elements of the returned collection are in no particular
     * order.  This method is designed to facilitate construction of
     * subclasses that provide more extensive monitoring facilities.
     *
     * @return the collection of threads
     */
    protected Collection<Thread> getQueuedThreads() {
        return sync.getQueuedThreads();
    }

    /**
     * Returns a string identifying this semaphore, as well as its state.
     * The state, in brackets, includes the String {@code "Permits ="}
     * followed by the number of permits.
     *
     * @return a string identifying this semaphore, as well as its state
     */
    public String toString() {
        return super.toString() + "[Permits = " + sync.getPermits() + "]";
    }
    
    /**
     * 本节首先通过案例介绍了Semphore的使用方法，Semaphore完全可以达到CountDownLatch的效果，
     * 但是Semphore的计数器是不可以自动重置的，不过通过变相地改变aquire方法的参数还是可以实现CycleBanier的功能的。
     * 然后介绍了Smaphore的源码实现，Semaphore是使用AQS实现的，并且获取信号量时有公平策略和非公平策略之分。
     * 
     * 本章介绍了并发包中关于线程协作的一些重要类。
     * 首先CountDownLatch通过计数器提供了更灵活的控制，
     * 只要检测到计数器值为0，就可以往下执行，
     * 这相比使用join必须等待线程执完毕后主线程才会继续向下运行更灵活。
     * 另外，CyclicBarrier也可以达到CountDownLatch的效果，但是后者在计数器值变为0后，
     * 就不能再被复用，而前者则可以使用reset方法重置后复用，
     * 前者对同一个算法但是输入参数不同的类似场景比较适用。
     * 而Semaphore采用了信号量递增的策略，一开始并不需要关心同步的线程个数，
     * 等调用aquire方法时再指定需要同步的个数，并且提供了获取信号量的公平性策略。
     * 使用本章介绍的类会大大减少你在Java中使用wait、notify来实现线程同步的代码量，
     * 在日常开发中当需要进行线程同步时使用这些同步类会节省很多代码并且可以保证正确性。
     */
    
}
