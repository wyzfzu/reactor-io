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

package reactor.io.netty.config;

import java.net.InetSocketAddress;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.ssl.SslContextBuilder;
import reactor.core.scheduler.TimedScheduler;
import reactor.io.netty.common.Peer;

/**
 * @author Stephane Maldini
 */
public class ClientOptions extends NettyOptions<ClientOptions> {

	/**
	 * Proxy Type
	 */
	public enum Proxy {
		HTTP,
		SOCK4,
		SOCK5
	}

	/**
	 * @return
	 */
	public static ClientOptions create(){
		return new ClientOptions();
	}

	/**
	 *
	 * @param host
	 * @return
	 */
	public static ClientOptions to(String host){
		return to(host, Peer.DEFAULT_PORT);
	}

	/**
	 *
	 * @param host
	 * @param port
	 * @return
	 */
	public static ClientOptions to(String host, int port){
		return create().connect(host, port);
	}

	String                                       proxyUsername;
	Function<? extends String, ? extends String> proxyPassword;
	Supplier<? extends InetSocketAddress>        proxyAddress;
	Proxy                                        proxyType;

	Supplier<? extends InetSocketAddress> connectAddress;

	ClientOptions(){

	}


	/**
	 * The host and port to which this client should connect.
	 *
	 * @param host The host to connect to.
	 * @param port The port to connect to.
	 * @return {@literal this}
	 */
	public ClientOptions connect(@Nonnull String host, int port) {
		return connect(new InetSocketAddress(host, port));
	}

	/**
	 * The address to which this client should connect.
	 *
	 * @param connectAddress The address to connect to.
	 * @return {@literal this}
	 */
	public ClientOptions connect(@Nonnull InetSocketAddress connectAddress) {
		return connect(() -> connectAddress);
	}

	/**
	 * The address to which this client should connect.
	 *
	 * @param connectAddress The address to connect to.
	 * @return {@literal this}
	 */
	public ClientOptions connect(@Nonnull Supplier<? extends InetSocketAddress> connectAddress) {
		if(this.connectAddress != null) {
			throw new IllegalStateException("Connect address is already set.");
		}
		this.connectAddress = connectAddress;
		return this;
	}

	/**
	 * The host and port to which this client should connect.
	 *
	 * @param host The host to connect to.
	 * @param port The port to connect to.
	 *
	 * @return {@literal this}
	 */
	public ClientOptions proxy(@Nonnull Proxy type,
			@Nonnull String host,
			int port,
			@Nullable String username,
			@Nullable Function<? extends String, ? extends String> password) {
		return proxy(type, new InetSocketAddress(host, port), username, password);
	}

	/**
	 * The host and port to which this client should connect.
	 *
	 * @param host The host to connect to.
	 * @param port The port to connect to.
	 *
	 * @return {@literal this}
	 */
	public ClientOptions proxy(@Nonnull Proxy type, @Nonnull String host, int port) {
		return proxy(type, new InetSocketAddress(host, port));
	}

	/**
	 * The address to which this client should connect.
	 *
	 * @param connectAddress The address to connect to.
	 *
	 * @return {@literal this}
	 */
	public ClientOptions proxy(@Nonnull Proxy type,
			@Nonnull InetSocketAddress connectAddress) {
		return proxy(type, () -> connectAddress, null, null);
	}

	/**
	 * The address to which this client should connect.
	 *
	 * @param connectAddress The address to connect to.
	 *
	 * @return {@literal this}
	 */
	public ClientOptions proxy(@Nonnull Proxy type,
			@Nonnull InetSocketAddress connectAddress,
			@Nullable String username,
			@Nullable Function<? extends String, ? extends String> password) {
		return proxy(type, () -> connectAddress, username, password);
	}

	/**
	 * The address to which this client should connect.
	 *
	 * @param connectAddress The address to connect to.
	 *
	 * @return {@literal this}
	 */
	public ClientOptions proxy(@Nonnull Proxy type,
			@Nonnull Supplier<? extends InetSocketAddress> connectAddress) {
		return proxy(type, connectAddress, null, null);
	}

	/**
	 * The address to which this client should connect.
	 *
	 * @param connectAddress The address to connect to.
	 *
	 * @return {@literal this}
	 */
	public ClientOptions proxy(@Nonnull Proxy type,
			@Nonnull Supplier<? extends InetSocketAddress> connectAddress,
			@Nullable String username,
			@Nullable Function<? extends String, ? extends String> password) {
		this.proxyUsername = username;
		this.proxyPassword = password;
		this.proxyAddress = connectAddress;
		this.proxyType = type;
		return this;
	}

	/**
	 * Return the eventual remote host
	 * @return the eventual remote host
	 */
	public InetSocketAddress remoteAddress() {
		return connectAddress != null ? connectAddress.get() : null;
	}

	/**
	 *
	 * @return this {@link ClientOptions}
	 */
	public ClientOptions sslSupport() {
		ssl(SslContextBuilder.forClient());
		return this;
	}

	/**
	 *
	 * @return immutable {@link ClientOptions}
	 */
	public ClientOptions toImmutable() {
		return new ImmutableClientOptions(this);
	}

	final static class ImmutableClientOptions extends ClientOptions {

		final ClientOptions options;

		ImmutableClientOptions(ClientOptions options) {
			this.options = options;
			if(options.ssl() != null){
					super.ssl(options.ssl());
			}
		}

		@Override
		public ClientOptions toImmutable() {
			return this;
		}

		@Override
		public InetSocketAddress remoteAddress() {
			return options.remoteAddress();
		}

		@Override
		public EventLoopGroup eventLoopGroup() {
			return options.eventLoopGroup();
		}

		@Override
		public boolean isManaged() {
			return options.isManaged();
		}

		@Override
		public boolean keepAlive() {
			return options.keepAlive();
		}

		@Override
		public int linger() {
			return options.linger();
		}

		@Override
		public Consumer<ChannelPipeline> pipelineConfigurer() {
			return options.pipelineConfigurer();
		}

		@Override
		public long prefetch() {
			return options.prefetch();
		}

		@Override
		public int rcvbuf() {
			return options.rcvbuf();
		}

		@Override
		public int sndbuf() {
			return options.sndbuf();
		}

		@Override
		public boolean tcpNoDelay() {
			return options.tcpNoDelay();
		}

		@Override
		public int timeout() {
			return options.timeout();
		}

		@Override
		public TimedScheduler timer() {
			return options.timer();
		}

		@Override
		public ClientOptions connect(@Nonnull String host, int port) {
			throw new UnsupportedOperationException("Immutable Options");
		}

		@Override
		public ClientOptions proxy(@Nonnull Proxy type,
				@Nonnull Supplier<? extends InetSocketAddress> connectAddress,
				@Nullable String username,
				@Nullable Function<? extends String, ? extends String> password) {
			throw new UnsupportedOperationException("Immutable Options");
		}

		@Override
		public ClientOptions connect(@Nonnull InetSocketAddress connectAddress) {
			throw new UnsupportedOperationException("Immutable Options");
		}

		@Override
		public ClientOptions eventLoopGroup(EventLoopGroup eventLoopGroup) {
			throw new UnsupportedOperationException("Immutable Options");
		}

		@Override
		public ClientOptions keepAlive(boolean keepAlive) {
			throw new UnsupportedOperationException("Immutable Options");
		}

		@Override
		public ClientOptions linger(int linger) {
			throw new UnsupportedOperationException("Immutable Options");
		}

		@Override
		public ClientOptions managed(boolean managed) {
			throw new UnsupportedOperationException("Immutable Options");
		}

		@Override
		public ClientOptions pipelineConfigurer(Consumer<ChannelPipeline> pipelineConfigurer) {
			throw new UnsupportedOperationException("Immutable Options");
		}

		@Override
		public ClientOptions prefetch(long prefetch) {
			throw new UnsupportedOperationException("Immutable Options");
		}

		@Override
		public ClientOptions rcvbuf(int rcvbuf) {
			throw new UnsupportedOperationException("Immutable Options");
		}

		@Override
		public ClientOptions ssl(SslContextBuilder sslOptions) {
			throw new UnsupportedOperationException("Immutable Options");
		}

		@Override
		public ClientOptions sslConfigurer(Consumer<? super SslContextBuilder> consumer) {
			throw new UnsupportedOperationException("Immutable Options");
		}

		@Override
		public ClientOptions sndbuf(int sndbuf) {
			throw new UnsupportedOperationException("Immutable Options");
		}

		@Override
		public ClientOptions tcpNoDelay(boolean tcpNoDelay) {
			throw new UnsupportedOperationException("Immutable Options");
		}

		@Override
		public ClientOptions timeout(int timeout) {
			throw new UnsupportedOperationException("Immutable Options");
		}

		@Override
		public ClientOptions timer(TimedScheduler timer) {
			throw new UnsupportedOperationException("Immutable Options");
		}
	}

}
