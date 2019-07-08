package com.concuurent.learn.test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
/**
 * 如下代码首先创建了一个信号量实例，构造函数的入参为0，
 * 说明当前信号量计数器的值为0。然后main函数向线程池添加两个线程任务，
 * 在每个线程内部调用信号量的release方法，这相当于让计数器值递增1。
 * 最后在main线程里面调用信号量的acquire方法，传参为2说明调用acquire方法的线程会一直阻塞，
 * 直到信号量的计数变为2才会返回。看到这里也就明白了，如果构造Semaphore时传递的参数为N，
 * 并在M个线程中调用了该信号量的release方法，那么在调用acquire使M个线程同步时传递的参数应该是M+N。
 * @author Wesley
 *
 * 2019年7月8日下午2:00:30
 * @Version 1.0
 */
public class SemaphoreTest {
	//创建一个Semaphore实例
	private static Semaphore semaphore=new Semaphore(0);
	
	public static void main(String[] args)  throws InterruptedException{
		
		ExecutorService executorService = Executors.newFixedThreadPool(2);
		
		//将线程A添加到线程池
		executorService.submit(new Runnable() {
			
			@Override
			public void run() {
				System.out.println(Thread.currentThread()+"over");
				semaphore.release();
			}
		},"A");
		//将线程B添加到线程池
		executorService.submit(new Runnable() {
			
			@Override
			public void run() {
				System.out.println(Thread.currentThread()+"over");
				semaphore.release();
			}
		},"B");
		
		//等待子线程执行完毕，返回
		semaphore.acquire(2);
		System.out.println("all child thread over");
		
		executorService.shutdown();
		
	}
	

}
