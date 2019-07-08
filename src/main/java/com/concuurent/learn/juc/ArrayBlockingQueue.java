package com.concuurent.learn.juc;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.AbstractQueue;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.lang.ref.WeakReference;
import java.util.Spliterators;
import java.util.Spliterator;

/**
 * ArrayBlockingQueue的内部有一数组items，用来存放队列元素，putindex变量表示入队元素下标，<br>
 * takelndex是出队下标，count统计队列元素个数。从定义可知，这些变量并没有使用volatile修饰，<br>
 * 这是因为访问这些变量都是在锁块内，而加锁己经保证了锁块内变量的内存可见性了。<br>
 * 另外有个独占锁lock用来保证出、入队操作的原子性，这保证了同时只有一线程可以进行入队、出队操作。<br>
 * 另外，notEmpty、notFull条件变量用来进行出、入队的同步。<br>
 * @author Wesley
 * @param <E>
 * 2019年7月3日下午1:55:23
 * @Version 1.0
 * 
 */
public class ArrayBlockingQueue<E> extends AbstractQueue<E>
       implements BlockingQueue<E>, java.io.Serializable {

   /**
    * Serialization ID. This class relies on default serialization
    * even for the items array, which is default-serialized, even if
    * it is empty. Otherwise it could not be declared final, which is
    * necessary here.
    */
   private static final long serialVersionUID = -817911632652898426L;

   /** The queued items */
   final Object[] items;

   /** items index for next take, poll, peek or remove */
   int takeIndex;

   /** items index for next put, offer, or add */
   int putIndex;

   /** Number of elements in the queue */
   int count;

   /*
    * Concurrency control uses the classic two-condition algorithm
    * found in any textbook.
    */

   /** Main lock guarding all access */
   final ReentrantLock lock;

   /** Condition for waiting takes */
   private final Condition notEmpty;

   /** Condition for waiting puts */
   private final Condition notFull;

   /**
    * Shared state for currently active iterators, or null if there
    * are known not to be any.  Allows queue operations to update
    * iterator state.
    */
   transient Itrs itrs = null;

   // Internal helper methods

   /**
    * Circularly decrement i.
    */
   final int dec(int i) {
       return ((i == 0) ? items.length : i) - 1;
   }

   /**
    * Returns item at index i.
    */
   @SuppressWarnings("unchecked")
   final E itemAt(int i) {
       return (E) items[i];
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

   /**
    * Inserts element at current put position, advances, and signals.
    * Call only when holding lock.
    * 首先把当前元素放入items数组，然后计算下一个元素应该存放的下标位置，<br>
    * 并递增元素个数计数器，最后激活notEmpty的条件队列中因为调用take操作而被阻塞的一个线程。<br>
    * 这里由于在操作共享变量count前加了锁，所以不存在内存不可见问题，<br>
    * 加过锁后获取的共享变量都是从主内存获取的，而不是从CPU缓存或者寄存器获取。<br>
    */
   private void enqueue(E x) {
        assert lock.getHoldCount() == 1;
       assert items[putIndex] == null;
	   //6.元素入队
       final Object[] items = this.items;
       items[putIndex] = x;
       //7.计算下一个元素应该存放的位置
       if (++putIndex == items.length)
           putIndex = 0;
       //递增元素个数计数器
       count++;
       //8
       notEmpty.signal();
   }

   /**
    * Extracts element at current take position, advances, and signals.
    * Call only when holding lock.
    * 首先获取当前队头元素并将其保存到局部变量，然后重置队元素为null，
    * 并重新设置队头下标，递减元素计数器，
    * 最后发送信号激活notFull的条件队列里面一个因为调用put方法而被阻塞的线程。
    */
   private E dequeue() {
       // assert lock.getHoldCount() == 1;
       // assert items[takeIndex] != null;
       final Object[] items = this.items;
       //获取元素值
       @SuppressWarnings("unchecked")
       E x = (E) items[takeIndex];
       //数组中的值为Null
       items[takeIndex] = null;
       //队头指针计算，队列元素个数减1
       if (++takeIndex == items.length)
           takeIndex = 0;
       count--;
       if (itrs != null)
           itrs.elementDequeued();
       //发送信号激活条件队列nofFull里面的一个线程。
       notFull.signal();
       return x;
   }

   /**
    * Deletes item at array index removeIndex.
    * Utility for remove(Object) and iterator.remove.
    * Call only when holding lock.
    */
   void removeAt(final int removeIndex) {
       // assert lock.getHoldCount() == 1;
       // assert items[removeIndex] != null;
       // assert removeIndex >= 0 && removeIndex < items.length;
       final Object[] items = this.items;
       if (removeIndex == takeIndex) {
           // removing front item; just advance
           items[takeIndex] = null;
           if (++takeIndex == items.length)
               takeIndex = 0;
           count--;
           if (itrs != null)
               itrs.elementDequeued();
       } else {
           // an "interior" remove

           // slide over all others up through putIndex.
           final int putIndex = this.putIndex;
           for (int i = removeIndex;;) {
               int next = i + 1;
               if (next == items.length)
                   next = 0;
               if (next != putIndex) {
                   items[i] = items[next];
                   i = next;
               } else {
                   items[i] = null;
                   this.putIndex = i;
                   break;
               }
           }
           count--;
           if (itrs != null)
               itrs.removedAt(removeIndex);
       }
       notFull.signal();
   }

   /**
    * Creates an {@code ArrayBlockingQueue} with the given (fixed)
    * capacity and default access policy.
    * 由于ArrayBlockingQueue是有界队列，所以构造函数必须传入队列大小参数。
    * @param capacity the capacity of this queue
    * @throws IllegalArgumentException if {@code capacity < 1}
    */
   public ArrayBlockingQueue(int capacity) {
       this(capacity, false);
   }

   /**
    * Creates an {@code ArrayBlockingQueue} with the given (fixed)
    * capacity and the specified access policy.
    *
    * @param capacity the capacity of this queue
    * @param fair if {@code true} then queue accesses for threads blocked
    *        on insertion or removal, are processed in FIFO order;
    *        if {@code false} the access order is unspecified.
    * @throws IllegalArgumentException if {@code capacity < 1}
    * 默认情况下使用ReentrantLock提供的非公平独占锁进行出、入队操作的同步。
    */
   public ArrayBlockingQueue(int capacity, boolean fair) {
       if (capacity <= 0)
           throw new IllegalArgumentException();
       this.items = new Object[capacity];
       lock = new ReentrantLock(fair);
       notEmpty = lock.newCondition();
       notFull =  lock.newCondition();
   }

   /**
    * Creates an {@code ArrayBlockingQueue} with the given (fixed)
    * capacity, the specified access policy and initially containing the
    * elements of the given collection,
    * added in traversal order of the collection's iterator.
    *
    * @param capacity the capacity of this queue
    * @param fair if {@code true} then queue accesses for threads blocked
    *        on insertion or removal, are processed in FIFO order;
    *        if {@code false} the access order is unspecified.
    * @param c the collection of elements to initially contain
    * @throws IllegalArgumentException if {@code capacity} is less than
    *         {@code c.size()}, or less than 1.
    * @throws NullPointerException if the specified collection or any
    *         of its elements are null
    */
   public ArrayBlockingQueue(int capacity, boolean fair,
                             Collection<? extends E> c) {
       this(capacity, fair);

       final ReentrantLock lock = this.lock;
       lock.lock(); // Lock only for visibility, not mutual exclusion
       try {
           int i = 0;
           try {
               for (E e : c) {
                   checkNotNull(e);
                   items[i++] = e;
               }
           } catch (ArrayIndexOutOfBoundsException ex) {
               throw new IllegalArgumentException();
           }
           count = i;
           putIndex = (i == capacity) ? 0 : i;
       } finally {
           lock.unlock();
       }
   }

   /**
    * Inserts the specified element at the tail of this queue if it is
    * possible to do so immediately without exceeding the queue's capacity,
    * returning {@code true} upon success and throwing an
    * {@code IllegalStateException} if this queue is full.
    *
    * @param e the element to add
    * @return {@code true} (as specified by {@link Collection#add})
    * @throws IllegalStateException if this queue is full
    * @throws NullPointerException if the specified element is null
    */
   public boolean add(E e) {
       return super.add(e);
   }

   /**
    * Inserts the specified element at the tail of this queue if it is
    * possible to do so immediately without exceeding the queue's capacity,
    * returning {@code true} upon success and {@code false} if this queue
    * is full.  This method is generally preferable to method {@link #add},
    * which can fail to insert an element only by throwing an exception.
    * <p>
	 * 向队列尾部插入一个元素，如果队列有空闲空间则插入成功后返回true，<br>
	 * 如果队列己满则丢弃当前元素然后返回false。<br>
	 * 如果e元素为null则抛出NullPointerException异常。另外，该方法是不阻塞的。<br>
    * @throws NullPointerException if the specified element is null
    */
   public boolean offer(E e) {
	   //如果e为null，抛出空指针异常
       checkNotNull(e);
       //2.获取独占锁 
       //获取独占锁，当前线程获取该锁后，
       //其他入队和出队操作的线程都会被阻塞挂起而后被放入lock锁的AQS阻塞队列。
       final ReentrantLock lock = this.lock;
       lock.lock();
       try {
    	   //3.如果队列满，则返回false 判断如果队列满则直接返回false，否则调用enqueue方法后返回true,
           if (count == items.length)
               return false;
           else {
        	   //4.否则插入元素 
               enqueue(e);
               return true;
           }
       } finally {
    	   //5.释放锁，然后会把修改的共享变量值（比如count的值〉刷新回主内存中，<br>
    	   // 这样其他线程通过加锁再次读取这些共享变量时，就可以看到最新的值。
           lock.unlock();
       }
   }

   /**
    * Inserts the specified element at the tail of this queue, waiting
    * for space to become available if the queue is full.
    * 向队列尾部插入一个元素，如果队列有空闲则插入后直接返回true，<br>
    * 如果队列己满则阻塞当前线程直到队列有空闲井插入成功后返回true，<br>
    * 如果在阻塞时被其他线程设置了中断标志，<br>
    * 则被阻塞线程会抛出InteηuptedException异常而返回。<br>
    * 另外，如果e元素为null则抛出NullPointerException异常。
    * @throws InterruptedException {@inheritDoc}
    * @throws NullPointerException {@inheritDoc}
    */
   public void put(E e) throws InterruptedException {
	   //1 如果e为null 抛出空指针异常
       checkNotNull(e);
       final ReentrantLock lock = this.lock;
       //获取锁，可被中断  获取锁的过程中当前线程被其他线程中断了，则当前线程会抛出InterruptedException异常而退出。
       lock.lockInterruptibly();
       try {
    	   //如果队列满，则把当前线程放入notFull管理的条件队列
    	   //判断如果当前队列己满，则把当前线程阻塞挂起后放入notFull的条件队列，
    	   //注意这里也是使用了while循环而不是if语句。
           while (count == items.length)
               notFull.await();
           //判断如果队列不满则插入当前元素
           enqueue(e);
       } finally {
           lock.unlock();
       }
   }

   /**
    * Inserts the specified element at the tail of this queue, waiting
    * up to the specified wait time for space to become available if
    * the queue is full.
    *
    * @throws InterruptedException {@inheritDoc}
    * @throws NullPointerException {@inheritDoc}
    */
   public boolean offer(E e, long timeout, TimeUnit unit)
       throws InterruptedException {

       checkNotNull(e);
       long nanos = unit.toNanos(timeout);
       final ReentrantLock lock = this.lock;
       lock.lockInterruptibly();
       try {
           while (count == items.length) {
               if (nanos <= 0)
                   return false;
               nanos = notFull.awaitNanos(nanos);
           }
           enqueue(e);
           return true;
       } finally {
           lock.unlock();
       }
   }

   /**
    * 队列头部获取并移除一个元素，如果队列为空则返回null，该方法是不阻塞的。
    */
   public E poll() {
	   //1.获取锁
       final ReentrantLock lock = this.lock;
       lock.lock();
       try {
    	   //当前队列为空则返回null，否调用dequeue获取
           return (count == 0) ? null : dequeue();
       } finally {
    	   //释放锁
           lock.unlock();
       }
   }
   /**
    * 获取当前队列头部元素并从队列里面移除它。
    * 如果队列为空则阻塞当前线程直到队列不为空然后返回元素，
    * 如果在阻塞时被其他线程设置了中断标志，
    * 则被阻塞线程会抛出InterruptedException异常而返回。
    */
   public E take() throws InterruptedException {
	   //获取锁
       final ReentrantLock lock = this.lock;
       
       lock.lockInterruptibly();
       try {
    	   //2队列为空则等待直到队列中有元素
           while (count == 0)
               notEmpty.await();
           //获取头元素
           return dequeue();
       } finally {
    	   //释放锁
           lock.unlock();
       }
       /*
        * take操作的代码也比较简单，与poll相比只是代码（2）不同。
        * 在这里，如果队列为空则把当前线程挂起后放入notEmpty的条件队列，
        * 等其他线程调用notEmpty.signal（）方法后再返回。
        * 需要注意的是，这里也是使用while循环进行检测井等待而不是使用if语句。
        */
   }

   public E poll(long timeout, TimeUnit unit) throws InterruptedException {
       long nanos = unit.toNanos(timeout);
       final ReentrantLock lock = this.lock;
       lock.lockInterruptibly();
       try {
           while (count == 0) {
               if (nanos <= 0)
                   return null;
               nanos = notEmpty.awaitNanos(nanos);
           }
           return dequeue();
       } finally {
           lock.unlock();
       }
   }
   /**
    * 获取队列头部元素但是不从队列里面移除它，
    * 如果队列为空则返回null，该方法是不阻塞的。
    */
   public E peek() {
	   //获取锁
       final ReentrantLock lock = this.lock;
       lock.lock();
       try {
    	   //2 从数组items中获取当前队头下标的值并返回
           return itemAt(takeIndex); // null when queue is empty
       } finally {
    	   //3 在返回前释放获取的锁。
           lock.unlock();
       }
   }

   // this doc comment is overridden to remove the reference to collections
   // greater in size than Integer.MAX_VALUE
   /**
    * Returns the number of elements in this queue.
    * 计算当前队列的元素个数
    * @return the number of elements in this queue
    * size操作比较简单，获取锁后直接返回count并在返回前释放锁。<br>
    * 也许你会问，这里又没有修改count的值，只是简单地获取，为何要加锁呢？<br>
    * 其实如果count被声明为volatile的这里就不需要加锁了，<br>
    * 因为volatile类型的变量保证了内存的可见性，<br>
    * 而ArrayBlockingQueue中的count并没有声明为volatile的，<br>
    * 这是因为count操作都是在获取锁后进行的，而获取锁的语义之一是，<br>
    * 获取锁后访问的变量都是从主内存获取的，这保证了变量的内存可见性。<br>
    */
   public int size() {
       final ReentrantLock lock = this.lock;
       lock.lock();
       try {
           return count;
       } finally {
           lock.unlock();
       }
   }

   // this doc comment is a modified copy of the inherited doc comment,
   // without the reference to unlimited queues.
   /**
    * Returns the number of additional elements that this queue can ideally
    * (in the absence of memory or resource constraints) accept without
    * blocking. This is always equal to the initial capacity of this queue
    * less the current {@code size} of this queue.
    *
    * <p>Note that you <em>cannot</em> always tell if an attempt to insert
    * an element will succeed by inspecting {@code remainingCapacity}
    * because it may be the case that another thread is about to
    * insert or remove an element.
    */
   public int remainingCapacity() {
       final ReentrantLock lock = this.lock;
       lock.lock();
       try {
           return items.length - count;
       } finally {
           lock.unlock();
       }
   }

   /**
    * Removes a single instance of the specified element from this queue,
    * if it is present.  More formally, removes an element {@code e} such
    * that {@code o.equals(e)}, if this queue contains one or more such
    * elements.
    * Returns {@code true} if this queue contained the specified element
    * (or equivalently, if this queue changed as a result of the call).
    *
    * <p>Removal of interior elements in circular array based queues
    * is an intrinsically slow and disruptive operation, so should
    * be undertaken only in exceptional circumstances, ideally
    * only when the queue is known not to be accessible by other
    * threads.
    *
    * @param o element to be removed from this queue, if present
    * @return {@code true} if this queue changed as a result of the call
    */
   public boolean remove(Object o) {
       if (o == null) return false;
       final Object[] items = this.items;
       final ReentrantLock lock = this.lock;
       lock.lock();
       try {
           if (count > 0) {
               final int putIndex = this.putIndex;
               int i = takeIndex;
               do {
                   if (o.equals(items[i])) {
                       removeAt(i);
                       return true;
                   }
                   if (++i == items.length)
                       i = 0;
               } while (i != putIndex);
           }
           return false;
       } finally {
           lock.unlock();
       }
   }

   /**
    * Returns {@code true} if this queue contains the specified element.
    * More formally, returns {@code true} if and only if this queue contains
    * at least one element {@code e} such that {@code o.equals(e)}.
    *
    * @param o object to be checked for containment in this queue
    * @return {@code true} if this queue contains the specified element
    */
   public boolean contains(Object o) {
       if (o == null) return false;
       final Object[] items = this.items;
       final ReentrantLock lock = this.lock;
       lock.lock();
       try {
           if (count > 0) {
               final int putIndex = this.putIndex;
               int i = takeIndex;
               do {
                   if (o.equals(items[i]))
                       return true;
                   if (++i == items.length)
                       i = 0;
               } while (i != putIndex);
           }
           return false;
       } finally {
           lock.unlock();
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
       Object[] a;
       final ReentrantLock lock = this.lock;
       lock.lock();
       try {
           final int count = this.count;
           a = new Object[count];
           int n = items.length - takeIndex;
           if (count <= n)
               System.arraycopy(items, takeIndex, a, 0, count);
           else {
               System.arraycopy(items, takeIndex, a, 0, n);
               System.arraycopy(items, 0, a, n, count - n);
           }
       } finally {
           lock.unlock();
       }
       return a;
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
       final Object[] items = this.items;
       final ReentrantLock lock = this.lock;
       lock.lock();
       try {
           final int count = this.count;
           final int len = a.length;
           if (len < count)
               a = (T[])java.lang.reflect.Array.newInstance(
                   a.getClass().getComponentType(), count);
           int n = items.length - takeIndex;
           if (count <= n)
               System.arraycopy(items, takeIndex, a, 0, count);
           else {
               System.arraycopy(items, takeIndex, a, 0, n);
               System.arraycopy(items, 0, a, n, count - n);
           }
           if (len > count)
               a[count] = null;
       } finally {
           lock.unlock();
       }
       return a;
   }

   public String toString() {
       final ReentrantLock lock = this.lock;
       lock.lock();
       try {
           int k = count;
           if (k == 0)
               return "[]";

           final Object[] items = this.items;
           StringBuilder sb = new StringBuilder();
           sb.append('[');
           for (int i = takeIndex; ; ) {
               Object e = items[i];
               sb.append(e == this ? "(this Collection)" : e);
               if (--k == 0)
                   return sb.append(']').toString();
               sb.append(',').append(' ');
               if (++i == items.length)
                   i = 0;
           }
       } finally {
           lock.unlock();
       }
   }

   /**
    * Atomically removes all of the elements from this queue.
    * The queue will be empty after this call returns.
    */
   public void clear() {
       final Object[] items = this.items;
       final ReentrantLock lock = this.lock;
       lock.lock();
       try {
           int k = count;
           if (k > 0) {
               final int putIndex = this.putIndex;
               int i = takeIndex;
               do {
                   items[i] = null;
                   if (++i == items.length)
                       i = 0;
               } while (i != putIndex);
               takeIndex = putIndex;
               count = 0;
               if (itrs != null)
                   itrs.queueIsEmpty();
               for (; k > 0 && lock.hasWaiters(notFull); k--)
                   notFull.signal();
           }
       } finally {
           lock.unlock();
       }
   }

   /**
    * @throws UnsupportedOperationException {@inheritDoc}
    * @throws ClassCastException            {@inheritDoc}
    * @throws NullPointerException          {@inheritDoc}
    * @throws IllegalArgumentException      {@inheritDoc}
    */
   public int drainTo(Collection<? super E> c) {
       return drainTo(c, Integer.MAX_VALUE);
   }

   /**
    * @throws UnsupportedOperationException {@inheritDoc}
    * @throws ClassCastException            {@inheritDoc}
    * @throws NullPointerException          {@inheritDoc}
    * @throws IllegalArgumentException      {@inheritDoc}
    */
   public int drainTo(Collection<? super E> c, int maxElements) {
       checkNotNull(c);
       if (c == this)
           throw new IllegalArgumentException();
       if (maxElements <= 0)
           return 0;
       final Object[] items = this.items;
       final ReentrantLock lock = this.lock;
       lock.lock();
       try {
           int n = Math.min(maxElements, count);
           int take = takeIndex;
           int i = 0;
           try {
               while (i < n) {
                   @SuppressWarnings("unchecked")
                   E x = (E) items[take];
                   c.add(x);
                   items[take] = null;
                   if (++take == items.length)
                       take = 0;
                   i++;
               }
               return n;
           } finally {
               // Restore invariants even if c.add() threw
               if (i > 0) {
                   count -= i;
                   takeIndex = take;
                   if (itrs != null) {
                       if (count == 0)
                           itrs.queueIsEmpty();
                       else if (i > take)
                           itrs.takeIndexWrapped();
                   }
                   for (; i > 0 && lock.hasWaiters(notFull); i--)
                       notFull.signal();
               }
           }
       } finally {
           lock.unlock();
       }
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

   /**
    * Shared data between iterators and their queue, allowing queue
    * modifications to update iterators when elements are removed.
    *
    * This adds a lot of complexity for the sake of correctly
    * handling some uncommon operations, but the combination of
    * circular-arrays and supporting interior removes (i.e., those
    * not at head) would cause iterators to sometimes lose their
    * places and/or (re)report elements they shouldn't.  To avoid
    * this, when a queue has one or more iterators, it keeps iterator
    * state consistent by:
    *
    * (1) keeping track of the number of "cycles", that is, the
    *     number of times takeIndex has wrapped around to 0.
    * (2) notifying all iterators via the callback removedAt whenever
    *     an interior element is removed (and thus other elements may
    *     be shifted).
    *
    * These suffice to eliminate iterator inconsistencies, but
    * unfortunately add the secondary responsibility of maintaining
    * the list of iterators.  We track all active iterators in a
    * simple linked list (accessed only when the queue's lock is
    * held) of weak references to Itr.  The list is cleaned up using
    * 3 different mechanisms:
    *
    * (1) Whenever a new iterator is created, do some O(1) checking for
    *     stale list elements.
    *
    * (2) Whenever takeIndex wraps around to 0, check for iterators
    *     that have been unused for more than one wrap-around cycle.
    *
    * (3) Whenever the queue becomes empty, all iterators are notified
    *     and this entire data structure is discarded.
    *
    * So in addition to the removedAt callback that is necessary for
    * correctness, iterators have the shutdown and takeIndexWrapped
    * callbacks that help remove stale iterators from the list.
    *
    * Whenever a list element is examined, it is expunged if either
    * the GC has determined that the iterator is discarded, or if the
    * iterator reports that it is "detached" (does not need any
    * further state updates).  Overhead is maximal when takeIndex
    * never advances, iterators are discarded before they are
    * exhausted, and all removals are interior removes, in which case
    * all stale iterators are discovered by the GC.  But even in this
    * case we don't increase the amortized complexity.
    *
    * Care must be taken to keep list sweeping methods from
    * reentrantly invoking another such method, causing subtle
    * corruption bugs.
    */
   class Itrs {

       /**
        * Node in a linked list of weak iterator references.
        */
       private class Node extends WeakReference<Itr> {
           Node next;

           Node(Itr iterator, Node next) {
               super(iterator);
               this.next = next;
           }
       }

       /** Incremented whenever takeIndex wraps around to 0 */
       int cycles = 0;

       /** Linked list of weak iterator references */
       private Node head;

       /** Used to expunge stale iterators */
       private Node sweeper = null;

       private static final int SHORT_SWEEP_PROBES = 4;
       private static final int LONG_SWEEP_PROBES = 16;

       Itrs(Itr initial) {
           register(initial);
       }

       /**
        * Sweeps itrs, looking for and expunging stale iterators.
        * If at least one was found, tries harder to find more.
        * Called only from iterating thread.
        *
        * @param tryHarder whether to start in try-harder mode, because
        * there is known to be at least one iterator to collect
        */
       void doSomeSweeping(boolean tryHarder) {
           // assert lock.getHoldCount() == 1;
           // assert head != null;
           int probes = tryHarder ? LONG_SWEEP_PROBES : SHORT_SWEEP_PROBES;
           Node o, p;
           final Node sweeper = this.sweeper;
           boolean passedGo;   // to limit search to one full sweep

           if (sweeper == null) {
               o = null;
               p = head;
               passedGo = true;
           } else {
               o = sweeper;
               p = o.next;
               passedGo = false;
           }

           for (; probes > 0; probes--) {
               if (p == null) {
                   if (passedGo)
                       break;
                   o = null;
                   p = head;
                   passedGo = true;
               }
               final Itr it = p.get();
               final Node next = p.next;
               if (it == null || it.isDetached()) {
                   // found a discarded/exhausted iterator
                   probes = LONG_SWEEP_PROBES; // "try harder"
                   // unlink p
                   p.clear();
                   p.next = null;
                   if (o == null) {
                       head = next;
                       if (next == null) {
                           // We've run out of iterators to track; retire
                           itrs = null;
                           return;
                       }
                   }
                   else
                       o.next = next;
               } else {
                   o = p;
               }
               p = next;
           }

           this.sweeper = (p == null) ? null : o;
       }

       /**
        * Adds a new iterator to the linked list of tracked iterators.
        */
       void register(Itr itr) {
           // assert lock.getHoldCount() == 1;
           head = new Node(itr, head);
       }

       /**
        * Called whenever takeIndex wraps around to 0.
        *
        * Notifies all iterators, and expunges any that are now stale.
        */
       void takeIndexWrapped() {
           // assert lock.getHoldCount() == 1;
           cycles++;
           for (Node o = null, p = head; p != null;) {
               final Itr it = p.get();
               final Node next = p.next;
               if (it == null || it.takeIndexWrapped()) {
                   // unlink p
                   // assert it == null || it.isDetached();
                   p.clear();
                   p.next = null;
                   if (o == null)
                       head = next;
                   else
                       o.next = next;
               } else {
                   o = p;
               }
               p = next;
           }
           if (head == null)   // no more iterators to track
               itrs = null;
       }

       /**
        * Called whenever an interior remove (not at takeIndex) occurred.
        *
        * Notifies all iterators, and expunges any that are now stale.
        */
       void removedAt(int removedIndex) {
           for (Node o = null, p = head; p != null;) {
               final Itr it = p.get();
               final Node next = p.next;
               if (it == null || it.removedAt(removedIndex)) {
                   // unlink p
                   // assert it == null || it.isDetached();
                   p.clear();
                   p.next = null;
                   if (o == null)
                       head = next;
                   else
                       o.next = next;
               } else {
                   o = p;
               }
               p = next;
           }
           if (head == null)   // no more iterators to track
               itrs = null;
       }

       /**
        * Called whenever the queue becomes empty.
        *
        * Notifies all active iterators that the queue is empty,
        * clears all weak refs, and unlinks the itrs datastructure.
        */
       void queueIsEmpty() {
           // assert lock.getHoldCount() == 1;
           for (Node p = head; p != null; p = p.next) {
               Itr it = p.get();
               if (it != null) {
                   p.clear();
                   it.shutdown();
               }
           }
           head = null;
           itrs = null;
       }

       /**
        * Called whenever an element has been dequeued (at takeIndex).
        */
       void elementDequeued() {
           // assert lock.getHoldCount() == 1;
           if (count == 0)
               queueIsEmpty();
           else if (takeIndex == 0)
               takeIndexWrapped();
       }
   }

   /**
    * Iterator for ArrayBlockingQueue.
    *
    * To maintain weak consistency with respect to puts and takes, we
    * read ahead one slot, so as to not report hasNext true but then
    * not have an element to return.
    *
    * We switch into "detached" mode (allowing prompt unlinking from
    * itrs without help from the GC) when all indices are negative, or
    * when hasNext returns false for the first time.  This allows the
    * iterator to track concurrent updates completely accurately,
    * except for the corner case of the user calling Iterator.remove()
    * after hasNext() returned false.  Even in this case, we ensure
    * that we don't remove the wrong element by keeping track of the
    * expected element to remove, in lastItem.  Yes, we may fail to
    * remove lastItem from the queue if it moved due to an interleaved
    * interior remove while in detached mode.
    */
   private class Itr implements Iterator<E> {
       /** Index to look for new nextItem; NONE at end */
       private int cursor;

       /** Element to be returned by next call to next(); null if none */
       private E nextItem;

       /** Index of nextItem; NONE if none, REMOVED if removed elsewhere */
       private int nextIndex;

       /** Last element returned; null if none or not detached. */
       private E lastItem;

       /** Index of lastItem, NONE if none, REMOVED if removed elsewhere */
       private int lastRet;

       /** Previous value of takeIndex, or DETACHED when detached */
       private int prevTakeIndex;

       /** Previous value of iters.cycles */
       private int prevCycles;

       /** Special index value indicating "not available" or "undefined" */
       private static final int NONE = -1;

       /**
        * Special index value indicating "removed elsewhere", that is,
        * removed by some operation other than a call to this.remove().
        */
       private static final int REMOVED = -2;

       /** Special value for prevTakeIndex indicating "detached mode" */
       private static final int DETACHED = -3;

       Itr() {
           // assert lock.getHoldCount() == 0;
           lastRet = NONE;
           final ReentrantLock lock = ArrayBlockingQueue.this.lock;
           lock.lock();
           try {
               if (count == 0) {
                   // assert itrs == null;
                   cursor = NONE;
                   nextIndex = NONE;
                   prevTakeIndex = DETACHED;
               } else {
                   final int takeIndex = ArrayBlockingQueue.this.takeIndex;
                   prevTakeIndex = takeIndex;
                   nextItem = itemAt(nextIndex = takeIndex);
                   cursor = incCursor(takeIndex);
                   if (itrs == null) {
                       itrs = new Itrs(this);
                   } else {
                       itrs.register(this); // in this order
                       itrs.doSomeSweeping(false);
                   }
                   prevCycles = itrs.cycles;
                   // assert takeIndex >= 0;
                   // assert prevTakeIndex == takeIndex;
                   // assert nextIndex >= 0;
                   // assert nextItem != null;
               }
           } finally {
               lock.unlock();
           }
       }

       boolean isDetached() {
           // assert lock.getHoldCount() == 1;
           return prevTakeIndex < 0;
       }

       private int incCursor(int index) {
           // assert lock.getHoldCount() == 1;
           if (++index == items.length)
               index = 0;
           if (index == putIndex)
               index = NONE;
           return index;
       }

       /**
        * Returns true if index is invalidated by the given number of
        * dequeues, starting from prevTakeIndex.
        */
       private boolean invalidated(int index, int prevTakeIndex,
                                   long dequeues, int length) {
           if (index < 0)
               return false;
           int distance = index - prevTakeIndex;
           if (distance < 0)
               distance += length;
           return dequeues > distance;
       }

       /**
        * Adjusts indices to incorporate all dequeues since the last
        * operation on this iterator.  Call only from iterating thread.
        */
       private void incorporateDequeues() {
           // assert lock.getHoldCount() == 1;
           // assert itrs != null;
           // assert !isDetached();
           // assert count > 0;

           final int cycles = itrs.cycles;
           final int takeIndex = ArrayBlockingQueue.this.takeIndex;
           final int prevCycles = this.prevCycles;
           final int prevTakeIndex = this.prevTakeIndex;

           if (cycles != prevCycles || takeIndex != prevTakeIndex) {
               final int len = items.length;
               // how far takeIndex has advanced since the previous
               // operation of this iterator
               long dequeues = (cycles - prevCycles) * len
                   + (takeIndex - prevTakeIndex);

               // Check indices for invalidation
               if (invalidated(lastRet, prevTakeIndex, dequeues, len))
                   lastRet = REMOVED;
               if (invalidated(nextIndex, prevTakeIndex, dequeues, len))
                   nextIndex = REMOVED;
               if (invalidated(cursor, prevTakeIndex, dequeues, len))
                   cursor = takeIndex;

               if (cursor < 0 && nextIndex < 0 && lastRet < 0)
                   detach();
               else {
                   this.prevCycles = cycles;
                   this.prevTakeIndex = takeIndex;
               }
           }
       }

       /**
        * Called when itrs should stop tracking this iterator, either
        * because there are no more indices to update (cursor < 0 &&
        * nextIndex < 0 && lastRet < 0) or as a special exception, when
        * lastRet >= 0, because hasNext() is about to return false for the
        * first time.  Call only from iterating thread.
        */
       private void detach() {
           // Switch to detached mode
           // assert lock.getHoldCount() == 1;
           // assert cursor == NONE;
           // assert nextIndex < 0;
           // assert lastRet < 0 || nextItem == null;
           // assert lastRet < 0 ^ lastItem != null;
           if (prevTakeIndex >= 0) {
               // assert itrs != null;
               prevTakeIndex = DETACHED;
               // try to unlink from itrs (but not too hard)
               itrs.doSomeSweeping(true);
           }
       }

       /**
        * For performance reasons, we would like not to acquire a lock in
        * hasNext in the common case.  To allow for this, we only access
        * fields (i.e. nextItem) that are not modified by update operations
        * triggered by queue modifications.
        */
       public boolean hasNext() {
           // assert lock.getHoldCount() == 0;
           if (nextItem != null)
               return true;
           noNext();
           return false;
       }

       private void noNext() {
           final ReentrantLock lock = ArrayBlockingQueue.this.lock;
           lock.lock();
           try {
               // assert cursor == NONE;
               // assert nextIndex == NONE;
               if (!isDetached()) {
                   // assert lastRet >= 0;
                   incorporateDequeues(); // might update lastRet
                   if (lastRet >= 0) {
                       lastItem = itemAt(lastRet);
                       // assert lastItem != null;
                       detach();
                   }
               }
               // assert isDetached();
               // assert lastRet < 0 ^ lastItem != null;
           } finally {
               lock.unlock();
           }
       }

       public E next() {
           // assert lock.getHoldCount() == 0;
           final E x = nextItem;
           if (x == null)
               throw new NoSuchElementException();
           final ReentrantLock lock = ArrayBlockingQueue.this.lock;
           lock.lock();
           try {
               if (!isDetached())
                   incorporateDequeues();
               // assert nextIndex != NONE;
               // assert lastItem == null;
               lastRet = nextIndex;
               final int cursor = this.cursor;
               if (cursor >= 0) {
                   nextItem = itemAt(nextIndex = cursor);
                   // assert nextItem != null;
                   this.cursor = incCursor(cursor);
               } else {
                   nextIndex = NONE;
                   nextItem = null;
               }
           } finally {
               lock.unlock();
           }
           return x;
       }

       public void remove() {
           // assert lock.getHoldCount() == 0;
           final ReentrantLock lock = ArrayBlockingQueue.this.lock;
           lock.lock();
           try {
               if (!isDetached())
                   incorporateDequeues(); // might update lastRet or detach
               final int lastRet = this.lastRet;
               this.lastRet = NONE;
               if (lastRet >= 0) {
                   if (!isDetached())
                       removeAt(lastRet);
                   else {
                       final E lastItem = this.lastItem;
                       // assert lastItem != null;
                       this.lastItem = null;
                       if (itemAt(lastRet) == lastItem)
                           removeAt(lastRet);
                   }
               } else if (lastRet == NONE)
                   throw new IllegalStateException();
               // else lastRet == REMOVED and the last returned element was
               // previously asynchronously removed via an operation other
               // than this.remove(), so nothing to do.

               if (cursor < 0 && nextIndex < 0)
                   detach();
           } finally {
               lock.unlock();
               // assert lastRet == NONE;
               // assert lastItem == null;
           }
       }

       /**
        * Called to notify the iterator that the queue is empty, or that it
        * has fallen hopelessly behind, so that it should abandon any
        * further iteration, except possibly to return one more element
        * from next(), as promised by returning true from hasNext().
        */
       void shutdown() {
           // assert lock.getHoldCount() == 1;
           cursor = NONE;
           if (nextIndex >= 0)
               nextIndex = REMOVED;
           if (lastRet >= 0) {
               lastRet = REMOVED;
               lastItem = null;
           }
           prevTakeIndex = DETACHED;
           // Don't set nextItem to null because we must continue to be
           // able to return it on next().
           //
           // Caller will unlink from itrs when convenient.
       }

       private int distance(int index, int prevTakeIndex, int length) {
           int distance = index - prevTakeIndex;
           if (distance < 0)
               distance += length;
           return distance;
       }

       /**
        * Called whenever an interior remove (not at takeIndex) occurred.
        *
        * @return true if this iterator should be unlinked from itrs
        */
       boolean removedAt(int removedIndex) {
           // assert lock.getHoldCount() == 1;
           if (isDetached())
               return true;

           final int cycles = itrs.cycles;
           final int takeIndex = ArrayBlockingQueue.this.takeIndex;
           final int prevCycles = this.prevCycles;
           final int prevTakeIndex = this.prevTakeIndex;
           final int len = items.length;
           int cycleDiff = cycles - prevCycles;
           if (removedIndex < takeIndex)
               cycleDiff++;
           final int removedDistance =
               (cycleDiff * len) + (removedIndex - prevTakeIndex);
           // assert removedDistance >= 0;
           int cursor = this.cursor;
           if (cursor >= 0) {
               int x = distance(cursor, prevTakeIndex, len);
               if (x == removedDistance) {
                   if (cursor == putIndex)
                       this.cursor = cursor = NONE;
               }
               else if (x > removedDistance) {
                   // assert cursor != prevTakeIndex;
                   this.cursor = cursor = dec(cursor);
               }
           }
           int lastRet = this.lastRet;
           if (lastRet >= 0) {
               int x = distance(lastRet, prevTakeIndex, len);
               if (x == removedDistance)
                   this.lastRet = lastRet = REMOVED;
               else if (x > removedDistance)
                   this.lastRet = lastRet = dec(lastRet);
           }
           int nextIndex = this.nextIndex;
           if (nextIndex >= 0) {
               int x = distance(nextIndex, prevTakeIndex, len);
               if (x == removedDistance)
                   this.nextIndex = nextIndex = REMOVED;
               else if (x > removedDistance)
                   this.nextIndex = nextIndex = dec(nextIndex);
           }
           else if (cursor < 0 && nextIndex < 0 && lastRet < 0) {
               this.prevTakeIndex = DETACHED;
               return true;
           }
           return false;
       }

       /**
        * Called whenever takeIndex wraps around to zero.
        *
        * @return true if this iterator should be unlinked from itrs
        */
       boolean takeIndexWrapped() {
           // assert lock.getHoldCount() == 1;
           if (isDetached())
               return true;
           if (itrs.cycles - prevCycles > 1) {
               // All the elements that existed at the time of the last
               // operation are gone, so abandon further iteration.
               shutdown();
               return true;
           }
           return false;
       }

//        /** Uncomment for debugging. */
//        public String toString() {
//            return ("cursor=" + cursor + " " +
//                    "nextIndex=" + nextIndex + " " +
//                    "lastRet=" + lastRet + " " +
//                    "nextItem=" + nextItem + " " +
//                    "lastItem=" + lastItem + " " +
//                    "prevCycles=" + prevCycles + " " +
//                    "prevTakeIndex=" + prevTakeIndex + " " +
//                    "size()=" + size() + " " +
//                    "remainingCapacity()=" + remainingCapacity());
//        }
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
   public Spliterator<E> spliterator() {
       return Spliterators.spliterator
           (this, Spliterator.ORDERED | Spliterator.NONNULL |
            Spliterator.CONCURRENT);
   }
   
   /*
    * ArrayBlockingQueue通过使用全局独占锁实现了同时只能有一个线程进行入队或者出队操作，
    * 这个锁的粒度比较大，有点类似于在方法上添加synchronized的意思。
    * 其中offer和poll作通过简单的加锁进行入队、出队操作，
    * 而put、take操作则使用条件变量实现了，如果队列满则等待，
    * 如果队列空则等待，然后分别在出队和队操作中发送信号激活等待线程实现同步。
    * 另外，相比LinkedBlockingQueue,ArrayBlockingQueue的size操作的结果是精确的，
    * 因为计算前加了全局锁。
    */

}

