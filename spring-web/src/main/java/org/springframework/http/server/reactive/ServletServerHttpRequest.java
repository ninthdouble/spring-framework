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

package org.springframework.http.server.reactive;

import org.apache.commons.logging.Log;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.util.*;
import reactor.core.publisher.Flux;

import javax.servlet.*;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Map;

/**
 * Adapt {@link ServerHttpRequest} to the Servlet {@link HttpServletRequest}.
 *
 * @author Rossen Stoyanchev
 * @since 5.0
 */
class ServletServerHttpRequest extends AbstractServerHttpRequest {

	static final DataBuffer EOF_BUFFER = DefaultDataBufferFactory.sharedInstance.allocateBuffer(0);


	private final HttpServletRequest request;

	private final RequestBodyPublisher bodyPublisher;

	private final Object cookieLock = new Object();

	private final DataBufferFactory bufferFactory;

	private final byte[] buffer;

	private final AsyncListener asyncListener;


	public ServletServerHttpRequest(HttpServletRequest request, AsyncContext asyncContext,
									String servletPath, DataBufferFactory bufferFactory, int bufferSize)
			throws IOException, URISyntaxException {

		this(createDefaultHttpHeaders(request), request, asyncContext, servletPath, bufferFactory, bufferSize);
	}

	public ServletServerHttpRequest(MultiValueMap<String, String> headers, HttpServletRequest request,
									AsyncContext asyncContext, String servletPath, DataBufferFactory bufferFactory, int bufferSize)
			throws IOException, URISyntaxException {

		super(initUri(request), request.getContextPath() + servletPath, initHeaders(headers, request));

		Assert.notNull(bufferFactory, "'bufferFactory' must not be null");
		Assert.isTrue(bufferSize > 0, "'bufferSize' must be higher than 0");

		this.request = request;
		this.bufferFactory = bufferFactory;
		this.buffer = new byte[bufferSize];

		this.asyncListener = new RequestAsyncListener();

		// Tomcat expects ReadListener registration on initial thread
		ServletInputStream inputStream = request.getInputStream();
		this.bodyPublisher = new RequestBodyPublisher(inputStream);
		this.bodyPublisher.registerReadListener();
	}


	private static MultiValueMap<String, String> createDefaultHttpHeaders(HttpServletRequest request) {
		MultiValueMap<String, String> headers =
				CollectionUtils.toMultiValueMap(new LinkedCaseInsensitiveMap<>(8, Locale.ENGLISH));
		for (Enumeration<?> names = request.getHeaderNames(); names.hasMoreElements(); ) {
			String name = (String) names.nextElement();
			for (Enumeration<?> values = request.getHeaders(name); values.hasMoreElements(); ) {
				headers.add(name, (String) values.nextElement());
			}
		}
		return headers;
	}

	private static URI initUri(HttpServletRequest request) throws URISyntaxException {
		Assert.notNull(request, "'request' must not be null");
		StringBuffer url = request.getRequestURL();
		String query = request.getQueryString();
		if (StringUtils.hasText(query)) {
			url.append('?').append(query);
		}
		return new URI(url.toString());
	}

	private static MultiValueMap<String, String> initHeaders(
			MultiValueMap<String, String> headerValues, HttpServletRequest request) {

		HttpHeaders headers = null;
		MediaType contentType = null;
		if (!StringUtils.hasLength(headerValues.getFirst(HttpHeaders.CONTENT_TYPE))) {
			String requestContentType = request.getContentType();
			if (StringUtils.hasLength(requestContentType)) {
				contentType = MediaType.parseMediaType(requestContentType);
				headers = new HttpHeaders(headerValues);
				headers.setContentType(contentType);
			}
		}
		if (contentType != null && contentType.getCharset() == null) {
			String encoding = request.getCharacterEncoding();
			if (StringUtils.hasLength(encoding)) {
				Map<String, String> params = new LinkedCaseInsensitiveMap<>();
				params.putAll(contentType.getParameters());
				params.put("charset", Charset.forName(encoding).toString());
				headers.setContentType(new MediaType(contentType, params));
			}
		}
		if (headerValues.getFirst(HttpHeaders.CONTENT_TYPE) == null) {
			int contentLength = request.getContentLength();
			if (contentLength != -1) {
				headers = (headers != null ? headers : new HttpHeaders(headerValues));
				headers.setContentLength(contentLength);
			}
		}
		return (headers != null ? headers : headerValues);
	}


	@Override
	public String getMethodValue() {
		return this.request.getMethod();
	}

	@Override
	protected MultiValueMap<String, HttpCookie> initCookies() {
		MultiValueMap<String, HttpCookie> httpCookies = new LinkedMultiValueMap<>();
		Cookie[] cookies;
		synchronized (this.cookieLock) {
			cookies = this.request.getCookies();
		}
		if (cookies != null) {
			for (Cookie cookie : cookies) {
				String name = cookie.getName();
				HttpCookie httpCookie = new HttpCookie(name, cookie.getValue());
				httpCookies.add(name, httpCookie);
			}
		}
		return httpCookies;
	}

	@Override
	@NonNull
	public InetSocketAddress getLocalAddress() {
		return new InetSocketAddress(this.request.getLocalAddr(), this.request.getLocalPort());
	}

	@Override
	@NonNull
	public InetSocketAddress getRemoteAddress() {
		return new InetSocketAddress(this.request.getRemoteHost(), this.request.getRemotePort());
	}

	@Override
	@Nullable
	protected SslInfo initSslInfo() {
		X509Certificate[] certificates = getX509Certificates();
		return certificates != null ? new DefaultSslInfo(getSslSessionId(), certificates) : null;
	}

	@Nullable
	private String getSslSessionId() {
		return (String) this.request.getAttribute("javax.servlet.request.ssl_session_id");
	}

	@Nullable
	private X509Certificate[] getX509Certificates() {
		String name = "javax.servlet.request.X509Certificate";
		return (X509Certificate[]) this.request.getAttribute(name);
	}

	@Override
	public Flux<DataBuffer> getBody() {
		return Flux.from(this.bodyPublisher);
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T getNativeRequest() {
		return (T) this.request;
	}

	/**
	 * Return an {@link RequestAsyncListener} that completes the request body
	 * Publisher when the Servlet container notifies that request input has ended.
	 * The listener is not actually registered but is rather exposed for
	 * {@link ServletHttpHandlerAdapter} to ensure events are delegated.
	 */
	AsyncListener getAsyncListener() {
		return this.asyncListener;
	}

	/**
	 * Read from the request body InputStream and return a DataBuffer.
	 * Invoked only when {@link ServletInputStream#isReady()} returns "true".
	 *
	 * @return a DataBuffer with data read, or {@link #EOF_BUFFER} if the input
	 * stream returned -1, or null if 0 bytes were read.
	 */
	@Nullable
	DataBuffer readFromInputStream() throws IOException {
		int read = this.request.getInputStream().read(this.buffer);
		logBytesRead(read);

		if (read > 0) {
			DataBuffer dataBuffer = this.bufferFactory.allocateBuffer(read);
			dataBuffer.write(this.buffer, 0, read);
			return dataBuffer;
		}

		if (read == -1) {
			return EOF_BUFFER;
		}

		return null;
	}

	protected final void logBytesRead(int read) {
		Log rsReadLogger = AbstractListenerReadPublisher.rsReadLogger;
		if (rsReadLogger.isTraceEnabled()) {
			rsReadLogger.trace(getLogPrefix() + "Read " + read + (read != -1 ? " bytes" : ""));
		}
	}


	private final class RequestAsyncListener implements AsyncListener {

		@Override
		public void onStartAsync(AsyncEvent event) {
		}

		@Override
		public void onTimeout(AsyncEvent event) {
			Throwable ex = event.getThrowable();
			ex = ex != null ? ex : new IllegalStateException("Async operation timeout.");
			bodyPublisher.onError(ex);
		}

		@Override
		public void onError(AsyncEvent event) {
			bodyPublisher.onError(event.getThrowable());
		}

		@Override
		public void onComplete(AsyncEvent event) {
			bodyPublisher.onAllDataRead();
		}
	}


	private class RequestBodyPublisher extends AbstractListenerReadPublisher<DataBuffer> {

		private final ServletInputStream inputStream;

		public RequestBodyPublisher(ServletInputStream inputStream) {
			super(ServletServerHttpRequest.this.getLogPrefix());
			this.inputStream = inputStream;
		}

		public void registerReadListener() throws IOException {
			this.inputStream.setReadListener(new RequestBodyPublisherReadListener());
		}

		@Override
		protected void checkOnDataAvailable() {
			if (this.inputStream.isReady() && !this.inputStream.isFinished()) {
				onDataAvailable();
			}
		}

		@Override
		@Nullable
		protected DataBuffer read() throws IOException {
			if (this.inputStream.isReady()) {
				DataBuffer dataBuffer = readFromInputStream();
				if (dataBuffer == EOF_BUFFER) {
					// No need to wait for container callback...
					onAllDataRead();
					dataBuffer = null;
				}
				return dataBuffer;
			}
			return null;
		}

		@Override
		protected void readingPaused() {
			// no-op
		}

		@Override
		protected void discardData() {
			// Nothing to discard since we pass data buffers on immediately..
		}


		private class RequestBodyPublisherReadListener implements ReadListener {

			@Override
			public void onDataAvailable() throws IOException {
				RequestBodyPublisher.this.onDataAvailable();
			}

			@Override
			public void onAllDataRead() throws IOException {
				RequestBodyPublisher.this.onAllDataRead();
			}

			@Override
			public void onError(Throwable throwable) {
				RequestBodyPublisher.this.onError(throwable);

			}
		}
	}

}
