package com.concuurent.learn.thread;

import java.io.IOException;
import java.io.PrintWriter;

import java.lang.ThreadLocal;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

//Tomcat的Servlet中使用ThreadLocal导致内存泄露
public class HelloWorldExample extends HttpServlet {

    private static final long serialVersionUID = 1L;

    static class LocalVariable {
        private Long[] a = new Long[1024 * 1024 * 100];
    }

    //(1)
    final static ThreadLocal<LocalVariable> localVariable = new ThreadLocal<LocalVariable>();

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        //(2)
        localVariable.set(new LocalVariable());

        response.setContentType("text/html");
        PrintWriter out = response.getWriter();

        out.println("<html>");
        out.println("<head>");

        out.println("<title>" + "title" + "</title>");
        out.println("</head>");
        out.println("<body bgcolor=\"white\">");
        //(3)
        out.println(this.toString());
        //(4)
        out.println(Thread.currentThread().toString());

        out.println("</body>");
        out.println("</html>");
    }
    
//    代码（1）创建一个localVariable对象，
//    代码（2）在servlet的doGet方法内设置localVariable值
//    代码（3）打印当前servlet的实例
//    代码（4）打印当前线程
    
//    修改tomcat的conf下sever.xml配置如下：
//    <Executor name="tomcatThreadPool" namePrefix="catalina-exec-" 
//            maxThreads="10" minSpareThreads="5"/>
//
//        <Connector executor="tomcatThreadPool" port="8080" protocol="HTTP/1.1" 
//                   connectionTimeout="20000" 
//                   redirectPort="8443" />
    
//    这里设置了tomcat的处理线程池最大线程为10个，最小线程为5个，那么这个线程池是干什么用的?
    
//    Tomcat中Connector组件负责接受并处理请求，其中Socket acceptor thread 负责接受用户的访问请求，
//    然后把接受到的请求交给Worker threads pool线程池进行具体处理，后者就是我们在server.xml里面配置的线程池。
//    Worker threads pool里面的线程则负责把具体请求分发到具体的应用的servlet上进行处理。
//    有了上述知识，下面启动tomcat访问该servlet多次，会发现有可能输出下面结果:
//          HelloWorldExample@2a10b2d2 Thread[catalina-exec-5,5,main]
//    		HelloWorldExample@2a10b2d2 Thread[catalina-exec-1,5,main]
//    		HelloWorldExample@2a10b2d2 Thread[catalina-exec-4,5,main]

    
//    其中前半部分是打印的servlet实例，这里都一样说明多次访问的都是一个servlet实例，
//    后半部分中catalina-exec-5，catalina-exec-1，catalina-exec-4，说明使用了connector中线程池里面的线程5，线程1，线程4来执行serlvet的。
//    如果在访问该servlet的同时打开了jconsole观察堆内存会发现内存会飙升，
//    究其原因是因为工作线程调用servlet的doGet方法时候，工作线程的threadLocals变量里面被添加了new LocalVariable()实例，
//    但是没有被remove，另外多次访问该servlet可能用的不是工作线程池里面的同一个线程，这会导致工作线程池里面多个线程都会存在内存泄露。
//    更糟糕的还在后面，上面的代码在tomcat6.0的时代，应用reload操作后会导致加载该应用的webappClassLoader释放不了，
//    这是因为servlet的doGet方法里面创建new LocalVariable()的时候使用的是webappclassloader，
//    所以LocalVariable.class里面持有webappclassloader的引用，由于LocalVariable的实例没有被释放，
//    所以LocalVariable.class对象也没有没释放，所以  webappclassloader也没有被释放，
//    那么webappclassloader加载的所有类也没有被释放。
//    这是因为应用reload的时候connector组件里面的工作线程池里面的线程还是一直存在的，
//    并且线程里面的threadLocals变量并没有被清理。而在tomcat7.0里面这个问题被修复了，
//    应用在reload时候会清理工作线程池中线程的threadLocals变量，
//    tomcat7.0里面reload后会有如下提示：


//    十二月 31, 2018 5:44:24 下午 org.apache.catalina.loader.WebappClassLoader checkThreadLocalMapForLeaks
//    严重: The web application [/examples] created a ThreadLocal with key of type [java.lang.ThreadLocal] (value [java.lang.ThreadLocal@63a3e00b]) and a value of type [HelloWorldExample.LocalVariable] (value [HelloWorldExample$LocalVariable@4fd7564b]) but failed to remove it when the web application was stopped. Threads are going to be renewed over time to try and avoid a probable memory leak.

    
//    Java提供的ThreadLocal给我们编程提供了方便，
//    但是如果使用不当也会给我们带来致命的灾难，
//    编码时候要养成良好的习惯，
//    线程中使用完ThreadLocal变量后，
//    要记得及时remove掉。
    
    
    
}