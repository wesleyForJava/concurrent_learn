package com.concuurent.learn.tomcat;

public class Summary {
	

	/**
	 * 本节通过分析Tomcat中NioEndPoint的实现源码介绍了并发组件ConcurrentLinkedQueue的使用。
	 * NioEndPoint的思想也是使用队列将同步转为异步，并且由于Concun-entLinkedQueue是无界队列，
	 * 所以需要让用户提供一个设置队列大小的接口以防止队列元素过多导致OOM
	 */

}
