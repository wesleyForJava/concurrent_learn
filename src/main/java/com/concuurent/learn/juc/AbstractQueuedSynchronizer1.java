package com.concuurent.learn.juc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.AbstractOwnableSynchronizer;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;

import sun.misc.Unsafe;

public abstract class AbstractQueuedSynchronizer1
extends AbstractOwnableSynchronizer
implements java.io.Serializable {

private static final long serialVersionUID = 7373984972572414691L;

/**
 * Creates a new {@code AbstractQueuedSynchronizer} instance
 * with initial synchronization state of zero.
 */
protected AbstractQueuedSynchronizer1() { }

static final class Node {
    /** Marker to indicate a node is waiting in shared mode */
	//Node节点内部的SHARED用来标记该线程是获取共享资源时被阻塞挂起后放入AQS队列的，
    static final Node SHARED = new Node();
    /** Marker to indicate a node is waiting in exclusive mode */
    //EXCLUSIVE用来标记线程是获取独占资源时被挂起后放入AQS队列的；
    static final Node EXCLUSIVE = null;

    /** waitStatus value to indicate thread has cancelled */
    //可以为CANCELLED(线程被取消了)
    static final int CANCELLED =  1;
    /** waitStatus value to indicate successor's thread needs unparking */
    //SIGNAL(线程需要被唤醒)、
    static final int SIGNAL    = -1;
    /** waitStatus value to indicate thread is waiting on condition */
    //CONDITION(线程在条件队列里面等待〉、
    static final int CONDITION = -2;
    /**
     * waitStatus value to indicate the next acquireShared should
     * unconditionally propagate
     */
    //PROPAGATE(释放共享资源时需要通知其他节点〕；
    static final int PROPAGATE = -3;

    /**
     * Status field, taking on only the values:
     *   SIGNAL:     The successor of this node is (or will soon be)
     *               blocked (via park), so the current node must
     *               unpark its successor when it releases or
     *               cancels. To avoid races, acquire methods must
     *               first indicate they need a signal,
     *               then retry the atomic acquire, and then,
     *               on failure, block.
     *   CANCELLED:  This node is cancelled due to timeout or interrupt.
     *               Nodes never leave this state. In particular,
     *               a thread with cancelled node never again blocks.
     *   CONDITION:  This node is currently on a condition queue.
     *               It will not be used as a sync queue node
     *               until transferred, at which time the status
     *               will be set to 0. (Use of this value here has
     *               nothing to do with the other uses of the
     *               field, but simplifies mechanics.)
     *   PROPAGATE:  A releaseShared should be propagated to other
     *               nodes. This is set (for head node only) in
     *               doReleaseShared to ensure propagation
     *               continues, even if other operations have
     *               since intervened.
     *   0:          None of the above
     *
     * The values are arranged numerically to simplify use.
     * Non-negative values mean that a node doesn't need to
     * signal. So, most code doesn't need to check for particular
     * values, just for sign.
     *
     * The field is initialized to 0 for normal sync nodes, and
     * CONDITION for condition nodes.  It is modified using CAS
     * (or when possible, unconditional volatile writes).
     */
    //waitStatus记录当前线程等待状态
    volatile int waitStatus;

    /**
     * Link to predecessor node that current node/thread relies on
     * for checking waitStatus. Assigned during enqueuing, and nulled
     * out (for sake of GC) only upon dequeuing.  Also, upon
     * cancellation of a predecessor, we short-circuit while
     * finding a non-cancelled one, which will always exist
     * because the head node is never cancelled: A node becomes
     * head only as a result of successful acquire. A
     * cancelled thread never succeeds in acquiring, and a thread only
     * cancels itself, not any other node.
     */
    //prev记录当前节点的前驱节点，
    volatile Node prev;

    /**
     * Link to the successor node that the current node/thread
     * unparks upon release. Assigned during enqueuing, adjusted
     * when bypassing cancelled predecessors, and nulled out (for
     * sake of GC) when dequeued.  The enq operation does not
     * assign next field of a predecessor until after attachment,
     * so seeing a null next field does not necessarily mean that
     * node is at end of queue. However, if a next field appears
     * to be null, we can scan prev's from the tail to
     * double-check.  The next field of cancelled nodes is set to
     * point to the node itself instead of null, to make life
     * easier for isOnSyncQueue.
     */
    //next记录当前节点的后继节点。
    volatile Node next;

    /**
     * The thread that enqueued this node.  Initialized on
     * construction and nulled out after use.
     */
    volatile Thread thread;

    /**
     * Link to next node waiting on condition, or the special
     * value SHARED.  Because condition queues are accessed only
     * when holding in exclusive mode, we just need a simple
     * linked queue to hold nodes while they are waiting on
     * conditions. They are then transferred to the queue to
     * re-acquire. And because conditions can only be exclusive,
     * we save a field by using special value to indicate shared
     * mode.
     */
    Node nextWaiter;

    /**
     * Returns true if node is waiting in shared mode.
     */
    final boolean isShared() {
        return nextWaiter == SHARED;
    }

    /**
     * Returns previous node, or throws NullPointerException if null.
     * Use when predecessor cannot be null.  The null check could
     * be elided, but is present to help the VM.
     *
     * @return the predecessor of this node
     */
    final Node predecessor() throws NullPointerException {
        Node p = prev;
        if (p == null)
            throw new NullPointerException();
        else
            return p;
    }

    Node() {    // Used to establish initial head or SHARED marker
    }

    Node(Thread thread, Node mode) {     // Used by addWaiter
        this.nextWaiter = mode;
        this.thread = thread;
    }

    Node(Thread thread, int waitStatus) { // Used by Condition
        this.waitStatus = waitStatus;
        this.thread = thread;
    }
}

/**
 * Head of the wait queue, lazily initialized.  Except for
 * initialization, it is modified only via method setHead.  Note:
 * If head exists, its waitStatus is guaranteed not to be
 * CANCELLED.
 */
private transient volatile Node head;

/**
 * Tail of the wait queue, lazily initialized.  Modified only via
 * method enq to add new wait node.
 */
private transient volatile Node tail;

/**
 * The synchronization state.
 */
//维持一个单一信息状态的state
private volatile int state;

/**
 * Returns the current value of synchronization state.
 * This operation has memory semantics of a {@code volatile} read.
 * @return current state value
 */
protected final int getState() {
    return state;
}

/**
 * Sets the value of synchronization state.
 * This operation has memory semantics of a {@code volatile} write.
 * @param newState the new state value
 */
protected final void setState(int newState) {
    state = newState;
}

/**
 * Atomically sets synchronization state to the given updated
 * value if the current state value equals the expected value.
 * This operation has memory semantics of a {@code volatile} read
 * and write.
 *
 * @param expect the expected value
 * @param update the new value
 * @return {@code true} if successful. False return indicates that the actual
 *         value was not equal to the expected value.
 */
//这个修改state的值
protected final boolean compareAndSetState(int expect, int update) {
    // See below for intrinsics setup to support this
    return unsafe.compareAndSwapInt(this, stateOffset, expect, update);
}

// Queuing utilities

/**
 * The number of nanoseconds for which it is faster to spin
 * rather than to use timed park. A rough estimate suffices
 * to improve responsiveness with very short timeouts.
 */
static final long spinForTimeoutThreshold = 1000L;

/**
 * Inserts node into queue, initializing if necessary. See picture above.
 * @param node the node to insert
 * @return node's predecessor
 */
//最后，我们来看看如何维护AQS提供的队列，主要看入队操作。
//入队操作：当一个线程获取锁失败后该线程会被转换为Node节点，
//然后就会使用enq(final Node node)方法将该节点插入到AQS的阻塞队列。
//下面结合代码和节点图(见图6-2)来讲解入队的过程。如上代码在第一次循环中，
//当要在AQS队列尾部插入元素时，AQS队列状态如图6-2(default)所示。
//也就是队列头、尾节点都指null；
//当执行代码(1)后节点t指向了尾部节点，这时候队列状态如图6-2中(I)所示。
//这时候t为null，故执行代码(2)，使用CAS算法设置一个哨兵节点为头节点，如果CAS设置成功，
//则让尾部节点也指向哨兵节点，这时候队列状态如图6-2中(II)所示。
//到现在为止只插入了一个哨兵节点，
//还需要插入node节点，所以在第二次循环后执行到代码(1)，这时候队列状态如图6-2(III)所示；
//然后执行代码(3)设置node的前驱节点为尾部节点，这时候队列状态如图6-2中(IV)所示：
//然后通过CAS算法设置node节点为尾部节点，CAS成功后队列状态如图6-2中(V)所示：
//CAS成功后再设置原来的尾部节点的后驱节点为node这时候就完成了双向链表的插入，
//此时队列状态如图6-2中(VI)示。 书本127页中的图
private Node enq(final Node node) {
    for (;;) {
        Node t = tail; //1
        if (t == null) { // Must initialize
            if (compareAndSetHead(new Node())) //2
                tail = head;
        } else {
            node.prev = t; //3
            if (compareAndSetTail(t, node)) {//4
                t.next = node;
                return t;
            }
        }
    }
}

/**
 * Creates and enqueues node for current thread and given mode.
 *
 * @param mode Node.EXCLUSIVE for exclusive, Node.SHARED for shared
 * @return the new node
 */
private Node addWaiter(Node mode) {
    Node node = new Node(Thread.currentThread(), mode);
    // Try the fast path of enq; backup to full enq on failure
    Node pred = tail;
    if (pred != null) {
        node.prev = pred;
        if (compareAndSetTail(pred, node)) {
            pred.next = node;
            return node;
        }
    }
    enq(node);
    return node;
}

/**
 * Sets head of queue to be node, thus dequeuing. Called only by
 * acquire methods.  Also nulls out unused fields for sake of GC
 * and to suppress unnecessary signals and traversals.
 *
 * @param node the node
 */
private void setHead(Node node) {
    head = node;
    node.thread = null;
    node.prev = null;
}

/**
 * Wakes up node's successor, if one exists.
 *
 * @param node the node
 */
private void unparkSuccessor(Node node) {
    /*
     * If status is negative (i.e., possibly needing signal) try
     * to clear in anticipation of signalling.  It is OK if this
     * fails or if status is changed by waiting thread.
     */
    int ws = node.waitStatus;
    if (ws < 0)
        compareAndSetWaitStatus(node, ws, 0);

    /*
     * Thread to unpark is held in successor, which is normally
     * just the next node.  But if cancelled or apparently null,
     * traverse backwards from tail to find the actual
     * non-cancelled successor.
     */
    Node s = node.next;
    if (s == null || s.waitStatus > 0) {
        s = null;
        for (Node t = tail; t != null && t != node; t = t.prev)
            if (t.waitStatus <= 0)
                s = t;
    }
    if (s != null)
        LockSupport.unpark(s.thread);
}

/**
 * Release action for shared mode -- signals successor and ensures
 * propagation. (Note: For exclusive mode, release just amounts
 * to calling unparkSuccessor of head if it needs signal.)
 */
private void doReleaseShared() {
    /*
     * Ensure that a release propagates, even if there are other
     * in-progress acquires/releases.  This proceeds in the usual
     * way of trying to unparkSuccessor of head if it needs
     * signal. But if it does not, status is set to PROPAGATE to
     * ensure that upon release, propagation continues.
     * Additionally, we must loop in case a new node is added
     * while we are doing this. Also, unlike other uses of
     * unparkSuccessor, we need to know if CAS to reset status
     * fails, if so rechecking.
     */
    for (;;) {
        Node h = head;
        if (h != null && h != tail) {
            int ws = h.waitStatus;
            if (ws == Node.SIGNAL) {
                if (!compareAndSetWaitStatus(h, Node.SIGNAL, 0))
                    continue;            // loop to recheck cases
                unparkSuccessor(h);
            }
            else if (ws == 0 &&
                     !compareAndSetWaitStatus(h, 0, Node.PROPAGATE))
                continue;                // loop on failed CAS
        }
        if (h == head)                   // loop if head changed
            break;
    }
}

/**
 * Sets head of queue, and checks if successor may be waiting
 * in shared mode, if so propagating if either propagate > 0 or
 * PROPAGATE status was set.
 *
 * @param node the node
 * @param propagate the return value from a tryAcquireShared
 */
private void setHeadAndPropagate(Node node, int propagate) {
    Node h = head; // Record old head for check below
    setHead(node);
    /*
     * Try to signal next queued node if:
     *   Propagation was indicated by caller,
     *     or was recorded (as h.waitStatus either before
     *     or after setHead) by a previous operation
     *     (note: this uses sign-check of waitStatus because
     *      PROPAGATE status may transition to SIGNAL.)
     * and
     *   The next node is waiting in shared mode,
     *     or we don't know, because it appears null
     *
     * The conservatism in both of these checks may cause
     * unnecessary wake-ups, but only when there are multiple
     * racing acquires/releases, so most need signals now or soon
     * anyway.
     */
    if (propagate > 0 || h == null || h.waitStatus < 0 ||
        (h = head) == null || h.waitStatus < 0) {
        Node s = node.next;
        if (s == null || s.isShared())
            doReleaseShared();
    }
}

// Utilities for various versions of acquire

/**
 * Cancels an ongoing attempt to acquire.
 *
 * @param node the node
 */
private void cancelAcquire(Node node) {
    // Ignore if node doesn't exist
    if (node == null)
        return;

    node.thread = null;

    // Skip cancelled predecessors
    Node pred = node.prev;
    while (pred.waitStatus > 0)
        node.prev = pred = pred.prev;

    // predNext is the apparent node to unsplice. CASes below will
    // fail if not, in which case, we lost race vs another cancel
    // or signal, so no further action is necessary.
    Node predNext = pred.next;

    // Can use unconditional write instead of CAS here.
    // After this atomic step, other Nodes can skip past us.
    // Before, we are free of interference from other threads.
    node.waitStatus = Node.CANCELLED;

    // If we are the tail, remove ourselves.
    if (node == tail && compareAndSetTail(node, pred)) {
        compareAndSetNext(pred, predNext, null);
    } else {
        // If successor needs signal, try to set pred's next-link
        // so it will get one. Otherwise wake it up to propagate.
        int ws;
        if (pred != head &&
            ((ws = pred.waitStatus) == Node.SIGNAL ||
             (ws <= 0 && compareAndSetWaitStatus(pred, ws, Node.SIGNAL))) &&
            pred.thread != null) {
            Node next = node.next;
            if (next != null && next.waitStatus <= 0)
                compareAndSetNext(pred, predNext, next);
        } else {
            unparkSuccessor(node);
        }

        node.next = node; // help GC
    }
}

/**
 * Checks and updates status for a node that failed to acquire.
 * Returns true if thread should block. This is the main signal
 * control in all acquire loops.  Requires that pred == node.prev.
 *
 * @param pred node's predecessor holding status
 * @param node the node
 * @return {@code true} if thread should block
 */
private static boolean shouldParkAfterFailedAcquire(Node pred, Node node) {
    int ws = pred.waitStatus;
    if (ws == Node.SIGNAL)
        /*
         * This node has already set status asking a release
         * to signal it, so it can safely park.
         */
        return true;
    if (ws > 0) {
        /*
         * Predecessor was cancelled. Skip over predecessors and
         * indicate retry.
         */
        do {
            node.prev = pred = pred.prev;
        } while (pred.waitStatus > 0);
        pred.next = node;
    } else {
        /*
         * waitStatus must be 0 or PROPAGATE.  Indicate that we
         * need a signal, but don't park yet.  Caller will need to
         * retry to make sure it cannot acquire before parking.
         */
        compareAndSetWaitStatus(pred, ws, Node.SIGNAL);
    }
    return false;
}

/**
 * Convenience method to interrupt current thread.
 */
static void selfInterrupt() {
    Thread.currentThread().interrupt();
}

/**
 * Convenience method to park and then check if interrupted
 *
 * @return {@code true} if interrupted
 */
private final boolean parkAndCheckInterrupt() {
    LockSupport.park(this);
    return Thread.interrupted();
}

/*
 * Various flavors of acquire, varying in exclusive/shared and
 * control modes.  Each is mostly the same, but annoyingly
 * different.  Only a little bit of factoring is possible due to
 * interactions of exception mechanics (including ensuring that we
 * cancel if tryAcquire throws exception) and other control, at
 * least not without hurting performance too much.
 */

/**
 * Acquires in exclusive uninterruptible mode for thread already in
 * queue. Used by condition wait methods as well as acquire.
 *
 * @param node the node
 * @param arg the acquire argument
 * @return {@code true} if interrupted while waiting
 */
final boolean acquireQueued(final Node node, int arg) {
    boolean failed = true;
    try {
        boolean interrupted = false;
        for (;;) {
            final Node p = node.predecessor();
            if (p == head && tryAcquire(arg)) {
                setHead(node);
                p.next = null; // help GC
                failed = false;
                return interrupted;
            }
            if (shouldParkAfterFailedAcquire(p, node) &&
                parkAndCheckInterrupt())
                interrupted = true;
        }
    } finally {
        if (failed)
            cancelAcquire(node);
    }
}

/**
 * Acquires in exclusive interruptible mode.
 * @param arg the acquire argument
 */
private void doAcquireInterruptibly(int arg)
    throws InterruptedException {
    final Node node = addWaiter(Node.EXCLUSIVE);
    boolean failed = true;
    try {
        for (;;) {
            final Node p = node.predecessor();
            if (p == head && tryAcquire(arg)) {
                setHead(node);
                p.next = null; // help GC
                failed = false;
                return;
            }
            if (shouldParkAfterFailedAcquire(p, node) &&
                parkAndCheckInterrupt())
                throw new InterruptedException();
        }
    } finally {
        if (failed)
            cancelAcquire(node);
    }
}

/**
 * Acquires in exclusive timed mode.
 *
 * @param arg the acquire argument
 * @param nanosTimeout max wait time
 * @return {@code true} if acquired
 */
private boolean doAcquireNanos(int arg, long nanosTimeout)
        throws InterruptedException {
    if (nanosTimeout <= 0L)
        return false;
    final long deadline = System.nanoTime() + nanosTimeout;
    final Node node = addWaiter(Node.EXCLUSIVE);
    boolean failed = true;
    try {
        for (;;) {
            final Node p = node.predecessor();
            if (p == head && tryAcquire(arg)) {
                setHead(node);
                p.next = null; // help GC
                failed = false;
                return true;
            }
            nanosTimeout = deadline - System.nanoTime();
            if (nanosTimeout <= 0L)
                return false;
            if (shouldParkAfterFailedAcquire(p, node) &&
                nanosTimeout > spinForTimeoutThreshold)
                LockSupport.parkNanos(this, nanosTimeout);
            if (Thread.interrupted())
                throw new InterruptedException();
        }
    } finally {
        if (failed)
            cancelAcquire(node);
    }
}

/**
 * Acquires in shared uninterruptible mode.
 * @param arg the acquire argument
 */
private void doAcquireShared(int arg) {
    final Node node = addWaiter(Node.SHARED);
    boolean failed = true;
    try {
        boolean interrupted = false;
        for (;;) {
            final Node p = node.predecessor();
            if (p == head) {
                int r = tryAcquireShared(arg);
                if (r >= 0) {
                    setHeadAndPropagate(node, r);
                    p.next = null; // help GC
                    if (interrupted)
                        selfInterrupt();
                    failed = false;
                    return;
                }
            }
            if (shouldParkAfterFailedAcquire(p, node) &&
                parkAndCheckInterrupt())
                interrupted = true;
        }
    } finally {
        if (failed)
            cancelAcquire(node);
    }
}

/**
 * Acquires in shared interruptible mode.
 * @param arg the acquire argument
 */
private void doAcquireSharedInterruptibly(int arg)
    throws InterruptedException {
    final Node node = addWaiter(Node.SHARED);
    boolean failed = true;
    try {
        for (;;) {
            final Node p = node.predecessor();
            if (p == head) {
                int r = tryAcquireShared(arg);
                if (r >= 0) {
                    setHeadAndPropagate(node, r);
                    p.next = null; // help GC
                    failed = false;
                    return;
                }
            }
            if (shouldParkAfterFailedAcquire(p, node) &&
                parkAndCheckInterrupt())
                throw new InterruptedException();
        }
    } finally {
        if (failed)
            cancelAcquire(node);
    }
}

/**
 * Acquires in shared timed mode.
 *
 * @param arg the acquire argument
 * @param nanosTimeout max wait time
 * @return {@code true} if acquired
 */
private boolean doAcquireSharedNanos(int arg, long nanosTimeout)
        throws InterruptedException {
    if (nanosTimeout <= 0L)
        return false;
    final long deadline = System.nanoTime() + nanosTimeout;
    final Node node = addWaiter(Node.SHARED);
    boolean failed = true;
    try {
        for (;;) {
            final Node p = node.predecessor();
            if (p == head) {
                int r = tryAcquireShared(arg);
                if (r >= 0) {
                    setHeadAndPropagate(node, r);
                    p.next = null; // help GC
                    failed = false;
                    return true;
                }
            }
            nanosTimeout = deadline - System.nanoTime();
            if (nanosTimeout <= 0L)
                return false;
            if (shouldParkAfterFailedAcquire(p, node) &&
                nanosTimeout > spinForTimeoutThreshold)
                LockSupport.parkNanos(this, nanosTimeout);
            if (Thread.interrupted())
                throw new InterruptedException();
        }
    } finally {
        if (failed)
            cancelAcquire(node);
    }
}

// Main exported methods

/**
 * Attempts to acquire in exclusive mode. This method should query
 * if the state of the object permits it to be acquired in the
 * exclusive mode, and if so to acquire it.
 *
 * <p>This method is always invoked by the thread performing
 * acquire.  If this method reports failure, the acquire method
 * may queue the thread, if it is not already queued, until it is
 * signalled by a release from some other thread. This can be used
 * to implement method {@link Lock#tryLock()}.
 *
 * <p>The default
 * implementation throws {@link UnsupportedOperationException}.
 *
 * @param arg the acquire argument. This value is always the one
 *        passed to an acquire method, or is the value saved on entry
 *        to a condition wait.  The value is otherwise uninterpreted
 *        and can represent anything you like.
 * @return {@code true} if successful. Upon success, this object has
 *         been acquired.
 * @throws IllegalMonitorStateException if acquiring would place this
 *         synchronizer in an illegal state. This exception must be
 *         thrown in a consistent fashion for synchronization to work
 *         correctly.
 * @throws UnsupportedOperationException if exclusive mode is not supported
 */
protected boolean tryAcquire(int arg) {
    throw new UnsupportedOperationException();
}

/**
 * Attempts to set the state to reflect a release in exclusive
 * mode.
 *
 * <p>This method is always invoked by the thread performing release.
 *
 * <p>The default implementation throws
 * {@link UnsupportedOperationException}.
 *
 * @param arg the release argument. This value is always the one
 *        passed to a release method, or the current state value upon
 *        entry to a condition wait.  The value is otherwise
 *        uninterpreted and can represent anything you like.
 * @return {@code true} if this object is now in a fully released
 *         state, so that any waiting threads may attempt to acquire;
 *         and {@code false} otherwise.
 * @throws IllegalMonitorStateException if releasing would place this
 *         synchronizer in an illegal state. This exception must be
 *         thrown in a consistent fashion for synchronization to work
 *         correctly.
 * @throws UnsupportedOperationException if exclusive mode is not supported
 */
protected boolean tryRelease(int arg) {
    throw new UnsupportedOperationException();
}

/**
 * Attempts to acquire in shared mode. This method should query if
 * the state of the object permits it to be acquired in the shared
 * mode, and if so to acquire it.
 *
 * <p>This method is always invoked by the thread performing
 * acquire.  If this method reports failure, the acquire method
 * may queue the thread, if it is not already queued, until it is
 * signalled by a release from some other thread.
 *
 * <p>The default implementation throws {@link
 * UnsupportedOperationException}.
 *
 * @param arg the acquire argument. This value is always the one
 *        passed to an acquire method, or is the value saved on entry
 *        to a condition wait.  The value is otherwise uninterpreted
 *        and can represent anything you like.
 * @return a negative value on failure; zero if acquisition in shared
 *         mode succeeded but no subsequent shared-mode acquire can
 *         succeed; and a positive value if acquisition in shared
 *         mode succeeded and subsequent shared-mode acquires might
 *         also succeed, in which case a subsequent waiting thread
 *         must check availability. (Support for three different
 *         return values enables this method to be used in contexts
 *         where acquires only sometimes act exclusively.)  Upon
 *         success, this object has been acquired.
 * @throws IllegalMonitorStateException if acquiring would place this
 *         synchronizer in an illegal state. This exception must be
 *         thrown in a consistent fashion for synchronization to work
 *         correctly.
 * @throws UnsupportedOperationException if shared mode is not supported
 */
protected int tryAcquireShared(int arg) {
    throw new UnsupportedOperationException();
}

/**
 * Attempts to set the state to reflect a release in shared mode.
 *
 * <p>This method is always invoked by the thread performing release.
 *
 * <p>The default implementation throws
 * {@link UnsupportedOperationException}.
 *
 * @param arg the release argument. This value is always the one
 *        passed to a release method, or the current state value upon
 *        entry to a condition wait.  The value is otherwise
 *        uninterpreted and can represent anything you like.
 * @return {@code true} if this release of shared mode may permit a
 *         waiting acquire (shared or exclusive) to succeed; and
 *         {@code false} otherwise
 * @throws IllegalMonitorStateException if releasing would place this
 *         synchronizer in an illegal state. This exception must be
 *         thrown in a consistent fashion for synchronization to work
 *         correctly.
 * @throws UnsupportedOperationException if shared mode is not supported
 */
protected boolean tryReleaseShared(int arg) {
    throw new UnsupportedOperationException();
}

/**
 * Returns {@code true} if synchronization is held exclusively with
 * respect to the current (calling) thread.  This method is invoked
 * upon each call to a non-waiting {@link ConditionObject} method.
 * (Waiting methods instead invoke {@link #release}.)
 *
 * <p>The default implementation throws {@link
 * UnsupportedOperationException}. This method is invoked
 * internally only within {@link ConditionObject} methods, so need
 * not be defined if conditions are not used.
 *
 * @return {@code true} if synchronization is held exclusively;
 *         {@code false} otherwise
 * @throws UnsupportedOperationException if conditions are not supported
 */
protected boolean isHeldExclusively() {
    throw new UnsupportedOperationException();
}

/**
 * Acquires in exclusive mode, ignoring interrupts.  Implemented
 * by invoking at least once {@link #tryAcquire},
 * returning on success.  Otherwise the thread is queued, possibly
 * repeatedly blocking and unblocking, invoking {@link
 * #tryAcquire} until success.  This method can be used
 * to implement method {@link Lock#lock}.
 *
 * @param arg the acquire argument.  This value is conveyed to
 *        {@link #tryAcquire} but is otherwise uninterpreted and
 *        can represent anything you like.
 */
//在独占方式下，获取与释放资源的流如下：
//(1)当一个线程调用acquire(int arg)方法获取独占资源时，
//会首先使用tryAcquire方法尝试获取资源，
//具体是设置状态变量state的值，成功则直接回，
//失败则将当前线程封装为类型为Node.EXLUSIVE的Node节点后插入到AQS阻塞队列的尾部，
//并调用LockSupport.park(this)方法挂起自己
public final void acquire(int arg) {
	//3.调用ReentrantLock重写的tryAcquire()方法
    if (!tryAcquire(arg) &&
    		//tryAcquire(arg)返回false会把当前线程放入AQS阻塞队列
        acquireQueued(addWaiter(Node.EXCLUSIVE), arg))
        selfInterrupt();
}

/**
 * Acquires in exclusive mode, aborting if interrupted.
 * Implemented by first checking interrupt status, then invoking
 * at least once {@link #tryAcquire}, returning on
 * success.  Otherwise the thread is queued, possibly repeatedly
 * blocking and unblocking, invoking {@link #tryAcquire}
 * until success or the thread is interrupted.  This method can be
 * used to implement method {@link Lock#lockInterruptibly}.
 *
 * @param arg the acquire argument.  This value is conveyed to
 *        {@link #tryAcquire} but is otherwise uninterpreted and
 *        can represent anything you like.
 * @throws InterruptedException if the current thread is interrupted
 */
public final void acquireInterruptibly(int arg)
        throws InterruptedException {
	//如果当前线程被中断，则直接抛出异常
    if (Thread.interrupted())
        throw new InterruptedException();
    //尝试获取资源
    if (!tryAcquire(arg))
    	//调用AQS可被中断的方法
        doAcquireInterruptibly(arg);
}

/**
 * Attempts to acquire in exclusive mode, aborting if interrupted,
 * and failing if the given timeout elapses.  Implemented by first
 * checking interrupt status, then invoking at least once {@link
 * #tryAcquire}, returning on success.  Otherwise, the thread is
 * queued, possibly repeatedly blocking and unblocking, invoking
 * {@link #tryAcquire} until success or the thread is interrupted
 * or the timeout elapses.  This method can be used to implement
 * method {@link Lock#tryLock(long, TimeUnit)}.
 *
 * @param arg the acquire argument.  This value is conveyed to
 *        {@link #tryAcquire} but is otherwise uninterpreted and
 *        can represent anything you like.
 * @param nanosTimeout the maximum number of nanoseconds to wait
 * @return {@code true} if acquired; {@code false} if timed out
 * @throws InterruptedException if the current thread is interrupted
 */
public final boolean tryAcquireNanos(int arg, long nanosTimeout)
        throws InterruptedException {
    if (Thread.interrupted())
        throw new InterruptedException();
    return tryAcquire(arg) ||
        doAcquireNanos(arg, nanosTimeout);
}

/**
 * Releases in exclusive mode.  Implemented by unblocking one or
 * more threads if {@link #tryRelease} returns true.
 * This method can be used to implement method {@link Lock#unlock}.
 *
 * @param arg the release argument.  This value is conveyed to
 *        {@link #tryRelease} but is otherwise uninterpreted and
 *        can represent anything you like.
 * @return the value returned from {@link #tryRelease}
 */
//(2)当一个线程调用release(int arg)方法时会尝试使用tryRelease操作释放资源，
//这里是设置状态变量state的值,然后调用LockSupport.unpark(thread)方法激活AQS列里面被阻塞的一个线程(thread)。
//被激活的线程则使用tryAcquire尝试，看当前状态变量state的值是否能满足自己的需要，满足则该线程被激活，然后继续向下运行，
//否则还是会被放入AQS队列并被挂起。
//要注意的是，AQS类并没有提供可用的tryAcquire和tryRelease方法，
//正如AQS是锁阻塞和同步器的基础框架一样，
//tryAcquire和tryRelease需要由具体的子类来实现。
//子类在实现tryAcquire和tryRelease时要根据具体场景使用CAS算法尝试修改state状态值，
//成功则返回true，否则返回false。
//子类还需要定义，在调用acquire和release法时state状态值的增减代表什么含义。
//比如继承自AQS实现的独占锁ReentrantLock，义当status为0时表示锁空闲，为l时表示锁己经被占用。在重写1Acquire时，
//在内部需要使用CAS算法查看当前state是否为0，如果为0则使用CAS设置为1，并设置当前锁的持有者为当前线程，而后返回true
//如果CAS失败则返回false。比如继承自AQS实现的独占锁在实现tryRelease时，
//在内部要使用CAS算法把当前state的值从1修改为0，并设置当前锁的持有者为null，然后返回true，如果CAS失败则返回false。
public final boolean release(int arg) {
    if (tryRelease(arg)) {
        Node h = head;
        if (h != null && h.waitStatus != 0)
            unparkSuccessor(h);
        return true;
    }
    return false;
}

/**
 * Acquires in shared mode, ignoring interrupts.  Implemented by
 * first invoking at least once {@link #tryAcquireShared},
 * returning on success.  Otherwise the thread is queued, possibly
 * repeatedly blocking and unblocking, invoking {@link
 * #tryAcquireShared} until success.
 *
 * @param arg the acquire argument.  This value is conveyed to
 *        {@link #tryAcquireShared} but is otherwise uninterpreted
 *        and can represent anything you like.
 */
//在共享方式下，获取与释放资源的流程如下：
//(1)当线程调用acquireShared(int arg)获取共享资源时，
//会首先使用tryacquireShared尝试获取资源，具体是设置状态变量state的值，成功则直接返回，
//失败则将当前线程封装为类型为Node.SHARED的Node节点后插入到AQS阻塞队列的尾部，
//并使用LockSupport.park(this)方法挂起自己。
public final void acquireShared(int arg) {
    if (tryAcquireShared(arg) < 0)
        doAcquireShared(arg);
}

/**
 * Acquires in shared mode, aborting if interrupted.  Implemented
 * by first checking interrupt status, then invoking at least once
 * {@link #tryAcquireShared}, returning on success.  Otherwise the
 * thread is queued, possibly repeatedly blocking and unblocking,
 * invoking {@link #tryAcquireShared} until success or the thread
 * is interrupted.
 * @param arg the acquire argument.
 * This value is conveyed to {@link #tryAcquireShared} but is
 * otherwise uninterpreted and can represent anything
 * you like.
 * @throws InterruptedException if the current thread is interrupted
 */
public final void acquireSharedInterruptibly(int arg)
        throws InterruptedException {
    if (Thread.interrupted())
        throw new InterruptedException();
    if (tryAcquireShared(arg) < 0)
        doAcquireSharedInterruptibly(arg);
}

/**
 * Attempts to acquire in shared mode, aborting if interrupted, and
 * failing if the given timeout elapses.  Implemented by first
 * checking interrupt status, then invoking at least once {@link
 * #tryAcquireShared}, returning on success.  Otherwise, the
 * thread is queued, possibly repeatedly blocking and unblocking,
 * invoking {@link #tryAcquireShared} until success or the thread
 * is interrupted or the timeout elapses.
 *
 * @param arg the acquire argument.  This value is conveyed to
 *        {@link #tryAcquireShared} but is otherwise uninterpreted
 *        and can represent anything you like.
 * @param nanosTimeout the maximum number of nanoseconds to wait
 * @return {@code true} if acquired; {@code false} if timed out
 * @throws InterruptedException if the current thread is interrupted
 */
public final boolean tryAcquireSharedNanos(int arg, long nanosTimeout)
        throws InterruptedException {
    if (Thread.interrupted())
        throw new InterruptedException();
    return tryAcquireShared(arg) >= 0 ||
        doAcquireSharedNanos(arg, nanosTimeout);
}


//(2)当一个线程调用releaseShared(int arg)时会尝试使用tryReleaseShared操作释放资源，这里是设置状态变量state的值，
//然后使用LockSupport.unpark(thread)激活AQS队列里面被阻塞的一个线程(thread).
//被激活的线程则使用tryReleaseShared查看当前状态变量state的值是否能满足自己的需要，
//满足则该线程被激活，然后继续向下运行，否则还是会被放入AQS队列并被挂起。

//同样需要注意的是，AQS类并没有提供可用的tryAcquireShared和tryReleaseShared法，
//正如AQS是锁阻塞和同步器的基础框架一样，tryAcquireShared和tryReleaseShared需要由具体的子类来实现。
//类在实现tryAcquireSharedtryReleaseShared时要根据具体场景使用CAS算法尝试修改state状态值，成功则返回true，
//否则返回false。

//比如继承自AQS实现的读写锁ReentrantReadWriteLock里面的读锁在重写tryAcquireShared时，
//首先查看写锁是否被其他线程持有，如果是则直接返回false，否则
//使用CAS递增state的高16位(在ReentrantReadWriteLock中,state的高16位为获取读锁的次数)。

//比如继承自AQS实现的读写锁ReentrantReadWriteLock里面的读锁在重写tryReleaseShared时，
//在内部需要使用CAS算法把当前state值的高16位减l，然后返回true，如果CAS失败则返回false。

//基于AQS实现的锁除了需要重写上面介绍的方法外，还需要重写isHeldExclusively方法，来判断锁是被当前线程独占还是被共享。

//另外，也许你会好奇，独占方式下的void acquire(int arg)和void acquirelnterruptibly(int arg)，
//与共享方式下的void acquireShared(int arg)和void acquireSharedlnterruptibly(int arg),
//这两套函数中都有一个带有Interruptibly关键字的函数，那么带这个关键字和不带有什么区别呢？

//我们来讲讲。其实不带Interruptibly关键字的方法的意思是不对中断进行响应，
//也就是线程在调用不带Interruptibly关键字的方法获取资源时或者获取资源失败被挂起时，其他线程中断了该线程，
//那么该线程不会因为被中断而抛出异常，它还是继续获取资源或者被挂起，也就是说不对中断进行响应，忽略中断。
//而带Interruptibly关键字的方法要对中断进行响应，
//也就是线程在调用带Interruptibly关键字的方获取资源时或者获取资源失败被挂起时，其他线程中断了该线程，
//那么该线程会抛出InterruptedException异常而返回。
public final boolean releaseShared(int arg) {
    if (tryReleaseShared(arg)) {
        doReleaseShared();
        return true;
    }
    return false;
}

// Queue inspection methods
/**
 * Queries whether any threads are waiting to acquire. Note that
 * because cancellations due to interrupts and timeouts may occur
 * at any time, a {@code true} return does not guarantee that any
 * other thread will ever acquire.
 *
 * <p>In this implementation, this operation returns in
 * constant time.
 *
 * @return {@code true} if there may be other threads waiting to acquire
 */
public final boolean hasQueuedThreads() {
    return head != tail;
}

/**
 * Queries whether any threads have ever contended to acquire this
 * synchronizer; that is if an acquire method has ever blocked.
 *
 * <p>In this implementation, this operation returns in
 * constant time.
 *
 * @return {@code true} if there has ever been contention
 */
public final boolean hasContended() {
    return head != null;
}

/**
 * Returns the first (longest-waiting) thread in the queue, or
 * {@code null} if no threads are currently queued.
 *
 * <p>In this implementation, this operation normally returns in
 * constant time, but may iterate upon contention if other threads are
 * concurrently modifying the queue.
 *
 * @return the first (longest-waiting) thread in the queue, or
 *         {@code null} if no threads are currently queued
 */
public final Thread getFirstQueuedThread() {
    // handle only fast path, else relay
    return (head == tail) ? null : fullGetFirstQueuedThread();
}

/**
 * Version of getFirstQueuedThread called when fastpath fails
 */
private Thread fullGetFirstQueuedThread() {
    /*
     * The first node is normally head.next. Try to get its
     * thread field, ensuring consistent reads: If thread
     * field is nulled out or s.prev is no longer head, then
     * some other thread(s) concurrently performed setHead in
     * between some of our reads. We try this twice before
     * resorting to traversal.
     */
    Node h, s;
    Thread st;
    if (((h = head) != null && (s = h.next) != null &&
         s.prev == head && (st = s.thread) != null) ||
        ((h = head) != null && (s = h.next) != null &&
         s.prev == head && (st = s.thread) != null))
        return st;

    /*
     * Head's next field might not have been set yet, or may have
     * been unset after setHead. So we must check to see if tail
     * is actually first node. If not, we continue on, safely
     * traversing from tail back to head to find first,
     * guaranteeing termination.
     */

    Node t = tail;
    Thread firstThread = null;
    while (t != null && t != head) {
        Thread tt = t.thread;
        if (tt != null)
            firstThread = tt;
        t = t.prev;
    }
    return firstThread;
}

/**
 * Returns true if the given thread is currently queued.
 *
 * <p>This implementation traverses the queue to determine
 * presence of the given thread.
 *
 * @param thread the thread
 * @return {@code true} if the given thread is on the queue
 * @throws NullPointerException if the thread is null
 */
public final boolean isQueued(Thread thread) {
    if (thread == null)
        throw new NullPointerException();
    for (Node p = tail; p != null; p = p.prev)
        if (p.thread == thread)
            return true;
    return false;
}

/**
 * Returns {@code true} if the apparent first queued thread, if one
 * exists, is waiting in exclusive mode.  If this method returns
 * {@code true}, and the current thread is attempting to acquire in
 * shared mode (that is, this method is invoked from {@link
 * #tryAcquireShared}) then it is guaranteed that the current thread
 * is not the first queued thread.  Used only as a heuristic in
 * ReentrantReadWriteLock.
 */
final boolean apparentlyFirstQueuedIsExclusive() {
    Node h, s;
    return (h = head) != null &&
        (s = h.next)  != null &&
        !s.isShared()         &&
        s.thread != null;
}

/**
 * Queries whether any threads have been waiting to acquire longer
 * than the current thread.
 *
 * <p>An invocation of this method is equivalent to (but may be
 * more efficient than):
 *  <pre> {@code
 * getFirstQueuedThread() != Thread.currentThread() &&
 * hasQueuedThreads()}</pre>
 *
 * <p>Note that because cancellations due to interrupts and
 * timeouts may occur at any time, a {@code true} return does not
 * guarantee that some other thread will acquire before the current
 * thread.  Likewise, it is possible for another thread to win a
 * race to enqueue after this method has returned {@code false},
 * due to the queue being empty.
 *
 * <p>This method is designed to be used by a fair synchronizer to
 * avoid <a href="AbstractQueuedSynchronizer#barging">barging</a>.
 * Such a synchronizer's {@link #tryAcquire} method should return
 * {@code false}, and its {@link #tryAcquireShared} method should
 * return a negative value, if this method returns {@code true}
 * (unless this is a reentrant acquire).  For example, the {@code
 * tryAcquire} method for a fair, reentrant, exclusive mode
 * synchronizer might look like this:
 *
 *  <pre> {@code
 * protected boolean tryAcquire(int arg) {
 *   if (isHeldExclusively()) {
 *     // A reentrant acquire; increment hold count
 *     return true;
 *   } else if (hasQueuedPredecessors()) {
 *     return false;
 *   } else {
 *     // try to acquire normally
 *   }
 * }}</pre>
 *
 * @return {@code true} if there is a queued thread preceding the
 *         current thread, and {@code false} if the current thread
 *         is at the head of the queue or the queue is empty
 * @since 1.7
 */
//如下代码中，如果当前线程节点有前驱节点则返回true，
//否则如果当前AQS队列为空或者当前线程节点是AQS的第一个节点则返回false。
//其中如果h==t则说明当前队列为空，直接返回false；如果h!=t并且s==null
//则说明有一个元素将要作为AQS的第一个节点入队列
//（回顾前面的内容，enq函数的第一个元素入队列是两步操作：
//首先创建一个哨兵头节点，然后将第一个元素插入哨兵节点后面〉，那么返回true，
//如果h!=t并且s!=null和s.thread!=Thread.cunentThread()
//则说明队列里面的第一个元素是当前线程，那么返回true。
public final boolean hasQueuedPredecessors() {
    // The correctness of this depends on head being initialized
    // before tail and on head.next being accurate if the current
    // thread is first in queue.
    Node t = tail; // Read fields in reverse initialization order
    Node h = head;
    Node s;
    return h != t &&
        ((s = h.next) == null || s.thread != Thread.currentThread());
}


// Instrumentation and monitoring methods

/**
 * Returns an estimate of the number of threads waiting to
 * acquire.  The value is only an estimate because the number of
 * threads may change dynamically while this method traverses
 * internal data structures.  This method is designed for use in
 * monitoring system state, not for synchronization
 * control.
 *
 * @return the estimated number of threads waiting to acquire
 */
public final int getQueueLength() {
    int n = 0;
    for (Node p = tail; p != null; p = p.prev) {
        if (p.thread != null)
            ++n;
    }
    return n;
}

/**
 * Returns a collection containing threads that may be waiting to
 * acquire.  Because the actual set of threads may change
 * dynamically while constructing this result, the returned
 * collection is only a best-effort estimate.  The elements of the
 * returned collection are in no particular order.  This method is
 * designed to facilitate construction of subclasses that provide
 * more extensive monitoring facilities.
 *
 * @return the collection of threads
 */
public final Collection<Thread> getQueuedThreads() {
    ArrayList<Thread> list = new ArrayList<Thread>();
    for (Node p = tail; p != null; p = p.prev) {
        Thread t = p.thread;
        if (t != null)
            list.add(t);
    }
    return list;
}

/**
 * Returns a collection containing threads that may be waiting to
 * acquire in exclusive mode. This has the same properties
 * as {@link #getQueuedThreads} except that it only returns
 * those threads waiting due to an exclusive acquire.
 *
 * @return the collection of threads
 */
public final Collection<Thread> getExclusiveQueuedThreads() {
    ArrayList<Thread> list = new ArrayList<Thread>();
    for (Node p = tail; p != null; p = p.prev) {
        if (!p.isShared()) {
            Thread t = p.thread;
            if (t != null)
                list.add(t);
        }
    }
    return list;
}

/**
 * Returns a collection containing threads that may be waiting to
 * acquire in shared mode. This has the same properties
 * as {@link #getQueuedThreads} except that it only returns
 * those threads waiting due to a shared acquire.
 *
 * @return the collection of threads
 */
public final Collection<Thread> getSharedQueuedThreads() {
    ArrayList<Thread> list = new ArrayList<Thread>();
    for (Node p = tail; p != null; p = p.prev) {
        if (p.isShared()) {
            Thread t = p.thread;
            if (t != null)
                list.add(t);
        }
    }
    return list;
}

/**
 * Returns a string identifying this synchronizer, as well as its state.
 * The state, in brackets, includes the String {@code "State ="}
 * followed by the current value of {@link #getState}, and either
 * {@code "nonempty"} or {@code "empty"} depending on whether the
 * queue is empty.
 *
 * @return a string identifying this synchronizer, as well as its state
 */
public String toString() {
    int s = getState();
    String q  = hasQueuedThreads() ? "non" : "";
    return super.toString() +
        "[State = " + s + ", " + q + "empty queue]";
}


// Internal support methods for Conditions

/**
 * Returns true if a node, always one that was initially placed on
 * a condition queue, is now waiting to reacquire on sync queue.
 * @param node the node
 * @return true if is reacquiring
 */
final boolean isOnSyncQueue(Node node) {
    if (node.waitStatus == Node.CONDITION || node.prev == null)
        return false;
    if (node.next != null) // If has successor, it must be on queue
        return true;
    /*
     * node.prev can be non-null, but not yet on queue because
     * the CAS to place it on queue can fail. So we have to
     * traverse from tail to make sure it actually made it.  It
     * will always be near the tail in calls to this method, and
     * unless the CAS failed (which is unlikely), it will be
     * there, so we hardly ever traverse much.
     */
    return findNodeFromTail(node);
}

/**
 * Returns true if node is on sync queue by searching backwards from tail.
 * Called only when needed by isOnSyncQueue.
 * @return true if present
 */
private boolean findNodeFromTail(Node node) {
    Node t = tail;
    for (;;) {
        if (t == node)
            return true;
        if (t == null)
            return false;
        t = t.prev;
    }
}

/**
 * Transfers a node from a condition queue onto sync queue.
 * Returns true if successful.
 * @param node the node
 * @return true if successfully transferred (else the node was
 * cancelled before signal)
 */
final boolean transferForSignal(Node node) {
    /*
     * If cannot change waitStatus, the node has been cancelled.
     */
    if (!compareAndSetWaitStatus(node, Node.CONDITION, 0))
        return false;

    /*
     * Splice onto queue and try to set waitStatus of predecessor to
     * indicate that thread is (probably) waiting. If cancelled or
     * attempt to set waitStatus fails, wake up to resync (in which
     * case the waitStatus can be transiently and harmlessly wrong).
     */
    Node p = enq(node);
    int ws = p.waitStatus;
    if (ws > 0 || !compareAndSetWaitStatus(p, ws, Node.SIGNAL))
        LockSupport.unpark(node.thread);
    return true;
}

/**
 * Transfers node, if necessary, to sync queue after a cancelled wait.
 * Returns true if thread was cancelled before being signalled.
 *
 * @param node the node
 * @return true if cancelled before the node was signalled
 */
final boolean transferAfterCancelledWait(Node node) {
    if (compareAndSetWaitStatus(node, Node.CONDITION, 0)) {
        enq(node);
        return true;
    }
    /*
     * If we lost out to a signal(), then we can't proceed
     * until it finishes its enq().  Cancelling during an
     * incomplete transfer is both rare and transient, so just
     * spin.
     */
    while (!isOnSyncQueue(node))
        Thread.yield();
    return false;
}

/**
 * Invokes release with current state value; returns saved state.
 * Cancels node and throws exception on failure.
 * @param node the condition node for this wait
 * @return previous sync state
 */
final int fullyRelease(Node node) {
    boolean failed = true;
    try {
        int savedState = getState();
        if (release(savedState)) {
            failed = false;
            return savedState;
        } else {
            throw new IllegalMonitorStateException();
        }
    } finally {
        if (failed)
            node.waitStatus = Node.CANCELLED;
    }
}

// Instrumentation methods for conditions

/**
 * Queries whether the given ConditionObject
 * uses this synchronizer as its lock.
 *
 * @param condition the condition
 * @return {@code true} if owned
 * @throws NullPointerException if the condition is null
 */
public final boolean owns(ConditionObject condition) {
    return condition.isOwnedBy(this);
}

/**
 * Queries whether any threads are waiting on the given condition
 * associated with this synchronizer. Note that because timeouts
 * and interrupts may occur at any time, a {@code true} return
 * does not guarantee that a future {@code signal} will awaken
 * any threads.  This method is designed primarily for use in
 * monitoring of the system state.
 *
 * @param condition the condition
 * @return {@code true} if there are any waiting threads
 * @throws IllegalMonitorStateException if exclusive synchronization
 *         is not held
 * @throws IllegalArgumentException if the given condition is
 *         not associated with this synchronizer
 * @throws NullPointerException if the condition is null
 */
public final boolean hasWaiters(ConditionObject condition) {
    if (!owns(condition))
        throw new IllegalArgumentException("Not owner");
    return condition.hasWaiters();
}

/**
 * Returns an estimate of the number of threads waiting on the
 * given condition associated with this synchronizer. Note that
 * because timeouts and interrupts may occur at any time, the
 * estimate serves only as an upper bound on the actual number of
 * waiters.  This method is designed for use in monitoring of the
 * system state, not for synchronization control.
 *
 * @param condition the condition
 * @return the estimated number of waiting threads
 * @throws IllegalMonitorStateException if exclusive synchronization
 *         is not held
 * @throws IllegalArgumentException if the given condition is
 *         not associated with this synchronizer
 * @throws NullPointerException if the condition is null
 */
public final int getWaitQueueLength(ConditionObject condition) {
    if (!owns(condition))
        throw new IllegalArgumentException("Not owner");
    return condition.getWaitQueueLength();
}

/**
 * Returns a collection containing those threads that may be
 * waiting on the given condition associated with this
 * synchronizer.  Because the actual set of threads may change
 * dynamically while constructing this result, the returned
 * collection is only a best-effort estimate. The elements of the
 * returned collection are in no particular order.
 *
 * @param condition the condition
 * @return the collection of threads
 * @throws IllegalMonitorStateException if exclusive synchronization
 *         is not held
 * @throws IllegalArgumentException if the given condition is
 *         not associated with this synchronizer
 * @throws NullPointerException if the condition is null
 */
public final Collection<Thread> getWaitingThreads(ConditionObject condition) {
    if (!owns(condition))
        throw new IllegalArgumentException("Not owner");
    return condition.getWaitingThreads();
}

/**
 * Condition implementation for a {@link
 * AbstractQueuedSynchronizer1} serving as the basis of a {@link
 * Lock} implementation.
 *
 * <p>Method documentation for this class describes mechanics,
 * not behavioral specifications from the point of view of Lock
 * and Condition users. Exported versions of this class will in
 * general need to be accompanied by documentation describing
 * condition semantics that rely on those of the associated
 * {@code AbstractQueuedSynchronizer}.
 *
 * <p>This class is Serializable, but all fields are transient,
 * so deserialized conditions have no waiters.
 */
//AQS有个内部类ConditionObject，用来结合锁实现线程同步。
//ConditionObject可以直接访问AQS对象内部的变量，比如state状态值和AQS队列。
//ConditionObject是条件变量，每个条件变量对应一个条件队列(单向链表队列)，
//其用来存放调用条件变量await方法后被阻塞的线程
//对于AQS来说，线程同步的关键是对状态值state进行操作。
//根据state是否属于一个线程，操作state的方式分为独占方式和共享方式。
//在独占方式下获取和释放资源使用的方法为：
//void acquire(intarg)
//void acquirelnterruptibly(intarg) 
//booleanrelease(intarg)。
//在共享方式下获取和释放资源的方法为：
//void acquireShared(intarg) 
//void acquireSharedinterruptibly(int arg)
//boolean releaseShared(int arg)
//使用独占方式获取的资源是与具体线程绑定的，就是说如果一个线程获取到了资源，
//就会标记是这个线程获取到了，其他线程再尝试操作state获取资源时会发现当前该资源不是自己持有的，
//就会在获取失败后被阻塞。比如独占锁ReentrantLock的实现，当一个线程获取了ReerrantLock的锁后，
//在QS内部会首先使用CAS操作把state状值从0变为1，然后设置当前锁的持有者为当前线程，
//当该线程再次获取锁时发现它就是锁的持有者，则会把状态值从l变为2，也就是设置可重入次数，
//而当另外一个线程获取锁时发现自己并不是该锁的持有者就会被放入AQS阻塞队列后挂起。
//对应共享方式的资源与具体线程是不相关的，当多个线程去请求资源时通过CAS方式竞争获取资源，
//当一个线程获取到了资源后，另外一个线程再次去获取时如果当前资源还能满足它的需要，
//则当前线程只需要使用CAS方式进行获取即可比如 Semaphore信号量 当一个线程通过acquire()法获取信号量时，
//会首先看当前信号量个数是否满足需要，不满足则把当前线程放入阻塞队列，
//如果满足则通过自旋CAS获取信号量。
public class ConditionObject implements Condition, java.io.Serializable {
    private static final long serialVersionUID = 1173984872572414699L;
    /** First node of condition queue. */
    //条件队列的头元素
    private transient Node firstWaiter;
    /** Last node of condition queue. */
    //条件队列的尾元素
    private transient Node lastWaiter;

    /**
     * Creates a new {@code ConditionObject} instance.
     */
    public ConditionObject() { }

    // Internal methods

    /**
     * Adds a new waiter to wait queue.
     * @return its new wait node
     */
//    要注意的是，AQS只提供了ConditioObject的实现，并没有提供newCondition函数，
//    该函数用来new一个ConditionObject对象。需要由AQS的子类来提供newCondition函数。
//    下面来看当一个线程调用条件变量的await()方法而被阻塞后，如何将其放入条件队列。
    private Node addConditionWaiter() {
        Node t = lastWaiter;
        // If lastWaiter is cancelled, clean out.
        if (t != null && t.waitStatus != Node.CONDITION) {
            unlinkCancelledWaiters();
            t = lastWaiter;
        }
        //1
        Node node = new Node(Thread.currentThread(), Node.CONDITION);
        //2
        if (t == null)
            firstWaiter = node;
        else
            t.nextWaiter = node;//3
        lastWaiter = node;//4
        return node;
    }
//    代码(1)首先根据当前线程创建一个类型为Node.CONDITION的节点，然后通过代码(2)(3)(4)在单向条件队列尾部插入一个元素。

//    注意：当多个线程同时调用lock.lock()方法获取锁时，只有一个线程获取到了锁，
//    其他线程会被转换为Node节点插入到lock对应的AQS阻塞队列里面，并做自旋CAS尝试获取锁。
    
//    如果获取到锁的线程又调用了对应的条件变量的await()方法，则该线程会释放获取到的锁，
//    并被转换为Node节点插入到条件变量对应的条队列里面。
    
//    这时候因为调用lock.lock()方法被阻塞到AQS队列里面的一个线程会获取到被释放的锁，
//    如果该线程也调用了条件变量的await()方法则该线程也会被放入条件变量的条件队列里面。
    
//    当另外一个线程调用条变量的signal()或者signa!All()方法，
//    会把条件队列里面的一个或者全部Node节点移动到AQS的阻塞队列里面，等待机获取锁。
    
//    最后使用一个图(见图6-3)总结如下：一个锁对应一个AQS阻塞队列，对应多个条件变量，每个条件变量有自己的一个条件队列。131 图6-3

    /**
     * Removes and transfers nodes until hit non-cancelled one or
     * null. Split out from signal in part to encourage compilers
     * to inline the case of no waiters.
     * @param first (non-null) the first node on condition queue
     */
    private void doSignal(Node first) {
        do {
            if ( (firstWaiter = first.nextWaiter) == null)
                lastWaiter = null;
            first.nextWaiter = null;
        } while (!transferForSignal(first) &&
                 (first = firstWaiter) != null);
    }

    /**
     * Removes and transfers all nodes.
     * @param first (non-null) the first node on condition queue
     */
    private void doSignalAll(Node first) {
        lastWaiter = firstWaiter = null;
        do {
            Node next = first.nextWaiter;
            first.nextWaiter = null;
            transferForSignal(first);
            first = next;
        } while (first != null);
    }

    /**
     * Unlinks cancelled waiter nodes from condition queue.
     * Called only while holding lock. This is called when
     * cancellation occurred during condition wait, and upon
     * insertion of a new waiter when lastWaiter is seen to have
     * been cancelled. This method is needed to avoid garbage
     * retention in the absence of signals. So even though it may
     * require a full traversal, it comes into play only when
     * timeouts or cancellations occur in the absence of
     * signals. It traverses all nodes rather than stopping at a
     * particular target to unlink all pointers to garbage nodes
     * without requiring many re-traversals during cancellation
     * storms.
     */
    private void unlinkCancelledWaiters() {
        Node t = firstWaiter;
        Node trail = null;
        while (t != null) {
            Node next = t.nextWaiter;
            if (t.waitStatus != Node.CONDITION) {
                t.nextWaiter = null;
                if (trail == null)
                    firstWaiter = next;
                else
                    trail.nextWaiter = next;
                if (next == null)
                    lastWaiter = trail;
            }
            else
                trail = t;
            t = next;
        }
    }

    // public methods

    /**
     * Moves the longest-waiting thread, if one exists, from the
     * wait queue for this condition to the wait queue for the
     * owning lock.
     *
     * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
     *         returns {@code false}
     */

    //如下代码中，当另外一个线程调用条变量的signal方法时(必须先调用锁的lock()方法获取锁)
//    在内部会把条件队列里面队头的一个线程节点从条队列里面移除并放入AQS的阻塞队列里面，然后激活这个线程。
    public final void signal() {
        if (!isHeldExclusively())
            throw new IllegalMonitorStateException();
        Node first = firstWaiter;
        if (first != null)
        	//将条件队列头元素移动到AQS队列
            doSignal(first);
    }

    /**
     * Moves all threads from the wait queue for this condition to
     * the wait queue for the owning lock.
     *
     * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
     *         returns {@code false}
     */
    public final void signalAll() {
        if (!isHeldExclusively())
            throw new IllegalMonitorStateException();
        Node first = firstWaiter;
        if (first != null)
            doSignalAll(first);
    }

    /**
     * Implements uninterruptible condition wait.
     * <ol>
     * <li> Save lock state returned by {@link #getState}.
     * <li> Invoke {@link #release} with saved state as argument,
     *      throwing IllegalMonitorStateException if it fails.
     * <li> Block until signalled.
     * <li> Reacquire by invoking specialized version of
     *      {@link #acquire} with saved state as argument.
     * </ol>
     */
    public final void awaitUninterruptibly() {
        Node node = addConditionWaiter();
        int savedState = fullyRelease(node);
        boolean interrupted = false;
        while (!isOnSyncQueue(node)) {
            LockSupport.park(this);
            if (Thread.interrupted())
                interrupted = true;
        }
        if (acquireQueued(node, savedState) || interrupted)
            selfInterrupt();
    }

    /*
     * For interruptible waits, we need to track whether to throw
     * InterruptedException, if interrupted while blocked on
     * condition, versus reinterrupt current thread, if
     * interrupted while blocked waiting to re-acquire.
     */

    /** Mode meaning to reinterrupt on exit from wait */
    private static final int REINTERRUPT =  1;
    /** Mode meaning to throw InterruptedException on exit from wait */
    private static final int THROW_IE    = -1;

    /**
     * Checks for interrupt, returning THROW_IE if interrupted
     * before signalled, REINTERRUPT if after signalled, or
     * 0 if not interrupted.
     */
    private int checkInterruptWhileWaiting(Node node) {
        return Thread.interrupted() ?
            (transferAfterCancelledWait(node) ? THROW_IE : REINTERRUPT) :
            0;
    }

    /**
     * Throws InterruptedException, reinterrupts current thread, or
     * does nothing, depending on mode.
     */
    private void reportInterruptAfterWait(int interruptMode)
        throws InterruptedException {
        if (interruptMode == THROW_IE)
            throw new InterruptedException();
        else if (interruptMode == REINTERRUPT)
            selfInterrupt();
    }

    /**
     * Implements interruptible condition wait.
     * <ol>
     * <li> If current thread is interrupted, throw InterruptedException.
     * <li> Save lock state returned by {@link #getState}.
     * <li> Invoke {@link #release} with saved state as argument,
     *      throwing IllegalMonitorStateException if it fails.
     * <li> Block until signalled or interrupted.
     * <li> Reacquire by invoking specialized version of
     *      {@link #acquire} with saved state as argument.
     * <li> If interrupted while blocked in step 4, throw InterruptedException.
     * </ol>
     */
//	  ReentrantLock lock=new ReentrantLock();
//	  Condition condition = lock.newCondition();
//	  lock.lock(); //3
//      try {
//    	System.out.println("begin await");
//			condition.await();//4
//			System.out.println("begin await");
//		} catch (InterruptedException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}finally {
//			lock.unlock(); //5
//		}
//      lock.lock(); //6
//      try {
//    	  System.out.println("begin await");
//    	  condition.signal();//7
//    	  System.out.println("begin await");
//      } catch (Exception e) {
//    	  e.printStackTrace();
//      }finally {
//    	  lock.unlock(); //8
//      }
//  在上面代码中，lock.newCondition()的作用其实是new了一个在AQS内部声明的ConditionObject对象，
//  ConditionObject是AQS的内部类，可以访问QS内部的变量(例如状态变state)和方法。
//  在每个条件变量内部都维护了一个条件队列，用来存放调用条变量的await()方法时被阻塞的线程。
//  注意这个条件队列和AQS队列不是一回事。在如下代码中，当线程调用条件变量的await()方法时
//  (必须先调用锁的lock()方获取锁)，在内部会构造一个类型为Node.CONDITION的node节点，
//  然后将该节点插入条件队列末尾，之后当前线程会释放获取的锁(也就是会操作锁对应的state变量的值)，
//  并被阻塞挂起。这时候如果有其他线程调用lock.lock()尝试获取锁，就会－个线程获取到锁，
//  如果获取到锁的线程调用了条件变量的await()方法，则该线程也会被放入条件量的阻塞队列，
//  然后释放获取到的锁，在await()方法处阻塞。
    public final void await() throws InterruptedException {
        if (Thread.interrupted())
            throw new InterruptedException();
        //创建新的Node节点，并插入到条件队列末尾 9
        Node node = addConditionWaiter();
        //释放当前线程获取的锁 10
        int savedState = fullyRelease(node);
        int interruptMode = 0;
        //调用park方法组织挂起当前线程 11
        while (!isOnSyncQueue(node)) {
            LockSupport.park(this);
            if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
                break;
        }
        if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
            interruptMode = REINTERRUPT;
        if (node.nextWaiter != null) // clean up if cancelled
            unlinkCancelledWaiters();
        if (interruptMode != 0)
            reportInterruptAfterWait(interruptMode);
    }

    /**
     * Implements timed condition wait.
     * <ol>
     * <li> If current thread is interrupted, throw InterruptedException.
     * <li> Save lock state returned by {@link #getState}.
     * <li> Invoke {@link #release} with saved state as argument,
     *      throwing IllegalMonitorStateException if it fails.
     * <li> Block until signalled, interrupted, or timed out.
     * <li> Reacquire by invoking specialized version of
     *      {@link #acquire} with saved state as argument.
     * <li> If interrupted while blocked in step 4, throw InterruptedException.
     * </ol>
     */
    public final long awaitNanos(long nanosTimeout)
            throws InterruptedException {
        if (Thread.interrupted())
            throw new InterruptedException();
        Node node = addConditionWaiter();
        int savedState = fullyRelease(node);
        final long deadline = System.nanoTime() + nanosTimeout;
        int interruptMode = 0;
        while (!isOnSyncQueue(node)) {
            if (nanosTimeout <= 0L) {
                transferAfterCancelledWait(node);
                break;
            }
            if (nanosTimeout >= spinForTimeoutThreshold)
                LockSupport.parkNanos(this, nanosTimeout);
            if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
                break;
            nanosTimeout = deadline - System.nanoTime();
        }
        if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
            interruptMode = REINTERRUPT;
        if (node.nextWaiter != null)
            unlinkCancelledWaiters();
        if (interruptMode != 0)
            reportInterruptAfterWait(interruptMode);
        return deadline - System.nanoTime();
    }

    /**
     * Implements absolute timed condition wait.
     * <ol>
     * <li> If current thread is interrupted, throw InterruptedException.
     * <li> Save lock state returned by {@link #getState}.
     * <li> Invoke {@link #release} with saved state as argument,
     *      throwing IllegalMonitorStateException if it fails.
     * <li> Block until signalled, interrupted, or timed out.
     * <li> Reacquire by invoking specialized version of
     *      {@link #acquire} with saved state as argument.
     * <li> If interrupted while blocked in step 4, throw InterruptedException.
     * <li> If timed out while blocked in step 4, return false, else true.
     * </ol>
     */
    public final boolean awaitUntil(Date deadline)
            throws InterruptedException {
        long abstime = deadline.getTime();
        if (Thread.interrupted())
            throw new InterruptedException();
        Node node = addConditionWaiter();
        int savedState = fullyRelease(node);
        boolean timedout = false;
        int interruptMode = 0;
        while (!isOnSyncQueue(node)) {
            if (System.currentTimeMillis() > abstime) {
                timedout = transferAfterCancelledWait(node);
                break;
            }
            LockSupport.parkUntil(this, abstime);
            if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
                break;
        }
        if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
            interruptMode = REINTERRUPT;
        if (node.nextWaiter != null)
            unlinkCancelledWaiters();
        if (interruptMode != 0)
            reportInterruptAfterWait(interruptMode);
        return !timedout;
    }

    /**
     * Implements timed condition wait.
     * <ol>
     * <li> If current thread is interrupted, throw InterruptedException.
     * <li> Save lock state returned by {@link #getState}.
     * <li> Invoke {@link #release} with saved state as argument,
     *      throwing IllegalMonitorStateException if it fails.
     * <li> Block until signalled, interrupted, or timed out.
     * <li> Reacquire by invoking specialized version of
     *      {@link #acquire} with saved state as argument.
     * <li> If interrupted while blocked in step 4, throw InterruptedException.
     * <li> If timed out while blocked in step 4, return false, else true.
     * </ol>
     */
    public final boolean await(long time, TimeUnit unit)
            throws InterruptedException {
        long nanosTimeout = unit.toNanos(time);
        if (Thread.interrupted())
            throw new InterruptedException();
        Node node = addConditionWaiter();
        int savedState = fullyRelease(node);
        final long deadline = System.nanoTime() + nanosTimeout;
        boolean timedout = false;
        int interruptMode = 0;
        while (!isOnSyncQueue(node)) {
            if (nanosTimeout <= 0L) {
                timedout = transferAfterCancelledWait(node);
                break;
            }
            if (nanosTimeout >= spinForTimeoutThreshold)
                LockSupport.parkNanos(this, nanosTimeout);
            if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
                break;
            nanosTimeout = deadline - System.nanoTime();
        }
        if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
            interruptMode = REINTERRUPT;
        if (node.nextWaiter != null)
            unlinkCancelledWaiters();
        if (interruptMode != 0)
            reportInterruptAfterWait(interruptMode);
        return !timedout;
    }

    //  support for instrumentation

    /**
     * Returns true if this condition was created by the given
     * synchronization object.
     *
     * @return {@code true} if owned
     */
    final boolean isOwnedBy(AbstractQueuedSynchronizer1 sync) {
        return sync == AbstractQueuedSynchronizer1.this;
    }

    /**
     * Queries whether any threads are waiting on this condition.
     * Implements {@link AbstractQueuedSynchronizer1#hasWaiters(ConditionObject)}.
     *
     * @return {@code true} if there are any waiting threads
     * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
     *         returns {@code false}
     */
    protected final boolean hasWaiters() {
        if (!isHeldExclusively())
            throw new IllegalMonitorStateException();
        for (Node w = firstWaiter; w != null; w = w.nextWaiter) {
            if (w.waitStatus == Node.CONDITION)
                return true;
        }
        return false;
    }

    /**
     * Returns an estimate of the number of threads waiting on
     * this condition.
     * Implements {@link AbstractQueuedSynchronizer1#getWaitQueueLength(ConditionObject)}.
     *
     * @return the estimated number of waiting threads
     * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
     *         returns {@code false}
     */
    protected final int getWaitQueueLength() {
        if (!isHeldExclusively())
            throw new IllegalMonitorStateException();
        int n = 0;
        for (Node w = firstWaiter; w != null; w = w.nextWaiter) {
            if (w.waitStatus == Node.CONDITION)
                ++n;
        }
        return n;
    }

    /**
     * Returns a collection containing those threads that may be
     * waiting on this Condition.
     * Implements {@link AbstractQueuedSynchronizer1#getWaitingThreads(ConditionObject)}.
     *
     * @return the collection of threads
     * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
     *         returns {@code false}
     */
    protected final Collection<Thread> getWaitingThreads() {
        if (!isHeldExclusively())
            throw new IllegalMonitorStateException();
        ArrayList<Thread> list = new ArrayList<Thread>();
        for (Node w = firstWaiter; w != null; w = w.nextWaiter) {
            if (w.waitStatus == Node.CONDITION) {
                Thread t = w.thread;
                if (t != null)
                    list.add(t);
            }
        }
        return list;
    }
}

/**
 * Setup to support compareAndSet. We need to natively implement
 * this here: For the sake of permitting future enhancements, we
 * cannot explicitly subclass AtomicInteger, which would be
 * efficient and useful otherwise. So, as the lesser of evils, we
 * natively implement using hotspot intrinsics API. And while we
 * are at it, we do the same for other CASable fields (which could
 * otherwise be done with atomic field updaters).
 */
private static final Unsafe unsafe = Unsafe.getUnsafe();
private static final long stateOffset;
private static final long headOffset;
private static final long tailOffset;
private static final long waitStatusOffset;
private static final long nextOffset;

static {
    try {
        stateOffset = unsafe.objectFieldOffset
            (AbstractQueuedSynchronizer1.class.getDeclaredField("state"));
        headOffset = unsafe.objectFieldOffset
            (AbstractQueuedSynchronizer1.class.getDeclaredField("head"));
        tailOffset = unsafe.objectFieldOffset
            (AbstractQueuedSynchronizer1.class.getDeclaredField("tail"));
        waitStatusOffset = unsafe.objectFieldOffset
            (Node.class.getDeclaredField("waitStatus"));
        nextOffset = unsafe.objectFieldOffset
            (Node.class.getDeclaredField("next"));

    } catch (Exception ex) { throw new Error(ex); }
}

/**
 * CAS head field. Used only by enq.
 */
private final boolean compareAndSetHead(Node update) {
    return unsafe.compareAndSwapObject(this, headOffset, null, update);
}

/**
 * CAS tail field. Used only by enq.
 */
private final boolean compareAndSetTail(Node expect, Node update) {
    return unsafe.compareAndSwapObject(this, tailOffset, expect, update);
}

/**
 * CAS waitStatus field of a node.
 */
private static final boolean compareAndSetWaitStatus(Node node,
                                                     int expect,
                                                     int update) {
    return unsafe.compareAndSwapInt(node, waitStatusOffset,
                                    expect, update);
}

/**
 * CAS next field of a node.
 */
private static final boolean compareAndSetNext(Node node,
                                               Node expect,
                                               Node update) {
    return unsafe.compareAndSwapObject(node, nextOffset, expect, update);
}
}
