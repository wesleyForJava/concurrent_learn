package com.concuurent.learn.juc;



import java.util.AbstractQueue;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;

/**
 *ConcurrentLinkedQueue内部的队列使用单向链表方式实现，
 *其中有两个volatile类型的Node节点分别用来存放队列的首、尾节点。
 *从下面的无参构造函数可知，默认头、尾节点都是指向item为null的哨兵节点。
 *新元素会被插入队列末尾，出队时从队列头部获取一个元素
 * <p>ConcurrentLinkedQueue的底层使用单向链表数据结构来保存队列元素，
 * 每个元素被包装成一个Node节点。队列是靠头、尾节点来维护的，创建队列时头、尾节点指向－个item为null的哨兵节点。
 * 第一次执行peek或者first操作时会把head指向第一个真正的队列元素。由于使用非阻塞CAS算法，
 * 没有加锁，所以在计算size时有可能进行了offer、poll或者remove操作，
 * 导致计算的元素个数不精确所以在井发情况下size函数不是很有用。如图7-27所示，
 * 入队、出队都是操作使用volatile修饰的tail、head节点，要保证在多线程下出队线程安全，
 * 只需要保证这两个Node操作的可见性和原子性即可。
 * 由于volatile本身可以保证可见性，所以只需要保证对两个变量操作的原子性即可。
 * 
 * <p>offer操作是在tail后面添加元素，
 * 也就是调用tail.casNext方法，而这个方法使用的是CAS操作，
 * 只有一个线程会成功，然后失败的线程会循环，重新获取tail，再执行casNext方法。
 * poll操作也通过类似CAS的算法保证出队时移除节点操作的原子性。
 * @author Wesley
 * @param <E>
 * 2019年7月3日上午9:48:51
 * @Version 1.0
 */
public class ConcurrentLinkedQueue<E> extends AbstractQueue<E>
        implements Queue<E>, java.io.Serializable {
    private static final long serialVersionUID = 196745693267521676L;

    /**
     * 在Node点内部则维护一个使用volatile修饰的变量item，
     * 用来存放节点的值；next用来存放链表的下一个节点，
     * 从而链接为一个单向无界链表。
     * 其内部则使用UNSafe工具类提供的CAS算法来保证出入队时操作链表的原子性。
     */

    private static class Node<E> {
        volatile E item;
        volatile Node<E> next;

        /**
         * 构造新节点。使用轻松写入，因为item可以只有在通过Casnext发布后才能看到。
         */
        Node(E item) {
            UNSAFE.putObject(this, itemOffset, item);
        }

        boolean casItem(E cmp, E val) {
            return UNSAFE.compareAndSwapObject(this, itemOffset, cmp, val);
        }

        void lazySetNext(Node<E> val) {
            UNSAFE.putOrderedObject(this, nextOffset, val);
        }

        boolean casNext(Node<E> cmp, Node<E> val) {
            return UNSAFE.compareAndSwapObject(this, nextOffset, cmp, val);
        }

        // Unsafe mechanics

        private static final sun.misc.Unsafe UNSAFE;
        private static final long itemOffset;
        private static final long nextOffset;

        static {
            try {
                UNSAFE = sun.misc.Unsafe.getUnsafe();
                Class<?> k = Node.class;
                itemOffset = UNSAFE.objectFieldOffset
                    (k.getDeclaredField("item"));
                nextOffset = UNSAFE.objectFieldOffset
                    (k.getDeclaredField("next"));
            } catch (Exception e) {
                throw new Error(e);
            }
        }
    }

    /**
     * A node from which the first live (non-deleted) node (if any)
     * can be reached in O(1) time.
     * Invariants:
     * - all live nodes are reachable from head via succ()
     * - head != null
     * - (tmp = head).next != tmp || tmp != head
     * Non-invariants:
     * - head.item may or may not be null.
     * - it is permitted for tail to lag behind head, that is, for tail
     *   to not be reachable from head!
     */
    private transient volatile Node<E> head;

    /**
     * A node from which the last node on list (that is, the unique
     * node with node.next == null) can be reached in O(1) time.
     * Invariants:
     * - the last node is always reachable from tail via succ()
     * - tail != null
     * Non-invariants:
     * - tail.item may or may not be null.
     * - it is permitted for tail to lag behind head, that is, for tail
     *   to not be reachable from head!
     * - tail.next may or may not be self-pointing to tail.
     */
    private transient volatile Node<E> tail;

    /**
     * Creates a {@code ConcurrentLinkedQueue} that is initially empty.
     */
    public ConcurrentLinkedQueue() {
        head = tail = new Node<E>(null);
    }

    /**
     * Creates a {@code ConcurrentLinkedQueue}
     * initially containing the elements of the given collection,
     * added in traversal order of the collection's iterator.
     *
     * @param c the collection of elements to initially contain
     * @throws NullPointerException if the specified collection or any
     *         of its elements are null
     */
    public ConcurrentLinkedQueue(Collection<? extends E> c) {
        Node<E> h = null, t = null;
        for (E e : c) {
            checkNotNull(e);
            Node<E> newNode = new Node<E>(e);
            if (h == null)
                h = t = newNode;
            else {
                t.lazySetNext(newNode);
                t = newNode;
            }
        }
        if (h == null)
            h = t = new Node<E>(null);
        head = h;
        tail = t;
    }

    // Have to override just to update the javadoc

    /**
     * Inserts the specified element at the tail of this queue.
     * As the queue is unbounded, this method will never throw
     * {@link IllegalStateException} or return {@code false}.
     *  add操作是在链表末尾添加一个元素，其实在内部调用的还是offer操作。
     * @return {@code true} (as specified by {@link Collection#add})
     * @throws NullPointerException if the specified element is null
     */
    public boolean add(E e) {
        return offer(e);
    }

    /**
     * Tries to CAS head to p. If successful, repoint old head to itself
     * as sentinel for succ(), below.
     */
    final void updateHead(Node<E> h, Node<E> p) {
        if (h != p && casHead(h, p))
            h.lazySetNext(h);
    }

    /**
     * Returns the successor of p, or the head node if p.next has been
     * linked to self, which will only be true if traversing with a
     * stale pointer that is now off the list.
     * 获取当前节点的next元素，如果是自引入节点则返回真正的头节点
     */
    final Node<E> succ(Node<E> p) {
        Node<E> next = p.next;
        return (p == next) ? head : next;
    }

    /**
     * offer操作是在队列末尾添加一个元素，如果传递的参数是null则抛出NPE异常，
     * 否则由于ConcurrentLinkedQueue是无界队列，该方法一直会返回true。
     * 另外，由于使用CAS无阻塞算法，因此该方法不会阻塞挂起调用线程。下面具体看下实现原理。
     * 
     * (2）上面是一个线程调用offer方法的情况，如果多个线程同时调用，就会存在多个线程同时执行到代码（5）的情况。
     * 假设线程A调用offer(item1），线程B用offer(item2），同时执行到代码（5)p.casNext(null,newNode）。
     * 由于CAS的比较设置操作是原子性的，所以这里假设线程A先执行了比较设置操作，发现当前p的next节点确实是null，
     * 则会原子性地更新next节点为item，这时候线程B也会判断p的next节点是否为null，
     * 结果发现不是null（因为线程A己经设置了p的next节点为item！），则会跳到代码(3），
     * 然后执行到代码4，这时候的队列分布如图7-4所示。
     * 
     * 根据上面的状态图可知线程B接下来会执行代码（8），然后把q赋给了p，这时候队列状态如图7 - 5所示。
     * 
     * 然后线程B再次跳转到代码（3）执行，当执行到代码（4）时队列状态如图7 - 6所示。
     * 
     * 由于是这时候q==null，所以线程B会执行代码（5），通过CAS操作判断当前p的next节点是否是null，
     * 不是则再次循环尝试，是则使用i tem2替换。假设CAS成功了，那么执行代码（6），由于p !=t，
     * 所以设置tai l节点为it em2，然后退出o f fer方法。这时候队列分布如图7- 7所示。
     * 
     * TODO 分析到现在，就差代码（7）还没走过，其实这一步要在执行poll操作后才会执行。
     * 这里先来看一下执行poll操作后可能会存在的一种情况，如图7-8所示。
     * 
     * 下面分析当队列处于这种状态时调用offer添加元素，执行到代码（4）时的状态图（见图7-9）。
     * 
     * 这里由于q节点不为空并且p==q所以执行代码（7），由于t==tail所以p被赋值为head，然后重新循环，
     * 循环后执行到代码（的，这时候队列状态如图7-10所示。
     * 
     * 这时候由于q==null，所以执行代码（进行CAS操作，如果当前没有其他线程执行offer操作，则CAS操作会成功，
     * p的next节点被设置为新增节点。然后执行代码（6)'由于p!=t所以设置新节点为队列的尾部节点，
     * 现在队列状态如图7-11所示。
     *
     * 要注意的是，这里自引用的节点会被垃圾回收掉。
     * 可见，offer操作的关键步骤是代码（5，通过原子CAS操作来控制某时只有一个线程可以追加元素到队列末尾。
     * 进行CAS竞争失败的线程会通过循环一次次尝试进行CAS操作，直到CAS成功才会返回，
     * 也就是通过使用无限循环不断进行AS尝试方式来替代阻塞算法挂起调用线程。相比阻塞算法，
     * 这是使用CPU资源换取阻塞所带来的开销。
     */
    public boolean offer(E e) {
    	//1.e为空则抛出空指针异常
        checkNotNull(e);
        //2.构造Node节点，在构造函数内部调用 UNSAFE.putObject
        final Node<E> newNode = new Node<E>(e);
        //3.从尾节点进行插入
        for (Node<E> t = tail, p = t;;) {
            Node<E> q = p.next;
            //4.如果q为空，则说明p是尾节点，则进行插入
            if (q == null) {
                // p is last node
            	//5.使用CAS设置P节点的next节点
                if (p.casNext(null, newNode)) {
                    // Successful CAS is the linearization point
                    // for e to become an element of this queue,
                    // and for newNode to become "live".
                	//6.CAS成功，则说明新增节点已经被放入链表，然后设置当前尾节点（包含head，第1，3,5，...个节点为尾节点）
                    if (p != t) // hop two nodes at a time
                        casTail(t, newNode);  // Failure is OK.
                    return true;
                }
                // Lost CAS race to another thread; re-read next
            }
            else if (p == q)
            	// 7.多线程操作时，由于poll操作移除元素后可能会把head变为自引用，
            	// 也就是head的next变为head，这里需要重新找新的head
                // We have fallen off list.  If tail is unchanged, it
                // will also be off-list, in which case we need to
                // jump to head, from which all live nodes are always
                // reachable.  Else the new tail is a better bet.
                p = (t != (t = tail)) ? t : head;
            else
            	//8.寻找尾节点
                // Check for tail updates after two hops.
                p = (p != t && t != (t = tail)) ? t : q;
        }
    }

    /**
     * <p>poll操作是在队列头部获取并移除一个元素，
     * 如果队列为空则返回null。下面看看它的实现原理。
     * poll操作是从队头获取元素，所以代码（2）内层循环是从head节点开始迭代，
     * 代码（3）获取当前队列头的节点，队列一开始为空时队列状态如图7-12所示。
     * 
     * <p>由于head节指向的是item为null哨兵节点，所以会执行到代码（6），
     * 假设这个过程中没有线程调用offer方法，则此时q等于null，这时候队列状态如图7-13示。
     * 
     * <p>所以会执行updateHead方法，由于h等于p所以没有设置头节点，
     * poll方法直接返回null 
     * <p>II.假设执行到代码（6）时已经有其他线程调用了offer方法并成功添加一个元素到队列，
     * 这时候q指向的是新增元素的节点，此时队列状态如图7-14所示。
     * 所以代码（6）判断的结果为false，然后会转向执行代码（7），
     * 而此时p不等于q,所以转向执行代码（8），执行的结果是p指向了节点q，
     * 此时队列状态如图7-15示。
     * 
     * <p>然后程序转向执行代码（3)，p现在指向的元素值不为null，
     * 则执行p.casltem(item,null）通过CAS操作尝试设置p的item值为null，
     * 如果此时没有其他线程进行poll操作，则CAS成功会执行代码（5），
     * 由于此时p!=h所以设置头节点为p，并设置h的next节点为自己，
     * poll然后返回被从队列移除的节点值item。此队列状态如图7-16所示。
     * 这个状态就是在讲解offer操作时，。offer代码的执行路径(7）的状态。
     * <p>
     * III.假如现在一个线程调用了poll操作，则在执行代码（4）时队列状态如图7-17所示。
     * 
     * <p>这时候执行代码（6）返回null。IV.现在poll的代码还有分支（7）没有执行过，那么什么时候会执行呢？
     * 下面来看看。假设线程A执行poll操作时当前队列状态如图7-18所示。
     * 
     *<p> 那么执行p.casltem(item,null）通过CAS操作尝试设置p的item值为null，
     * 假设CAS设置成功则标记该节点并从队列中将其移除，此时队列状态如图7-19所示。
     * 然后，由于p!=h，所以会执行updateHead方法，假如线程A执行updateHead前另外一个线程B开始poll操作，
     * 这时候线程B的p指向head节点，但是还没有执行到代码（6)'这时候队列状态如图7-20所示
     * 
     * <p>然后线程A执行updateHead操作，执行完毕后线程A退出，这时候队列状态如图7-21所示。
     * 然后线程B继续执行代码（6),q=p.next，由于该节点是自引用节点，
     * 所以p=q，所以会执行代码（7）跳到外层循环restartFrornHead获取当前队列头head，现在的状态如7-22所示。
     * 总结：poll方法在移除一个元素时，只是简单地使用CAS操作把当前节点的item值设置为null，
     * 然后通过重新设置头节点将该元素从队列里面移除，被移除的节点就成了孤立节点，
     * 这个节点会在垃圾回收时被回收掉。
     * <p>另外，如果在执行分支中发现头节点被修改了，要跳到外层循环重新获取新的头节点。
     */
    public E poll() {
    	//1.goto标记
        restartFromHead:
       //2.无限循环
        for (;;) {
            for (Node<E> h = head, p = h, q;;) {
            	//3.保存当前节点的值
                E item = p.item;
                //4.当前节点有值则CAS变为空
                if (item != null && p.casItem(item, null)) {
                    // Successful CAS is the linearization point
                    // for item to be removed from this queue.
                	//5.CAS成功则标记当前节点并从链表中移除
                    if (p != h) // hop two nodes at a time
                        updateHead(h, ((q = p.next) != null) ? q : p);
                    return item;
                }
                //6.当前队列为空则返回null
                else if ((q = p.next) == null) {
                    updateHead(h, p);
                    return null;
                }
                /*7.当前节点被自引用了，则重新寻找新的队列头节点*/
                else if (p == q)
                    continue restartFromHead;
                else//8
                    p = q;
            }
        }
    }
    /**
     * peek操作是获取队列头部一个元素（只获取不移除〉，如果队列为空则返回null。下面看下其实现原理。
     * <p>Peek操作的代码结构与poll操作类似，不同之处在于代码(3）中少了castitem操作。
     * 其实这很正常，因为peek只是获取队列头元素值，并不清空其值。
     * 根据前面的介绍我们知道第一次执行offer后head指向的是哨兵节点（也就是item为null的节点〉，
     * 那么第一次执行peek时在代码（3）中会发现item=null，然后执行q=p.next，
     * 这时候q节点指向的才是队列里面第一个真正的元素，或者如果队列为null则q指向null。
     * 当队列为空时队列状态如图7 -23所示。
     * <p>这时候执行updateHead，由于h节点等于p节点，
     * 所以不进行任何操作，然后peek操作会返回null。队列中至少有一个元素时（这里假设只有一个），
     * 队列状态如图7-24所示。
     * <p>
     * 这时候执行代码(5),p指向了q点，然后执行代码（3），
     * 此时队列状态如图7-25所示
     * <p>执行代码3时发现item不为null，所以执行updateHead方法，由于h!=p，
     * 所以设置头节点，设置后队列状态如图7-26所示。就是剔除了哨兵节点。
     * <p>
     * 总结：peek操作的代码与poll操作类似，
     * 前者只获取队列头元素但是并不从队列里将它删除，
     * 而后者获取后需要从队列里面将它删除。
     * 另外，在第一次调用peek操作时，会删除哨兵节点，
     * 并让队列的head节点指向队列里面第一个元素或者null。	
     * 
     * 
     */
    public E peek() {
    	//1
        restartFromHead:
        for (;;) {
            for (Node<E> h = head, p = h, q;;) {
            	//2
                E item = p.item;
                //3
                if (item != null || (q = p.next) == null) {
                    updateHead(h, p);
                    return item;
                }
                //4
                else if (p == q)
                    continue restartFromHead;
                //5
                else
                    p = q;
            }
        }
    }

    /**
     * Returns the first live (non-deleted) node on list, or null if none.
     * This is yet another variant of poll/peek; here returning the
     * first node, not element.  We could make peek() a wrapper around
     * first(), but that would cost an extra volatile read of item,
     * and the need to add a retry loop to deal with the possibility
     * of losing a race to a concurrent poll().
     * 获取第一个队列元素（哨兵元素不算），没有则为null
     */
    Node<E> first() {
        restartFromHead:
        for (;;) {
            for (Node<E> h = head, p = h, q;;) {
                boolean hasItem = (p.item != null);
                if (hasItem || (q = p.next) == null) {
                    updateHead(h, p);
                    return hasItem ? p : null;
                }
                else if (p == q)
                    continue restartFromHead;
                else
                    p = q;
            }
        }
    }

    /**
     * Returns {@code true} if this queue contains no elements.
     *
     * @return {@code true} if this queue contains no elements
     */
    public boolean isEmpty() {
        return first() == null;
    }

    /**
     * Returns the number of elements in this queue.  If this queue
     * contains more than {@code Integer.MAX_VALUE} elements, returns
     * {@code Integer.MAX_VALUE}.
     *
     * <p>Beware that, unlike in most collections, this method is
     * <em>NOT</em> a constant-time operation. Because of the
     * asynchronous nature of these queues, determining the current
     * number of elements requires an O(n) traversal.
     * Additionally, if elements are added or removed during execution
     * of this method, the returned result may be inaccurate.  Thus,
     * this method is typically not very useful in concurrent
     * applications.
     *  计算当前队列元素个数，
     *  在并发环境下不是很有用，
     *  因为CAS没有加锁，
     *  所以从调用size函数到返回结果期间有可能增删元素，
     *  导致统计的元素个数不精确。
     * @return the number of elements in this queue
     */
    public int size() {
        int count = 0;
        for (Node<E> p = first(); p != null; p = succ(p))
            if (p.item != null)
                // Collection.size() spec says to max out
                if (++count == Integer.MAX_VALUE)
                    break;
        return count;
    }

    /**
     * Returns {@code true} if this queue contains the specified element.
     * More formally, returns {@code true} if and only if this queue contains
     * at least one element {@code e} such that {@code o.equals(e)}.
     *  判断队列里面是否含有指定对象，由于是遍历整个队列，
     *  所以像size操作一样结果也不是那么精确，有可能调用该方法时元素还在队列里面，
     *  但是遍历过程中其他线程才把该元素删除了，那么就会返回false
     * @param o object to be checked for containment in this queue
     * @return {@code true} if this queue contains the specified element
     */
    public boolean contains(Object o) {
        if (o == null) return false;
        for (Node<E> p = first(); p != null; p = succ(p)) {
            E item = p.item;
            if (item != null && o.equals(item))
                return true;
        }
        return false;
    }

    /**
     * Removes a single instance of the specified element from this queue,
     * if it is present.  More formally, removes an element {@code e} such
     * that {@code o.equals(e)}, if this queue contains one or more such
     * elements.
     * Returns {@code true} if this queue contained the specified element
     * (or equivalently, if this queue changed as a result of the call).
     * 如果队列里面存在该元素则删除该元素，
     * 如果存在多个则删除第一个，并返回true,否则返回false。
     * @param o element to be removed from this queue, if present
     * @return {@code true} if this queue changed as a result of the call
     */
    public boolean remove(Object o) {
    	//如果为空直接返回false
        if (o != null) {
            Node<E> next, pred = null;
            for (Node<E> p = first(); p != null; pred = p, p = next) {
                boolean removed = false;
                E item = p.item;
//                相等则使用CAS设置为null，同时一个线程操作成功，失败的线程循环查找队列中是否有匹配的其他元素。
                if (item != null) {
                    if (!o.equals(item)) {
                        next = succ(p);
                        continue;
                    }
                    removed = p.casItem(item, null);
                }
              //获取next元素
                next = succ(p);
                //如果有前驱节点，并且next节点不为空则链接前驱节点到next节点
                if (pred != null && next != null) // unlink
                    pred.casNext(p, next);
                if (removed)
                    return true;
            }
        }
        return false;
    }

    /**
     * Appends all of the elements in the specified collection to the end of
     * this queue, in the order that they are returned by the specified
     * collection's iterator.  Attempts to {@code addAll} of a queue to
     * itself result in {@code IllegalArgumentException}.
     *
     * @param c the elements to be inserted into this queue
     * @return {@code true} if this queue changed as a result of the call
     * @throws NullPointerException if the specified collection or any
     *         of its elements are null
     * @throws IllegalArgumentException if the collection is this queue
     */
    public boolean addAll(Collection<? extends E> c) {
        if (c == this)
            // As historically specified in AbstractQueue#addAll
            throw new IllegalArgumentException();

        // Copy c into a private chain of Nodes
        Node<E> beginningOfTheEnd = null, last = null;
        for (E e : c) {
            checkNotNull(e);
            Node<E> newNode = new Node<E>(e);
            if (beginningOfTheEnd == null)
                beginningOfTheEnd = last = newNode;
            else {
                last.lazySetNext(newNode);
                last = newNode;
            }
        }
        if (beginningOfTheEnd == null)
            return false;

        // Atomically append the chain at the tail of this collection
        for (Node<E> t = tail, p = t;;) {
            Node<E> q = p.next;
            if (q == null) {
                // p is last node
                if (p.casNext(null, beginningOfTheEnd)) {
                    // Successful CAS is the linearization point
                    // for all elements to be added to this queue.
                    if (!casTail(t, last)) {
                        // Try a little harder to update tail,
                        // since we may be adding many elements.
                        t = tail;
                        if (last.next == null)
                            casTail(t, last);
                    }
                    return true;
                }
                // Lost CAS race to another thread; re-read next
            }
            else if (p == q)
                // We have fallen off list.  If tail is unchanged, it
                // will also be off-list, in which case we need to
                // jump to head, from which all live nodes are always
                // reachable.  Else the new tail is a better bet.
                p = (t != (t = tail)) ? t : head;
            else
                // Check for tail updates after two hops.
                p = (p != t && t != (t = tail)) ? t : q;
        }
    }

    /**
     * Returns an array containing all of the elements in this queue, in
     * proper sequence.
     *
     * <p>The returned array will be "safe" in that no references to it are
     * maintained by this queue.  (In other words, this method must allocate
     * a new array).  The caller is thus free to modify the returned array.
     *
     * <p>This method acts as bridge between array-based and collection-based
     * APIs.
     *
     * @return an array containing all of the elements in this queue
     */
    public Object[] toArray() {
        // Use ArrayList to deal with resizing.
        ArrayList<E> al = new ArrayList<E>();
        for (Node<E> p = first(); p != null; p = succ(p)) {
            E item = p.item;
            if (item != null)
                al.add(item);
        }
        return al.toArray();
    }

    /**
     * Returns an array containing all of the elements in this queue, in
     * proper sequence; the runtime type of the returned array is that of
     * the specified array.  If the queue fits in the specified array, it
     * is returned therein.  Otherwise, a new array is allocated with the
     * runtime type of the specified array and the size of this queue.
     *
     * <p>If this queue fits in the specified array with room to spare
     * (i.e., the array has more elements than this queue), the element in
     * the array immediately following the end of the queue is set to
     * {@code null}.
     *
     * <p>Like the {@link #toArray()} method, this method acts as bridge between
     * array-based and collection-based APIs.  Further, this method allows
     * precise control over the runtime type of the output array, and may,
     * under certain circumstances, be used to save allocation costs.
     *
     * <p>Suppose {@code x} is a queue known to contain only strings.
     * The following code can be used to dump the queue into a newly
     * allocated array of {@code String}:
     *
     *  <pre> {@code String[] y = x.toArray(new String[0]);}</pre>
     *
     * Note that {@code toArray(new Object[0])} is identical in function to
     * {@code toArray()}.
     *
     * @param a the array into which the elements of the queue are to
     *          be stored, if it is big enough; otherwise, a new array of the
     *          same runtime type is allocated for this purpose
     * @return an array containing all of the elements in this queue
     * @throws ArrayStoreException if the runtime type of the specified array
     *         is not a supertype of the runtime type of every element in
     *         this queue
     * @throws NullPointerException if the specified array is null
     */
    @SuppressWarnings("unchecked")
    public <T> T[] toArray(T[] a) {
        // try to use sent-in array
        int k = 0;
        Node<E> p;
        for (p = first(); p != null && k < a.length; p = succ(p)) {
            E item = p.item;
            if (item != null)
                a[k++] = (T)item;
        }
        if (p == null) {
            if (k < a.length)
                a[k] = null;
            return a;
        }

        // If won't fit, use ArrayList version
        ArrayList<E> al = new ArrayList<E>();
        for (Node<E> q = first(); q != null; q = succ(q)) {
            E item = q.item;
            if (item != null)
                al.add(item);
        }
        return al.toArray(a);
    }

    /**
     * Returns an iterator over the elements in this queue in proper sequence.
     * The elements will be returned in order from first (head) to last (tail).
     *
     * <p>The returned iterator is
     * <a href="package-summary.html#Weakly"><i>weakly consistent</i></a>.
     *
     * @return an iterator over the elements in this queue in proper sequence
     */
    public Iterator<E> iterator() {
        return new Itr();
    }

    private class Itr implements Iterator<E> {
        /**
         * Next node to return item for.
         */
        private Node<E> nextNode;

        /**
         * nextItem holds on to item fields because once we claim
         * that an element exists in hasNext(), we must return it in
         * the following next() call even if it was in the process of
         * being removed when hasNext() was called.
         */
        private E nextItem;

        /**
         * Node of the last returned item, to support remove.
         */
        private Node<E> lastRet;

        Itr() {
            advance();
        }

        /**
         * Moves to next valid node and returns item to return for
         * next(), or null if no such.
         */
        private E advance() {
            lastRet = nextNode;
            E x = nextItem;

            Node<E> pred, p;
            if (nextNode == null) {
                p = first();
                pred = null;
            } else {
                pred = nextNode;
                p = succ(nextNode);
            }

            for (;;) {
                if (p == null) {
                    nextNode = null;
                    nextItem = null;
                    return x;
                }
                E item = p.item;
                if (item != null) {
                    nextNode = p;
                    nextItem = item;
                    return x;
                } else {
                    // skip over nulls
                    Node<E> next = succ(p);
                    if (pred != null && next != null)
                        pred.casNext(p, next);
                    p = next;
                }
            }
        }

        public boolean hasNext() {
            return nextNode != null;
        }

        public E next() {
            if (nextNode == null) throw new NoSuchElementException();
            return advance();
        }

        public void remove() {
            Node<E> l = lastRet;
            if (l == null) throw new IllegalStateException();
            // rely on a future traversal to relink.
            l.item = null;
            lastRet = null;
        }
    }

    /**
     * Saves this queue to a stream (that is, serializes it).
     *
     * @param s the stream
     * @throws java.io.IOException if an I/O error occurs
     * @serialData All of the elements (each an {@code E}) in
     * the proper order, followed by a null
     */
    private void writeObject(java.io.ObjectOutputStream s)
        throws java.io.IOException {

        // Write out any hidden stuff
        s.defaultWriteObject();

        // Write out all elements in the proper order.
        for (Node<E> p = first(); p != null; p = succ(p)) {
            Object item = p.item;
            if (item != null)
                s.writeObject(item);
        }

        // Use trailing null as sentinel
        s.writeObject(null);
    }

    /**
     * Reconstitutes this queue from a stream (that is, deserializes it).
     * @param s the stream
     * @throws ClassNotFoundException if the class of a serialized object
     *         could not be found
     * @throws java.io.IOException if an I/O error occurs
     */
    private void readObject(java.io.ObjectInputStream s)
        throws java.io.IOException, ClassNotFoundException {
        s.defaultReadObject();

        // Read in elements until trailing null sentinel found
        Node<E> h = null, t = null;
        Object item;
        while ((item = s.readObject()) != null) {
            @SuppressWarnings("unchecked")
            Node<E> newNode = new Node<E>((E) item);
            if (h == null)
                h = t = newNode;
            else {
                t.lazySetNext(newNode);
                t = newNode;
            }
        }
        if (h == null)
            h = t = new Node<E>(null);
        head = h;
        tail = t;
    }

    /** A customized variant of Spliterators.IteratorSpliterator */
    static final class CLQSpliterator<E> implements Spliterator<E> {
        static final int MAX_BATCH = 1 << 25;  // max batch array size;
        final ConcurrentLinkedQueue<E> queue;
        Node<E> current;    // current node; null until initialized
        int batch;          // batch size for splits
        boolean exhausted;  // true when no more nodes
        CLQSpliterator(ConcurrentLinkedQueue<E> queue) {
            this.queue = queue;
        }

        public Spliterator<E> trySplit() {
            Node<E> p;
            final ConcurrentLinkedQueue<E> q = this.queue;
            int b = batch;
            int n = (b <= 0) ? 1 : (b >= MAX_BATCH) ? MAX_BATCH : b + 1;
            if (!exhausted &&
                ((p = current) != null || (p = q.first()) != null) &&
                p.next != null) {
                Object[] a = new Object[n];
                int i = 0;
                do {
                    if ((a[i] = p.item) != null)
                        ++i;
                    if (p == (p = p.next))
                        p = q.first();
                } while (p != null && i < n);
                if ((current = p) == null)
                    exhausted = true;
                if (i > 0) {
                    batch = i;
                    return Spliterators.spliterator
                        (a, 0, i, Spliterator.ORDERED | Spliterator.NONNULL |
                         Spliterator.CONCURRENT);
                }
            }
            return null;
        }

        public void forEachRemaining(Consumer<? super E> action) {
            Node<E> p;
            if (action == null) throw new NullPointerException();
            final ConcurrentLinkedQueue<E> q = this.queue;
            if (!exhausted &&
                ((p = current) != null || (p = q.first()) != null)) {
                exhausted = true;
                do {
                    E e = p.item;
                    if (p == (p = p.next))
                        p = q.first();
                    if (e != null)
                        action.accept(e);
                } while (p != null);
            }
        }

        public boolean tryAdvance(Consumer<? super E> action) {
            Node<E> p;
            if (action == null) throw new NullPointerException();
            final ConcurrentLinkedQueue<E> q = this.queue;
            if (!exhausted &&
                ((p = current) != null || (p = q.first()) != null)) {
                E e;
                do {
                    e = p.item;
                    if (p == (p = p.next))
                        p = q.first();
                } while (e == null && p != null);
                if ((current = p) == null)
                    exhausted = true;
                if (e != null) {
                    action.accept(e);
                    return true;
                }
            }
            return false;
        }

        public long estimateSize() { return Long.MAX_VALUE; }

        public int characteristics() {
            return Spliterator.ORDERED | Spliterator.NONNULL |
                Spliterator.CONCURRENT;
        }
    }

    /**
     * Returns a {@link Spliterator} over the elements in this queue.
     *
     * <p>The returned spliterator is
     * <a href="package-summary.html#Weakly"><i>weakly consistent</i></a>.
     *
     * <p>The {@code Spliterator} reports {@link Spliterator#CONCURRENT},
     * {@link Spliterator#ORDERED}, and {@link Spliterator#NONNULL}.
     *
     * @implNote
     * The {@code Spliterator} implements {@code trySplit} to permit limited
     * parallelism.
     *
     * @return a {@code Spliterator} over the elements in this queue
     * @since 1.8
     */
    @Override
    public Spliterator<E> spliterator() {
        return new CLQSpliterator<E>(this);
    }

    /**
     * Throws NullPointerException if argument is null.
     *
     * @param v the element
     */
    private static void checkNotNull(Object v) {
        if (v == null)
            throw new NullPointerException();
    }

    private boolean casTail(Node<E> cmp, Node<E> val) {
        return UNSAFE.compareAndSwapObject(this, tailOffset, cmp, val);
    }

    private boolean casHead(Node<E> cmp, Node<E> val) {
        return UNSAFE.compareAndSwapObject(this, headOffset, cmp, val);
    }

    // Unsafe mechanics

    private static final sun.misc.Unsafe UNSAFE;
    private static final long headOffset;
    private static final long tailOffset;
    static {
        try {
            UNSAFE = sun.misc.Unsafe.getUnsafe();
            Class<?> k = ConcurrentLinkedQueue.class;
            headOffset = UNSAFE.objectFieldOffset
                (k.getDeclaredField("head"));
            tailOffset = UNSAFE.objectFieldOffset
                (k.getDeclaredField("tail"));
        } catch (Exception e) {
            throw new Error(e);
        }
    }
}

