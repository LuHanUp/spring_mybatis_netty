package com.luhan.handlers;

import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.List;
import java.util.Map.Entry;

import javax.servlet.Servlet;
import javax.servlet.ServletContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.stream.ChunkedStream;
import io.netty.util.CharsetUtil;

//NettyHandler与servlet之间的转换
public class ServletNettyHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
	private final static Logger logger = LoggerFactory.getLogger(ServletNettyHandler.class);
	private Servlet servlet;
	private ServletContext servletContext;

	public ServletNettyHandler(Servlet servlet) {
		this.servlet = servlet;
		this.servletContext = servlet.getServletConfig().getServletContext();
	}

	@Override
	protected void channelRead0(ChannelHandlerContext channelHandlerContext, FullHttpRequest fullHttpRequest) throws Exception {
		if (!fullHttpRequest.decoderResult().isSuccess()) {
			sendError(channelHandlerContext, BAD_REQUEST);
			return;
		}
		MockHttpServletRequest servletRequest = new MockHttpServletRequest(this.servletContext);
		MockHttpServletResponse servletResponse = new MockHttpServletResponse();
		createServletRequest(fullHttpRequest, servletRequest);
		try {
			// 请求转给springMVC处理
			servlet.service(servletRequest, servletResponse);
			HttpResponseStatus status = HttpResponseStatus.valueOf(servletResponse.getStatus());
			HttpResponse response = new DefaultHttpResponse(HTTP_1_1, status);
			for (String name : servletResponse.getHeaderNames()) {
				for (Object value : servletResponse.getHeaderValues(name)) {
					response.headers().add(name, value);
				}
			}
			writeResponse(channelHandlerContext, servletResponse.getContentAsByteArray(), response);
		} catch (Exception e) {
			logger.error(e.getLocalizedMessage());
			HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
			writeResponse(channelHandlerContext, servletResponse.getContentAsByteArray(), response);
		}
	}

	private void writeResponse(ChannelHandlerContext channelHandlerContext, byte[] bytes, HttpResponse response) {
		response.headers().add(io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
		response.headers().add("Access-Control-Allow-Origin", "*");
		response.headers().add("Access-Control-Allow-Methods", "*");
		response.headers().add("Access-Control-Max-Age", "100");
		response.headers().add("Access-Control-Allow-Headers", "Content-Type");
		response.headers().add("Access-Control-Allow-Credentials", "false");
		// Write the initial line and the header.
		channelHandlerContext.write(response);
		
		// Write the content and flush it.
		InputStream contentStream = new ByteArrayInputStream(bytes);
		ChannelFuture writeFuture = channelHandlerContext.writeAndFlush(new ChunkedStream(contentStream));
		writeFuture.addListener(ChannelFutureListener.CLOSE);
	}
	/**
	 * netty的HttpRequest转换为ServletRequest
	 * @param servletRequest2 
	 */
	private void createServletRequest(FullHttpRequest fullHttpRequest, MockHttpServletRequest servletRequest) {
		UriComponents uriComponents = UriComponentsBuilder.fromUriString(sanitizeUri(fullHttpRequest.uri())).build();

		servletRequest.setRequestURI(uriComponents.getPath());
		servletRequest.setPathInfo(uriComponents.getPath());
		servletRequest.setMethod(fullHttpRequest.method().name());

		if (uriComponents.getScheme() != null) {
			servletRequest.setScheme(uriComponents.getScheme());
		}
		if (uriComponents.getHost() != null) {
			servletRequest.setServerName(uriComponents.getHost());
		}
		if (uriComponents.getPort() != -1) {
			servletRequest.setServerPort(uriComponents.getPort());
		}

		for (String name : fullHttpRequest.headers().names()) {
			servletRequest.addHeader(name, fullHttpRequest.headers().get(name));
		}

        // 将post请求的参数，添加到HttpServletRrequest的parameter
        try {
            ByteBuf buf = fullHttpRequest.content();
            int readable = buf.readableBytes();
            byte[] bytes = new byte[readable];
            buf.readBytes(bytes);
            String contentStr = UriUtils.decode(new String(bytes,"UTF-8"), "UTF-8");
            if (StringUtils.hasLength(contentStr)) {
            	for(String params : contentStr.split("&")){
            		String[] para = params.split("=");
            		if(para.length > 1){
            			servletRequest.addParameter(para[0], para[1]);
            		} else {
            			servletRequest.addParameter(para[0], "");
            		}
            	}
            }
        } catch (UnsupportedEncodingException e) {
        	logger.error(e.getLocalizedMessage());
        }
        // 将get请求的参数，添加到HttpServletRrequest的parameter
		try {
			if (uriComponents.getQuery() != null) {
				String query = UriUtils.decode(uriComponents.getQuery(), "UTF-8");
				servletRequest.setQueryString(query);
			}
			for (Entry<String, List<String>> entry : uriComponents.getQueryParams().entrySet()) {
				for (String value : entry.getValue()) {
					servletRequest.addParameter(
							UriUtils.decode(entry.getKey(), "UTF-8"),
							UriUtils.decode(value, "UTF-8"));
				}
			}
		}catch (UnsupportedEncodingException ex) {
			logger.error(ex.getLocalizedMessage());
		}
	}
	private String sanitizeUri(String uri) {
        try {
            uri = URLDecoder.decode(uri, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            try {
                uri = URLDecoder.decode(uri, "ISO-8859-1");
            } catch (UnsupportedEncodingException e1) {
                throw new Error();
            }
        }
        return uri;
    }

	private static void sendError(ChannelHandlerContext ctx, HttpResponseStatus status) {
		ByteBuf content = Unpooled.copiedBuffer(
				"Failure: " + status.toString() + "\r\n",
				CharsetUtil.UTF_8);

		FullHttpResponse fullHttpResponse = new DefaultFullHttpResponse(
				HTTP_1_1,
				status,
				content
		);
		fullHttpResponse.headers().add("Access-Control-Allow-Origin", "*");
		fullHttpResponse.headers().add("Access-Control-Allow-Methods", "*");
		fullHttpResponse.headers().add("Access-Control-Max-Age", "100");
		fullHttpResponse.headers().add("Access-Control-Allow-Headers", "Content-Type");
		fullHttpResponse.headers().add("Access-Control-Allow-Credentials", "false");
		fullHttpResponse.headers().add(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");

		// Close the connection as soon as the error message is sent.
		ctx.write(fullHttpResponse).addListener(ChannelFutureListener.CLOSE);
	}
	
	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		logger.error(cause.getLocalizedMessage());
		if (ctx.channel().isActive()) {
			sendError(ctx, INTERNAL_SERVER_ERROR);
		}
	}
}
