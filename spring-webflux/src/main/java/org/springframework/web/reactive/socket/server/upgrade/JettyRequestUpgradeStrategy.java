/*
 * Copyright 2002-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.web.reactive.socket.server.upgrade;

import org.eclipse.jetty.websocket.api.WebSocketPolicy;
import org.eclipse.jetty.websocket.server.WebSocketServerFactory;
import org.springframework.context.Lifecycle;
import org.springframework.core.NamedThreadLocal;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.web.reactive.socket.HandshakeInfo;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.adapter.ContextWebSocketHandler;
import org.springframework.web.reactive.socket.adapter.JettyWebSocketHandlerAdapter;
import org.springframework.web.reactive.socket.adapter.JettyWebSocketSession;
import org.springframework.web.reactive.socket.server.RequestUpgradeStrategy;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.function.Supplier;

/**
 * A {@link RequestUpgradeStrategy} for use with Jetty.
 *
 * @author Violeta Georgieva
 * @author Rossen Stoyanchev
 * @since 5.0
 */
public class JettyRequestUpgradeStrategy implements RequestUpgradeStrategy, Lifecycle {

	private static final ThreadLocal<WebSocketHandlerContainer> adapterHolder =
			new NamedThreadLocal<>("JettyWebSocketHandlerAdapter");
	private final Object lifecycleMonitor = new Object();
	@Nullable
	private WebSocketPolicy webSocketPolicy;
	@Nullable
	private WebSocketServerFactory factory;
	@Nullable
	private volatile ServletContext servletContext;
	private volatile boolean running;

	/**
	 * Return the configured {@link WebSocketPolicy}, if any.
	 */
	@Nullable
	public WebSocketPolicy getWebSocketPolicy() {
		return this.webSocketPolicy;
	}

	/**
	 * Configure a {@link WebSocketPolicy} to use to initialize
	 * {@link WebSocketServerFactory}.
	 *
	 * @param webSocketPolicy the WebSocket settings
	 */
	public void setWebSocketPolicy(WebSocketPolicy webSocketPolicy) {
		this.webSocketPolicy = webSocketPolicy;
	}

	@Override
	public void start() {
		synchronized (this.lifecycleMonitor) {
			ServletContext servletContext = this.servletContext;
			if (!isRunning() && servletContext != null) {
				try {
					this.factory = (this.webSocketPolicy != null ?
							new WebSocketServerFactory(servletContext, this.webSocketPolicy) :
							new WebSocketServerFactory(servletContext));
					this.factory.setCreator((request, response) -> {
						WebSocketHandlerContainer container = adapterHolder.get();
						String protocol = container.getProtocol();
						if (protocol != null) {
							response.setAcceptedSubProtocol(protocol);
						}
						return container.getAdapter();
					});
					this.factory.start();
					this.running = true;
				} catch (Throwable ex) {
					throw new IllegalStateException("Unable to start WebSocketServerFactory", ex);
				}
			}
		}
	}

	@Override
	public void stop() {
		synchronized (this.lifecycleMonitor) {
			if (isRunning()) {
				if (this.factory != null) {
					try {
						this.factory.stop();
						this.running = false;
					} catch (Throwable ex) {
						throw new IllegalStateException("Failed to stop WebSocketServerFactory", ex);
					}
				}
			}
		}
	}

	@Override
	public boolean isRunning() {
		return this.running;
	}


	@Override
	public Mono<Void> upgrade(ServerWebExchange exchange, WebSocketHandler handler,
							  @Nullable String subProtocol, Supplier<HandshakeInfo> handshakeInfoFactory) {

		ServerHttpRequest request = exchange.getRequest();
		ServerHttpResponse response = exchange.getResponse();

		HttpServletRequest servletRequest = ServerHttpRequestDecorator.getNativeRequest(request);
		HttpServletResponse servletResponse = ServerHttpResponseDecorator.getNativeResponse(response);

		HandshakeInfo handshakeInfo = handshakeInfoFactory.get();
		DataBufferFactory factory = response.bufferFactory();

		startLazily(servletRequest);

		Assert.state(this.factory != null, "No WebSocketServerFactory available");
		boolean isUpgrade = this.factory.isUpgradeRequest(servletRequest, servletResponse);
		Assert.isTrue(isUpgrade, "Not a WebSocket handshake");

		// Trigger WebFlux preCommit actions and upgrade
		return exchange.getResponse().setComplete()
				.then(Mono.deferContextual(contextView -> {
					JettyWebSocketHandlerAdapter adapter = new JettyWebSocketHandlerAdapter(
							ContextWebSocketHandler.decorate(handler, contextView),
							session -> new JettyWebSocketSession(session, handshakeInfo, factory));

					try {
						adapterHolder.set(new WebSocketHandlerContainer(adapter, subProtocol));
						this.factory.acceptWebSocket(servletRequest, servletResponse);
					} catch (IOException ex) {
						return Mono.error(ex);
					} finally {
						adapterHolder.remove();
					}
					return Mono.empty();
				}));
	}

	private void startLazily(HttpServletRequest request) {
		if (isRunning()) {
			return;
		}
		synchronized (this.lifecycleMonitor) {
			if (!isRunning()) {
				this.servletContext = request.getServletContext();
				start();
			}
		}
	}


	private static class WebSocketHandlerContainer {

		private final JettyWebSocketHandlerAdapter adapter;

		@Nullable
		private final String protocol;

		public WebSocketHandlerContainer(JettyWebSocketHandlerAdapter adapter, @Nullable String protocol) {
			this.adapter = adapter;
			this.protocol = protocol;
		}

		public JettyWebSocketHandlerAdapter getAdapter() {
			return this.adapter;
		}

		@Nullable
		public String getProtocol() {
			return this.protocol;
		}
	}

}
