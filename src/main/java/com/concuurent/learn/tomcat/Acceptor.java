package com.concuurent.learn.tomcat;

import java.io.IOException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Locale;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.net.AbstractEndpoint;
import org.apache.tomcat.util.net.NioChannel;
import org.apache.tomcat.util.net.NioEndpoint;
import org.apache.tomcat.util.net.NioEndpoint.PollerEvent;
import org.apache.tomcat.util.res.StringManager;
import org.apache.tomcat.util.threads.LimitLatch;


// --------------------------------------------------- Acceptor Inner Class
/**
 * Acceptor套接字接受线程(Socketacceptorthread），
 * 用来接受用户的请求，并把请求封装为事件任务放入Poller的队列，
 * 一个Connector面只有一个Acceptor。
 * •Poller是套接字处理线程（Socketpollerthread），
 * 每个Poller部都有一个独有的队列，
 * Poller线程则从自己的队列里面获取具体的事件任务，
 * 然后将其交给Worker进行处理。Poller线程的个数与处理器的核数有关，
 * 代码如下。protected int pollerThreadCount=Math.min(2,Runtime.getRuntime().availableProcessors());
 * 这里最多有2个Poller线程。
 * •Worker是实际处理请求的线程，Worker只是组件名字，
 * 真正做事情的是SocketProcessor，它是Poller线程从自己的队列获取任务后的真正任务执行者。
 * 可见，Tomcat使用队列把接受请求与处理请求操作进行解耦，实现异步处理。
 * 其实Tomcat中NioEndPoint中的每个Poller里面都维护一个ConcurrentLinkedQueue，
 * 用来缓存请求任务，其本身也是一个多生产者，单消费者模型。
 * @author Wesley
 *
 * 2019年7月10日上午11:03:59
 * @Version 1.0
 */
public class Acceptor extends AbstractEndpoint.Acceptor {
	  private static final Log log = LogFactory.getLog(NioEndpoint.class);
	 
    /**
     * Running state of the endpoint.
     */
    protected volatile boolean running = false;
    /**
     * Will be set to true whenever the endpoint is paused.
     */
    protected volatile boolean paused = false;
    private int maxConnections = 10000;
    /**
     * counter for nr of connections handled by an endpoint
     */
    private volatile LimitLatch connectionLimitLatch = null;
    
    public static final StringManager getManager(Class<?> clazz) {
        return getManager(clazz.getPackage().getName());
    }
    
    /**
     * Get the StringManager for a particular package. If a manager for
     * a package already exists, it will be reused, else a new
     * StringManager will be created and returned.
     *
     * @param packageName The package name
     *
     * @return The instance associated with the given package and the default
     *         Locale
     */
    public static final StringManager getManager(String packageName) {
        return getManager(packageName, Locale.getDefault());
    }

    /**
     * Get the StringManager for a particular package and Locale. If a manager
     * for a package/Locale combination already exists, it will be reused, else
     * a new StringManager will be created and returned.
     *
     * @param packageName The package name
     * @param locale      The Locale
     *
     * @return The instance associated with the given package and Locale
     */
    public static final synchronized StringManager getManager(
            String packageName, Locale locale) {
        return null;
    }
    /**
     * Server socket "pointer".
     */
    private volatile ServerSocketChannel serverSock = null;
    
    protected void countUpOrAwaitConnection() throws InterruptedException {
        if (maxConnections==-1) return;
        LimitLatch latch = connectionLimitLatch;
        if (latch!=null) latch.countUpOrAwait();
    }
   // protected abstract Log getLog();
    protected Log getLog() {
        return log;
    }
    protected static final StringManager sm = getManager(AbstractEndpoint.class);
    
    protected long countDownConnection() {
        if (maxConnections==-1) return -1;
        LimitLatch latch = connectionLimitLatch;
        if (latch!=null) {
            long result = latch.countDown();
            if (result<0) {
                getLog().warn(sm.getString("endpoint.warn.incorrectConnectionCount"));
            }
            return result;
        } else return -1;
    }
    
    private static final int INITIAL_ERROR_DELAY = 50;
    private static final int MAX_ERROR_DELAY = 1600;
    protected int handleExceptionWithDelay(int currentErrorDelay) {
        // Don't delay on first exception
        if (currentErrorDelay > 0) {
            try {
                Thread.sleep(currentErrorDelay);
            } catch (InterruptedException e) {
                // Ignore
            }
        }

        // On subsequent exceptions, start the delay at 50ms, doubling the delay
        // on every subsequent exception until the delay reaches 1.6 seconds.
        if (currentErrorDelay == 0) {
            return INITIAL_ERROR_DELAY;
        } else if (currentErrorDelay < MAX_ERROR_DELAY) {
            return currentErrorDelay * 2;
        } else {
            return MAX_ERROR_DELAY;
        }
    }
        
        /**
         * 代码(1）中的无限循环用来一直等待客户端的连接，
         * 循环退出条件是调用了shutdown命令。
         * 代码（2）用来控制客户端的请求连接数量，如果连接数量达到设置的阈值，则当前请求会被挂起。
         * 代码（3）从TCP缓存获取一个完成三次握手的套接宇，如果当前没有，则当前线程会被阻塞挂起。
         * 当代码（3）获取到一个连接套接字后，
         * 代码（4）会调用setSocketOptions设置该套接字。
         */
	    @Override
        public void run() {

            int errorDelay = 0;

            // Loop until we receive a shutdown command
            //(1)一直循环命令
            while (running) {

                // Loop if endpoint is paused
                while (paused && running) {
                    state = AcceptorState.PAUSED;
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        // Ignore
                    }
                }

                if (!running) {
                    break;
                }
                state = AcceptorState.RUNNING;

                try {
                    //if we have reached max connections, wait
                	//(2)如果达到max connections 则等待
                    countUpOrAwaitConnection();

                    SocketChannel socket = null;
                    try {
                        // Accept the next incoming connection from the server
                        // socket
//                    	(3) 从TCP缓存获取一个完成三次握手的套接字，没有则阻塞
                        socket = serverSock.accept();
                    } catch (IOException ioe) {
                        // We didn't get a socket
                        countDownConnection();
                        if (running) {
                            // Introduce delay if necessary
                            errorDelay = handleExceptionWithDelay(errorDelay);
                            // re-throw
                            throw ioe;
                        } else {
                            break;
                        }
                    }
                    // Successful accept, reset the error delay
                    errorDelay = 0;

                    // Configure the socket
                    if (running && !paused) {
                        // setSocketOptions() will hand the socket off to
                        // an appropriate processor if successful
//                    	（4）设置套接字参数并封装套接字为事件任务，然后放入Poller的队列
                        if (!setSocketOptions(socket)) {
                            closeSocket(socket);
                        }
                    } else {
                        closeSocket(socket);
                    }
                } catch (Throwable t) {
                    ExceptionUtils.handleThrowable(t);
                    log.error(sm.getString("endpoint.accept.fail"), t);
                }
            }
            state = AcceptorState.ENDED;
        }

	protected boolean setSocketOptions(SocketChannel socket) {
        // Process the connection
//		处理连接
//        try {
//            //disable blocking, APR style, we are gonna be polling it
//            socket.configureBlocking(false);
//            Socket sock = socket.socket();
//            socketProperties.setProperties(sock);
//
//            NioChannel channel = nioChannels.pop();
//            if (channel == null) {
//                SocketBufferHandler bufhandler = new SocketBufferHandler(
//                        socketProperties.getAppReadBufSize(),
//                        socketProperties.getAppWriteBufSize(),
//                        socketProperties.getDirectBuffer());
//                if (isSSLEnabled()) {
//                    channel = new SecureNioChannel(socket, bufhandler, selectorPool, this);
//                } else {
//                    channel = new NioChannel(socket, bufhandler);
//                }
//            } else {
//                channel.setIOChannel(socket);
//                channel.reset();
//            }
//		       封装连接套接字为channel并注册到Poller队列
//            getPoller0().register(channel);
//        } catch (Throwable t) {
//            ExceptionUtils.handleThrowable(t);
//            try {
//                log.error("",t);
//            } catch (Throwable tt) {
//                ExceptionUtils.handleThrowable(tt);
//            }
//            // Tell to close the socket
//            return false;
//        }
        return true;
    }
	
    /**
     * Return an available poller in true round robin fashion.
     *
     * @return The next poller in sequence
     */
//    public Poller getPoller0() {
//        int idx = Math.abs(pollerRotater.incrementAndGet()) % pollers.length;
//        return pollers[idx];
//    }
//    
	
	 /**
     * 具体注册到事件队列
     *
     * @param socket    The newly created socket
     */
    public void register(final NioChannel socket) {
//        socket.setPoller(this);
//        NioSocketWrapper ka = new NioSocketWrapper(socket, NioEndpoint.this);
//        socket.setSocketWrapper(ka);
//        ka.setPoller(this);
//        ka.setReadTimeout(getSocketProperties().getSoTimeout());
//        ka.setWriteTimeout(getSocketProperties().getSoTimeout());
//        ka.setKeepAliveLeft(NioEndpoint.this.getMaxKeepAliveRequests());
//        ka.setSecure(isSSLEnabled());
//        ka.setReadTimeout(getConnectionTimeout());
//        ka.setWriteTimeout(getConnectionTimeout());
//        PollerEvent r = eventCache.pop();
//        ka.interestOps(SelectionKey.OP_READ);//this is what OP_REGISTER turns into.
//        if ( r==null) r = new PollerEvent(socket,ka,OP_REGISTER);
//        else r.reset(socket,ka,OP_REGISTER);
//        addEvent(r);
    }
    
    private void addEvent(PollerEvent event) {
//        events.offer(event);
//        if ( wakeupCounter.incrementAndGet() == 0 ) selector.wakeup();
    }
//      events的定义如下:
//    events是一个无界队列ConcurrentLinkedQueue，
//    根据前文讲的，使用队列作为同步转异步的方式要注意设置队列大小，
//    否则可能造成OOM。当然Tomcat肯定不会忽略这个问题，从代码（2）可以看出，
//    Tomcat让用户配置了一个最大连接数，超过这个数则会等待。
//    private final ConcurrentLinkedQueue<Runnable> events =
//            new ConcurrentLinkedQueue<>();
    
        private void closeSocket(SocketChannel socket) {
            countDownConnection();
            try {
                socket.socket().close();
            } catch (IOException ioe)  {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("endpoint.err.close"), ioe);
                }
            }
            try {
                socket.close();
            } catch (IOException ioe) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("endpoint.err.close"), ioe);
                }
            }
        }
    }