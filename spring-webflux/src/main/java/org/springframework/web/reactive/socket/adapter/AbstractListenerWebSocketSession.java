/*
 * Copyright 2002-2020 the original author or authors.
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

package org.springframework.web.reactive.socket.adapter;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.server.reactive.AbstractListenerReadPublisher;
import org.springframework.http.server.reactive.AbstractListenerWriteProcessor;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.web.reactive.socket.*;
import org.springframework.web.reactive.socket.WebSocketMessage.Type;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.util.concurrent.Queues;

import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Base class for {@link WebSocketSession} implementations that bridge between
 * event-listener WebSocket APIs (e.g. Java WebSocket API JSR-356, Jetty,
 * Undertow) and Reactive Streams.
 *
 * <p>Also implements {@code Subscriber<Void>} so it can be used to subscribe to
 * the completion of {@link WebSocketHandler#handle(WebSocketSession)}.
 *
 * @param <T> the native delegate type
 * @author Violeta Georgieva
 * @author Rossen Stoyanchev
 * @since 5.0
 */
public abstract class AbstractListenerWebSocketSession<T> extends AbstractWebSocketSession<T>
		implements Subscriber<Void> {

	/**
	 * The "back-pressure" buffer size to use if the underlying WebSocket API
	 * does not have flow control for receiving messages.
	 */
	private static final int RECEIVE_BUFFER_SIZE = 8192;


	@Nullable
	private final Sinks.Empty<Void> handlerCompletionSink;

	@Nullable
	@SuppressWarnings("deprecation")
	private final reactor.core.publisher.MonoProcessor<Void> handlerCompletionMono;

	private final WebSocketReceivePublisher receivePublisher;
	private final AtomicBoolean sendCalled = new AtomicBoolean();
	private final Sinks.One<CloseStatus> closeStatusSink = Sinks.one();
	@Nullable
	private volatile WebSocketSendProcessor sendProcessor;


	/**
	 * Base constructor.
	 *
	 * @param delegate      the native WebSocket session, channel, or connection
	 * @param id            the session id
	 * @param info          the handshake info
	 * @param bufferFactory the DataBuffer factor for the current connection
	 */
	public AbstractListenerWebSocketSession(
			T delegate, String id, HandshakeInfo info, DataBufferFactory bufferFactory) {

		this(delegate, id, info, bufferFactory, (Sinks.Empty<Void>) null);
	}

	/**
	 * Alternative constructor with completion sink to use to signal when the
	 * handling of the session is complete, with success or error.
	 * <p>Primarily for use with {@code WebSocketClient} to be able to
	 * communicate the end of handling.
	 */
	public AbstractListenerWebSocketSession(T delegate, String id, HandshakeInfo info,
											DataBufferFactory bufferFactory, @Nullable Sinks.Empty<Void> handlerCompletionSink) {

		super(delegate, id, info, bufferFactory);
		this.receivePublisher = new WebSocketReceivePublisher();
		this.handlerCompletionSink = handlerCompletionSink;
		this.handlerCompletionMono = null;
	}

	/**
	 * Alternative constructor with completion MonoProcessor to use to signal
	 * when the handling of the session is complete, with success or error.
	 * <p>Primarily for use with {@code WebSocketClient} to be able to
	 * communicate the end of handling.
	 *
	 * @deprecated as of 5.3 in favor of
	 * {@link #AbstractListenerWebSocketSession(Object, String, HandshakeInfo, DataBufferFactory, Sinks.Empty)}
	 */
	@Deprecated
	public AbstractListenerWebSocketSession(T delegate, String id, HandshakeInfo info,
											DataBufferFactory bufferFactory, @Nullable reactor.core.publisher.MonoProcessor<Void> handlerCompletion) {

		super(delegate, id, info, bufferFactory);
		this.receivePublisher = new WebSocketReceivePublisher();
		this.handlerCompletionMono = handlerCompletion;
		this.handlerCompletionSink = null;
	}


	protected WebSocketSendProcessor getSendProcessor() {
		WebSocketSendProcessor sendProcessor = this.sendProcessor;
		Assert.state(sendProcessor != null, "No WebSocketSendProcessor available");
		return sendProcessor;
	}

	@Override
	public Flux<WebSocketMessage> receive() {
		return (canSuspendReceiving() ? Flux.from(this.receivePublisher) :
				Flux.from(this.receivePublisher).onBackpressureBuffer(RECEIVE_BUFFER_SIZE));
	}

	@Override
	public Mono<Void> send(Publisher<WebSocketMessage> messages) {
		if (this.sendCalled.compareAndSet(false, true)) {
			WebSocketSendProcessor sendProcessor = new WebSocketSendProcessor();
			this.sendProcessor = sendProcessor;
			return Mono.from(subscriber -> {
				messages.subscribe(sendProcessor);
				sendProcessor.subscribe(subscriber);
			});
		} else {
			return Mono.error(new IllegalStateException("send() has already been called"));
		}
	}

	@Override
	public Mono<CloseStatus> closeStatus() {
		return this.closeStatusSink.asMono();
	}

	/**
	 * Whether the underlying WebSocket API has flow control and can suspend and
	 * resume the receiving of messages.
	 * <p><strong>Note:</strong> Sub-classes are encouraged to start out in
	 * suspended mode, if possible, and wait until demand is received.
	 */
	protected abstract boolean canSuspendReceiving();

	/**
	 * Suspend receiving until received message(s) are processed and more demand
	 * is generated by the downstream Subscriber.
	 * <p><strong>Note:</strong> if the underlying WebSocket API does not provide
	 * flow control for receiving messages, this method should be a no-op
	 * and {@link #canSuspendReceiving()} should return {@code false}.
	 */
	protected abstract void suspendReceiving();

	/**
	 * Resume receiving new message(s) after demand is generated by the
	 * downstream Subscriber.
	 * <p><strong>Note:</strong> if the underlying WebSocket API does not provide
	 * flow control for receiving messages, this method should be a no-op
	 * and {@link #canSuspendReceiving()} should return {@code false}.
	 */
	protected abstract void resumeReceiving();

	/**
	 * Send the given WebSocket message.
	 * <p><strong>Note:</strong> Sub-classes are responsible for releasing the
	 * payload data buffer, once fully written, if pooled buffers apply to the
	 * underlying container.
	 */
	protected abstract boolean sendMessage(WebSocketMessage message) throws IOException;


	// WebSocketHandler adapter delegate methods

	/**
	 * Handle a message callback from the WebSocketHandler adapter.
	 */
	void handleMessage(Type type, WebSocketMessage message) {
		this.receivePublisher.handleMessage(message);
	}

	/**
	 * Handle an error callback from the WebSocket engine.
	 */
	void handleError(Throwable ex) {
		// Ignore result: can't overflow, ok if not first or no one listens
		this.closeStatusSink.tryEmitEmpty();
		this.receivePublisher.onError(ex);
		WebSocketSendProcessor sendProcessor = this.sendProcessor;
		if (sendProcessor != null) {
			sendProcessor.cancel();
			sendProcessor.onError(ex);
		}
	}

	/**
	 * Handle a close callback from the WebSocket engine.
	 */
	void handleClose(CloseStatus closeStatus) {
		// Ignore result: can't overflow, ok if not first or no one listens
		this.closeStatusSink.tryEmitValue(closeStatus);
		this.receivePublisher.onAllDataRead();
		WebSocketSendProcessor sendProcessor = this.sendProcessor;
		if (sendProcessor != null) {
			sendProcessor.cancel();
			sendProcessor.onComplete();
		}
	}


	// Subscriber<Void> implementation tracking WebSocketHandler#handle completion

	@Override
	public void onSubscribe(Subscription subscription) {
		subscription.request(Long.MAX_VALUE);
	}

	@Override
	public void onNext(Void aVoid) {
		// no op
	}

	@Override
	public void onError(Throwable ex) {
		if (this.handlerCompletionSink != null) {
			// Ignore result: can't overflow, ok if not first or no one listens
			this.handlerCompletionSink.tryEmitError(ex);
		}
		if (this.handlerCompletionMono != null) {
			this.handlerCompletionMono.onError(ex);
		}
		close(CloseStatus.SERVER_ERROR.withReason(ex.getMessage()));
	}

	@Override
	public void onComplete() {
		if (this.handlerCompletionSink != null) {
			// Ignore result: can't overflow, ok if not first or no one listens
			this.handlerCompletionSink.tryEmitEmpty();
		}
		if (this.handlerCompletionMono != null) {
			this.handlerCompletionMono.onComplete();
		}
		close();
	}


	private final class WebSocketReceivePublisher extends AbstractListenerReadPublisher<WebSocketMessage> {

		private volatile Queue<Object> pendingMessages = Queues.unbounded(Queues.SMALL_BUFFER_SIZE).get();


		WebSocketReceivePublisher() {
			super(AbstractListenerWebSocketSession.this.getLogPrefix());
		}


		@Override
		protected void checkOnDataAvailable() {
			resumeReceiving();
			int size = this.pendingMessages.size();
			if (rsReadLogger.isTraceEnabled()) {
				rsReadLogger.trace(getLogPrefix() + "checkOnDataAvailable (" + size + " pending)");
			}
			if (size > 0) {
				onDataAvailable();
			}
		}

		@Override
		protected void readingPaused() {
			suspendReceiving();
		}

		@Override
		@Nullable
		protected WebSocketMessage read() throws IOException {
			return (WebSocketMessage) this.pendingMessages.poll();
		}

		void handleMessage(WebSocketMessage message) {
			if (logger.isTraceEnabled()) {
				logger.trace(getLogPrefix() + "Received " + message);
			} else if (rsReadLogger.isTraceEnabled()) {
				rsReadLogger.trace(getLogPrefix() + "Received " + message);
			}
			if (!this.pendingMessages.offer(message)) {
				discardData();
				throw new IllegalStateException(
						"Too many messages. Please ensure WebSocketSession.receive() is subscribed to.");
			}
			onDataAvailable();
		}

		@Override
		protected void discardData() {
			while (true) {
				WebSocketMessage message = (WebSocketMessage) this.pendingMessages.poll();
				if (message == null) {
					return;
				}
				message.release();
			}
		}
	}


	/**
	 * Processor to send web socket messages.
	 */
	protected final class WebSocketSendProcessor extends AbstractListenerWriteProcessor<WebSocketMessage> {

		private volatile boolean isReady = true;


		WebSocketSendProcessor() {
			super(receivePublisher.getLogPrefix());
		}


		@Override
		protected boolean write(WebSocketMessage message) throws IOException {
			if (logger.isTraceEnabled()) {
				logger.trace(getLogPrefix() + "Sending " + message);
			} else if (rsWriteLogger.isTraceEnabled()) {
				rsWriteLogger.trace(getLogPrefix() + "Sending " + message);
			}
			// In case of IOException, onError handling should call discardData(WebSocketMessage)..
			return sendMessage(message);
		}

		@Override
		protected boolean isDataEmpty(WebSocketMessage message) {
			return (message.getPayload().readableByteCount() == 0);
		}

		@Override
		protected boolean isWritePossible() {
			return (this.isReady);
		}

		/**
		 * Sub-classes can invoke this before sending a message (false) and
		 * after receiving the async send callback (true) effective translating
		 * async completion callback into simple flow control.
		 */
		public void setReadyToSend(boolean ready) {
			if (ready && rsWriteLogger.isTraceEnabled()) {
				rsWriteLogger.trace(getLogPrefix() + "Ready to send");
			}
			this.isReady = ready;
		}

		@Override
		protected void discardData(WebSocketMessage message) {
			message.release();
		}
	}

}
