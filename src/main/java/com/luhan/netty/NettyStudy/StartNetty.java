package com.luhan.netty.NettyStudy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.luhan.handlers.DispatcherServletChannelInitializer;
import com.luhan.handlers.MvcConfig;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

/**
 * 项目启动类
 * @author luHan
 */
public class StartNetty {
	private final static Logger logger = LoggerFactory.getLogger(StartNetty.class);

	private final int port;

	public StartNetty(int port){
		this.port = port;
	}
	/**
	 * Netty启动
	 */
	public void run() {
		EventLoopGroup bossGroup = new NioEventLoopGroup(1);
		EventLoopGroup workerGroup = new NioEventLoopGroup();
		try {
			ServerBootstrap server = new ServerBootstrap();
			server.group(bossGroup, workerGroup)
					.channel(NioServerSocketChannel.class)
					.childHandler(new DispatcherServletChannelInitializer(MvcConfig.class));
			logger.info("Netty server has started on port : " + port);
			server.bind(port).sync().channel().closeFuture().sync();
		} catch (Exception e){ 
			logger.error(e.getLocalizedMessage());
        } finally {
        	bossGroup.shutdownGracefully();
        	workerGroup.shutdownGracefully();
		}
	}
	public static void main(String[] args) throws Exception {
		new StartNetty(9000).run();
	}
}
