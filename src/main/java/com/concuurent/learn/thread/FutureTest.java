package com.concuurent.learn.thread;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 线程池使用FutureTask的时候
 * 如果拒绝策略设置为了DiscardPolicy和DiscardOldestPolicy
 * 并且在被拒绝的任务的Future对象上调用无参get方法那么调用线程会一直被阻塞。
 * @author Wesley
 *
 * 2019年7月11日下午4:19:42
 * @Version 1.0
 */
public class FutureTest {
	 
    //(1)线程池单个线程，线程池队列元素个数为1
//        private final static ThreadPoolExecutor executorService = new ThreadPoolExecutor(1, 1, 1L, TimeUnit.MINUTES,
//            new ArrayBlockingQueue<Runnable>(1),new ThreadPoolExecutor.DiscardPolicy());
        private final static ThreadPoolExecutor executorService = new ThreadPoolExecutor(1, 1, 1L, TimeUnit.MINUTES,
        		new ArrayBlockingQueue<Runnable>(1), new  MyRejectedExecutionHandler());
 
    public static void main(String[] args) throws Exception {
 
        //(2)添加任务one
        Future futureOne = executorService.submit(new Runnable() {
            @Override
            public void run() {
 
                System.out.println("start runable one");
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        
        //(3)添加任务two
        Future futureTwo = executorService.submit(new Runnable() {
            @Override
            public void run() {
                System.out.println("start runable two");
            }
        });
        
        //(4)添加任务three
        Future futureThree=null;
        try {
            futureThree = executorService.submit(new Runnable() {
                @Override
                public void run() {
                    System.out.println("start runable three");
                }
            });
        } catch (Exception e) {
            System.out.println(e.getLocalizedMessage());
        }
        
    
	    
       
        System.out.println("task one " + futureOne.get());//(5)等待任务one执行完毕
        System.out.println("task two " + futureTwo.get());//(6)等待任务two执行完毕
        //System.out.println("task three " + (futureThree==null?null:futureThree.get()));// (7)等待任务three执行完毕
        
        try{
            System.out.println("task three " + (futureThree==null?null:futureThree.get()));// (6)等待任务three
        }catch(Exception e){
            System.out.println(e.getLocalizedMessage());
        }
        
        executorService.shutdown();//(8)关闭线程池，阻塞直到所有任务执行完毕
    }
    //运行代码结果为：
//    start runable one
//    task one null
//    start runable two
//    task two null

//    代码(1)创建了一个单线程并且队列元素个数为1的线程池，并且拒绝策略设置为了 DiscardPolicy
//    代码（2）向线程池提交了一个任务one，那么这个任务会使用唯一的一个线程进行执行，任务在打印 start runable one后会阻塞该线程5s.
//    代码（3）向线程池提交了一个任务two，这时候会把任务two放入到阻塞队列
//    代码（4）向线程池提交任务three，由于队列已经满了则会触发拒绝策略丢弃任务three,
//    从执行结果看在任务one阻塞的5s内，主线程执行到了代码(5)等待任务one执行完毕，
//    当任务one执行完毕后代码（5）返回，主线程打印出task one null。
//    任务one执行完成后线程池的唯一线程会去队列里面取出任务two并执行所以输出start runable two然后代码（6）会返回，
//    这时候主线程输出task two null，然后执行代码（7）等待任务three执行完毕，
//    从执行结果看代码（7）会一直阻塞不会返回，至此问题产生，如果把拒绝策略修改为DiscardOldestPolicy
//    也会存在有一个任务的get方法一直阻塞只是现在是任务two被阻塞。但是如果拒绝策略设置为默认的AbortPolicy则会正常返回，
//    并且会输出如下结果：
//	  private final static ThreadPoolExecutor executorService = new ThreadPoolExecutor(1, 1, 1L, TimeUnit.MINUTES,
//	  new ArrayBlockingQueue<Runnable>(1),new ThreadPoolExecutor.AbortPolicy());


//	  start runable one
//	  Task java.util.concurrent.FutureTask@70dea4e rejected from java.util.concurrent.ThreadPoolExecutor@5c647e05[Running, pool size = 1, active threads = 1, queued tasks = 1, completed tasks = 0]
//	  start runable two
//	  task one null
//	  task two null
//	  task three null
	  
	  
//	  要分析这个问题需要看下线程池的submit方法里面做了什么，submit方法代码如下：
//	    public Future<?> submit(Runnable task) {
//	        ...
//	        //（1）装饰Runnable为Future对象
//	        RunnableFuture<Void> ftask = newTaskFor(task, null);
//	        execute(ftask);
//	        //(6)返回future对象
//	        return ftask;
//	    }
//	    
//	        protected <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value) {
//	        return new FutureTask<T>(runnable, value);
//	    }
	    
//	  public void execute(Runnable command) {
//	         ...
//	        //(2) 如果线程个数消息核心线程数则新增处理线程处理
//	        int c = ctl.get();
//	        if (workerCountOf(c) < corePoolSize) {
//	            if (addWorker(command, true))
//	                return;
//	            c = ctl.get();
//	        }
//	        //（3）如果当前线程个数已经达到核心线程数则任务放入队列
//	        if (isRunning(c) && workQueue.offer(command)) {
//	            int recheck = ctl.get();
//	            if (! isRunning(recheck) && remove(command))
//	                reject(command);
//	            else if (workerCountOf(recheck) == 0)
//	                addWorker(null, false);
//	        }
//	        //（4）尝试新增处理线程进行处理
//	        else if (!addWorker(command, false))
//	            reject(command);//(5)新增失败则调用拒绝策略
//	    }
//	  根据代码可以总结如下：
//
//	  代码（1）装饰Runnable为FutureTask对象，然后调用线程池的execute方法
//	  代码  (2)  如果线程个数消息核心线程数则新增处理线程处理
//	  代码（3）如果当前线程个数已经达到核心线程数则任务放入队列
//	  代码（4）尝试新增处理线程进行处理，失败则进行代码（5），否者直接使用新线程处理
//	  代码（5）执行具体拒绝策略。
//	  所以要分析上面例子中问题所在只需要看步骤（5）对被拒绝任务的影响，这里先看下拒绝策略DiscardPolicy的代码：
	  
	    public static class DiscardPolicy implements RejectedExecutionHandler {
	        public DiscardPolicy() { }
	        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
	        }
	    }
//	    可知拒绝策略rejectedExecution方法里面什么都没做，
//	    所以代码（4）调用submit后会返回一个future对象，
//	    这里有必要在重新说future是有状态的，future的状态枚举值如下：
	    
	    private static final int NEW          = 0;
	    private static final int COMPLETING   = 1;
	    private static final int NORMAL       = 2;
	    private static final int EXCEPTIONAL  = 3;
	    private static final int CANCELLED    = 4;
	    private static final int INTERRUPTING = 5;
	    private static final int INTERRUPTED  = 6;
	    
	    
//	    在步骤（1）的时候使用newTaskFor方法转换Runnable任务为FutureTask，而FutureTask的构造函数里面设置的状态就是New。
//	    public FutureTask(Runnable runnable, V result) {
//	        this.callable = Executors.callable(runnable, result);
//	        this.state = NEW;       // ensure visibility of callable
//	    }
	    
//	    所以使用DiscardPolicy策略提交后返回了一个状态为NEW的future对象。
//	    那么我们下面就需要看下当调用future的无参get方法时候当future变为什么状态时候才会返回那,那就需看下FutureTask的get（）方法代码：

//	    public V get() throws InterruptedException, ExecutionException {
//	        int s = state;
//	        //当状态值<=COMPLETING时候需要等待，否者调用report返回
//	        if (s <= COMPLETING)
//	            s = awaitDone(false, 0L);
//	        return report(s);
//	    }
//	    
//	   private V report(int s) throws ExecutionException {
//	   Object x = outcome;
//	   //状态值为NORMAL正常返回
//	   if (s == NORMAL)
//	       return (V)x;
//	   //状态值大于等于CANCELLED则抛异常
//	   if (s >= CANCELLED)
//	       throw new CancellationException();
//	   throw new ExecutionException((Throwable)x);
//	}
//	    也就是说当future的状态>COMPLETING时候调用get方法才会返回，
//	    而明显DiscardPolicy策略在拒绝元素的时候并没有设置该future的状态，
//	    后面也没有其他机会可以设置该future的状态，所以future的状态一直是NEW，
//	    所以一直不会返回，同理DiscardOldestPolicy策略也是这样的问题，
//	    最老的任务被淘汰时候没有设置被淘汰任务对于future的状态。

//	    那么默认的AbortPolicy策略为啥没问题那？其实AbortPolicy策略时候步骤（5）直接会抛出RejectedExecutionException异常，
//	    也就是submit方法并没有返回future对象，这时候futureThree是null。
//
//	    所以当使用Future的时候，尽量使用带超时时间的get方法，
//	    这样即使使用了DiscardPolicy拒绝策略也不至于一直等待，
//	    等待超时时间到了会自动返回的，如果非要使用不带参数的get方法则
//	    可以重写DiscardPolicy的拒绝策略在执行策略时候设置该Future的状态
//	    大于COMPLETING即可，但是查看FutureTask提供的方法发现只有cancel方法
//	    是public的并且可以设置FutureTask的状态大于COMPLETING，重写拒绝策略具体代码可以如下：
	    
	    static class MyRejectedExecutionHandler implements RejectedExecutionHandler{

			@Override
	        public void rejectedExecution(Runnable runable, ThreadPoolExecutor e) {
	             if (!e.isShutdown()) {
	                 if(null != runable && runable instanceof FutureTask){
	                     ((FutureTask) runable).cancel(true);
	                 }
	             }
	        }
	     
	    }
	    
	    //使用这个策略时候由于从report方法知道在cancel的任务上调用get()方法会抛出异常所以代码（7）需要使用try-catch捕获异常代码（7）修改为如下：   
//	    start runable one
//	    task one null
//	    start runable two
//	    task two null
//	    null

//	    当然这相比正常情况下多了一个异常捕获，
//	    其实最好的情况是重写拒绝策略时候设置FutureTask的状态为NORMAL，
//	    但是这需要重写FutureTask方法了，因为FutureTask并没有提供接口进行设置。

//	    总结
//	    本文通过案例介绍了线程池中使用FutureTask时候当拒绝策略为DiscardPolicy
//	    和 DiscardOldestPolicy的时候在被拒绝的任务的FutureTask对象上调用get()
//	    方法会导致调用线程一直阻塞，所以在日常开发中尽量使用带超时参数的get方法以避免线程一直阻塞，
//	    另外通过重写这些拒绝策略设置拒绝任务的状态也可以达到想要的效果。多线程下使用时候最好使用ThreadLocal对象
}
