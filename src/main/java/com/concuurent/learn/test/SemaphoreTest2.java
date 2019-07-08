package com.concuurent.learn.test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
/**
 * @author Wesley
 * 首先将线程A和线程B加入到线程池。主线程执行代码（1）后被阻塞。
 * 线程A和线程B调用release方法后信号量的值变为了2，
 * 这时候主线程的aquire方法会在获取到2个信号量后返回（返回后当前信号量值为0）。
 * 然后主线程添加线程C和线程D到线程池，之后主线程执行代码（2）后被阻塞（因为主线程要获取2个信号量，而当前信号量个数为0）。
 * 当线程C和线程D执行完release方法后，主线程才返回。从本例子可以看出，
 * Semaphore在某种程度上实现了CyclicBarrier的复用功能。
 * 2019年7月8日下午2:00:30
 * @Version 1.0
 */
public class SemaphoreTest2 {
	//下面举个例子来模仿CyclicBarrier复用功能
	//创建一个Semaphore实例
	private static Semaphore semaphore=new Semaphore(0);
	
	public static void main(String[] args)  throws InterruptedException{
		
		ExecutorService executorService = Executors.newFixedThreadPool(2);
		
		//将线程A添加到线程池
		executorService.submit(new Runnable() {
			
			@Override
			public void run() {
				System.out.println(Thread.currentThread()+" A task over");
				semaphore.release();
			}
		},"A");
		//将线程B添加到线程池
		executorService.submit(new Runnable() {
			
			@Override
			public void run() {
				System.out.println(Thread.currentThread()+" A task over");
				semaphore.release();
			}
		},"B");
		
		//等待子线程执行任务A完毕，返回
		semaphore.acquire(2);
		
		//将线程C添加到线程池
		executorService.submit(new Runnable() {
			
			@Override
			public void run() {
				System.out.println(Thread.currentThread()+" B task over");
				semaphore.release();
			}
		});
		//将线程D添加到线程池
		executorService.submit(new Runnable() {
			
			@Override
			public void run() {
				System.out.println(Thread.currentThread()+" B task over");
				semaphore.release();
			}
		});
		//等待子线程执行任务B完毕，返回
		semaphore.acquire(2);
		System.out.println("task is over");
		
		executorService.shutdown();
		
	}
	

}
