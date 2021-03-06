/*
 * Copyright (c) 2011-2016 Pivotal Software Inc, All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package reactor.io.netty.http;

import java.io.IOException;
import java.net.URI;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.LastHttpContent;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.publisher.DirectProcessor;
import reactor.core.publisher.Flux;
import reactor.core.subscriber.BaseSubscriber;
import reactor.core.util.BackpressureUtils;
import reactor.core.util.EmptySubscription;
import reactor.io.ipc.ChannelHandler;
import reactor.io.netty.common.ChannelBridge;
import reactor.io.netty.common.NettyChannel;
import reactor.io.netty.common.NettyChannelHandler;

/**
 * @author Stephane Maldini
 */
class NettyHttpClientHandler extends NettyChannelHandler<HttpClientChannel> {

	HttpClientChannel                      httpChannel;
	DirectProcessor<Void>                  connectSignal;
	Subscriber<? super HttpClientResponse> replySubscriber;

	public NettyHttpClientHandler(ChannelHandler<ByteBuf, ByteBuf, NettyChannel> handler,
			ChannelBridge<HttpClientChannel> channelBridge) {
		super(handler, channelBridge);
	}

	@Override
	public void channelActive(final ChannelHandlerContext ctx) throws Exception {
		ctx.fireChannelActive();

		if (httpChannel != null) {
			return;
		}

		httpChannel = bridgeFactory.createChannelBridge(ctx.channel());
		httpChannel.keepAlive(true);
		HttpUtil.setTransferEncodingChunked(httpChannel.nettyRequest, true);

		handler.apply(httpChannel)
		       .subscribe(new BaseSubscriber<Void>() {
			       @Override
			       public void onSubscribe(final Subscription s) {
				       ctx.read();
				       BackpressureUtils.validate(null, s);
				       s.request(Long.MAX_VALUE);
			       }

			       @Override
			       public void onError(Throwable t) {
				       BaseSubscriber.super.onError(t);
				       if(t instanceof IOException && t.getMessage() != null && t.getMessage().contains("Broken pipe")){
					       if (log.isDebugEnabled()) {
						       log.debug("Connection closed remotely", t);
					       }
					       return;
				       }
				       if (ctx.channel()
				              .isOpen()) {
					       ctx.channel()
					          .close();
				       }
			       }
		       });
	}

	void bridgeReply(Subscriber<? super HttpClientResponse> replySubscriber,
			DirectProcessor<Void> connectSignal) {
		this.replySubscriber = replySubscriber;
		this.connectSignal = connectSignal;
	}

	@Override
	protected void doOnTerminate(ChannelHandlerContext ctx,
			ChannelFuture last,
			ChannelPromise promise,
			Throwable exception) {
		ctx.channel().write(LastHttpContent.EMPTY_LAST_CONTENT);
		super.doOnTerminate(ctx, last, promise, exception);
	}

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
		Class<?> messageClass = msg.getClass();
		if (HttpResponse.class.isAssignableFrom(messageClass)) {

			HttpResponse response = (HttpResponse) msg;

			if (httpChannel != null) {
				httpChannel.setNettyResponse(response);
			}

			if(log.isDebugEnabled()){
				log.debug("Received response (auto-read:{}) : {}", ctx.channel().config
						().isAutoRead(), httpChannel.headers().toString());
			}

			if(checkResponseCode(ctx, response)) {
				ctx.fireChannelRead(msg);
				if (replySubscriber != null) {
					Flux.just(httpChannel)
					    .subscribe(replySubscriber);
				}
				else {
					log.debug(
							"No Response/ HttpInbound subscriber on {}, msg is dropped {}",
							ctx.channel(),
							msg);
				}
			}
			postRead(ctx, msg);
			return;
		}
		if(LastHttpContent.EMPTY_LAST_CONTENT != msg){
			doRead(ctx, msg);
		}
		postRead(ctx, msg);
	}

	final NettyWebSocketClientHandler withWebsocketSupport(URI url, String
			protocols, boolean textPlain){
		//prevent further header to be sent for handshaking
		if(!httpChannel.markHeadersAsFlushed()){
			log.error("Cannot enable websocket if headers have already been sent");
			return null;
		}
		return new NettyWebSocketClientHandler(url, protocols, this, textPlain);
	}

	final boolean checkResponseCode(ChannelHandlerContext ctx, HttpResponse response) throws
	                                                                          Exception {
		int code = response.status()
		                   .code();
		if (code >= 400) {
			Exception ex = new HttpException(httpChannel);
			if (connectSignal != null) {
				connectSignal.onError(ex);
			}
			else if (replySubscriber != null) {
				EmptySubscription.error(replySubscriber, ex);
			}
			return false;
		}
		if (code >= 300 && httpChannel.isFollowRedirect()) {
			Exception ex = new RedirectException(httpChannel);
			if (connectSignal != null) {
				connectSignal.onError(ex);
			}
			else if (replySubscriber != null) {
				EmptySubscription.error(replySubscriber, ex);
			}
			return false;
		}
		if (connectSignal != null) {
			connectSignal.onComplete();
		}
		return true;
	}

	protected void postRead(ChannelHandlerContext ctx, Object msg){
		if (msg instanceof LastHttpContent) {
			ctx.channel().close();
			if (this.channelSubscriber != null) {
				channelSubscriber.onComplete();
				if(channelSubscriber.downstream() != null) {
					channelSubscriber = null;
				}
			}
		}
	}

	@Override
	public String getName() {
		return httpChannel != null ? httpChannel.getName() : "HTTP Client Connection";
	}

}
