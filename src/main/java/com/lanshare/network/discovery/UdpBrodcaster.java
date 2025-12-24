package com.lanshare.network.discovery;
import com.lanshare.network.config.NetworkConfig;

import java.io.IOException;
import java.net.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
public class UdpBrodcaster {
    private static final Logger logger=Logger.getLogger(UdpBrodcaster.class.getName());
    private final int port=NetworkConfig.getUdpPort();
    private final int bufferSize=NetworkConfig.UDP_BUFFER_SIZE;

    private DatagramSocket socket;//UDP Socket
    private InetAddress broadcastAddress;//广播地址
    private ExecutorService listenerExecutor;//用于运行监听任务的单线程池


    private volatile boolean running=false;

    public interface UdpMessageListener {
        void onMessage(String message,InetAddress senderAddress);//当收到消息时调用
    }
    //把自己注册为监听器·
    private UdpMessageListener udpMessageListener;
    public void setMessageListener(UdpMessageListener udpMessageListener) {
        this.udpMessageListener = udpMessageListener;
    }

    public void start() throws IOException{
        if(running){
            logger.warning("UdpBrodcaster is already running");
            return;
        }
        //初始化Socket，并绑定到指定端口
        try{
            socket=new DatagramSocket(port);
            socket.setReuseAddress(true);//允许端口重用
            socket.setBroadcast(true);//允许发送广播
            logger.info("UDP Socket 已在端口"+port+"启动并绑定");

        }catch(Exception e){
            logger.severe("启动 UDP Socket 失败（端口 " + port + " 可能已被占用）：" + e.getMessage());
            throw e; // 向上抛出异常，通知上层启动失败
        }
        //获取广播地址
        try{
            broadcastAddress=InetAddress.getByName(NetworkConfig.getBroadcastAddress());
            logger.info("使用广播地址"+broadcastAddress.getHostAddress());
        }catch(UnknownHostException e){
            logger.severe("获取广播地址失败"+e.getMessage());
            throw e;
        }
        //创建并启动监听
        listenerExecutor=Executors.newSingleThreadExecutor();
        listenerExecutor.submit(this::listen);

        running=true;
        logger.info("UdpBrodcaster is started");

    }

    public void stop(){
        if(!running){
            return;
        }
        running=false;
        //关闭Socket
        if(socket!=null&&!socket.isClosed()){
            socket.close();
            logger.info("UDP Socket已关闭");
        }
        if(listenerExecutor!=null&&!listenerExecutor.isShutdown()){
            listenerExecutor.shutdown();
            logger.info("监听线程池已关闭");
        }
        logger.info("UdpBrodcaster 服务已停止");

    }
    public void sendBroadcast(String message){
        if(!running&&socket==null||socket.isClosed()){
            logger.info(" 广播未运行，无法发送消息");
            return;
        }
        try{
            byte[] data=message.getBytes("UTF-8");
            //创建数据包
            DatagramPacket packet=new DatagramPacket(data,data.length,broadcastAddress,port);

            socket.send(packet);

        }catch(IOException e){
            logger.severe("发送广播失败"+e.getMessage());
        }
    }
    private void listen(){
        logger.info("UDP监听线程已经启动");
        byte[] buffer=new byte[bufferSize];
        while(running){
            try{
                DatagramPacket packet=new DatagramPacket(buffer,buffer.length);
                socket.receive(packet);

                //解析数据包
                String message = new String(
                        packet.getData(),
                        0,
                        packet.getLength(),
                        "UTF-8"
                );
                InetAddress address=packet.getAddress();
                logger.info("收到来自"+address.getHostAddress()+"的消息");

                if(udpMessageListener!=null){
                    udpMessageListener.onMessage(message,address);
                }
            }catch(SocketException e){
                if (!running) {
                    // 这是预期的关闭
                    logger.info("UDP 监听线程正常停止");
                } else {
                    // 非预期的异常
                    logger.severe("Socket 异常: " + e.getMessage());
                }
            }catch(IOException e){
                if (running) {
                    logger.warning("监听时发生 IO 异常: " + e.getMessage());
                }
            }
        }
    }

}
