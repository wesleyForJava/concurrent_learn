package com.concuurent.learn.thread;

import java.security.AccessControlContext;
import java.security.AccessController;

/**
 * 建线程和线程池时要指定与业务相关的名称
 *在日常开发中，当在一个应用中需要创建多个钱程或者线程时最好给每个线程或者程池根据业务类型设置具体的名称，
 *以便在出现问题时方便进行定位。下面就通过实例说明不设置为何难以定位问题，以及如何进行设置。
 * @author Wesley
 *
 * 2019年7月11日下午2:47:00
 * @Version 1.0
 */
public class ThreadName {
	
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
		});
		Thread threadTwo=new Thread(new Runnable() {
			@Override
			public void run() {
				System.out.println("保存收货地址的线程");
			}
		});
		
		threadOne.start();
		threadTwo.start();
//		运行结果可知，Thread-0抛出了NPE异常，
//		那么单看这个日志根本无法判断是订单模块的线程抛出的异常。
//		首先我们分析下这个Thread-0是怎么来的，我们看一下创建线程时的代码。
	}

			//    public Thread(Runnable target) {
			//        init(null, target, "Thread-" + nextThreadNum(), 0);
			//    }
				
//			 private void init(ThreadGroup g, Runnable target, String name,
//			             long stackSize, AccessControlContext acc,
//			             boolean inheritThreadLocals) {
//			if (name == null) {
//			   throw new NullPointerException("name cannot be null");
//			}
//			
//			this.name = name;
//			
//			Thread parent = currentThread();
//			SecurityManager security = System.getSecurityManager();
//			if (g == null) {
//			   /* Determine if it's an applet or not */
//			
//			   /* If there is a security manager, ask the security manager
//			      what to do. */
//			   if (security != null) {
//			       g = security.getThreadGroup();
//			   }
//			
//			   /* If the security doesn't have a strong opinion of the matter
//			      use the parent thread group. */
//			   if (g == null) {
//			       g = parent.getThreadGroup();
//			   }
//			}
//			
//			/* checkAccess regardless of whether or not threadgroup is
//			  explicitly passed in. */
//			g.checkAccess();
//			
//			/*
//			* Do we have the required permissions?
//			*/
//			if (security != null) {
//			   if (isCCLOverridden(getClass())) {
//			       security.checkPermission(SUBCLASS_IMPLEMENTATION_PERMISSION);
//			   }
//			}
//			
//			g.addUnstarted();
//			
//			this.group = g;
//			this.daemon = parent.isDaemon();
//			this.priority = parent.getPriority();
//			if (security == null || isCCLOverridden(parent.getClass()))
//			   this.contextClassLoader = parent.getContextClassLoader();
//			else
//			   this.contextClassLoader = parent.contextClassLoader;
//			this.inheritedAccessControlContext =
//			       acc != null ? acc : AccessController.getContext();
//			this.target = target;
//			setPriority(priority);
//			if (inheritThreadLocals && parent.inheritableThreadLocals != null)
//			   this.inheritableThreadLocals =
//			       ThreadLocal.createInheritedMap(parent.inheritableThreadLocals);
//			/* Stash the specified stack size in case the VM cares */
//			this.stackSize = stackSize;
//			
//			/* Set thread ID */
//			tid = nextThreadID();
//			}
	
//	由这段代码可知，如果调用没有指定线程名称的方法创建线程，
//	其内部会使用”Thread-"+nextThreadNum（）作为线程的默认名称，其中nextThreadNum的代码如下。
    private static int threadInitNumber;
    private static synchronized int nextThreadNum() {
        return threadInitNumber++;
    }
//    由此知，threadlnitNumber是static变量，
//    nextThreadNum是static方法，
//    所以线的编号是全应用唯一的并且是递增的。
//    这里由于涉及多线程递增threadlnitNumber,
//    也就是执行读取－递增一写入操作，而这是线程不安全的，
//    所以要使用方法级别的synchronized进行同步。
    
	
}
