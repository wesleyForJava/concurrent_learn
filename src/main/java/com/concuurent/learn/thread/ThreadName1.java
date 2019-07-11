package com.concuurent.learn.thread;
//当一个系统中有多个业务模块而每个模块又都使用自己的线程时，
//除非抛出与业务相关的异常，则你根本没法断是哪个模块出现了问题。
//现在修改代码如下。
public class ThreadName1 {
	
	
	static final String THREAD_SAVE_ORDER="THREAD_SAVE_ORDER";
	static final String THREAD_SAVE_ADDR="THREAD_SAVE_ADDR";
	
	public static void main(String[] args) {
		Thread threadOne=new Thread(new Runnable() {
			@Override
			public void run() {
				System.out.println("保存订单的线程");
				try {
					Thread.sleep(500);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				throw new NullPointerException();
			}
		},THREAD_SAVE_ORDER);
		Thread threadTwo=new Thread(new Runnable() {
			@Override
			public void run() {
				System.out.println("保存收货地址的线程");
			}
		},THREAD_SAVE_ADDR);
		
		threadOne.start();
		threadTwo.start();
	}
//	从运行结果就可以定位到是保存订单模块抛出了NPE异常，一下子就可以找到问题所在。
}
