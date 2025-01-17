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

package org.springframework.web.reactive.function.client;

import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.http.client.reactive.HttpComponentsClientHttpConnector;
import org.springframework.http.client.reactive.JettyClientHttpConnector;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.ClientCodecConfigurer;
import org.springframework.lang.Nullable;
import org.springframework.util.*;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.UriBuilderFactory;

import java.util.*;
import java.util.function.Consumer;

/**
 * Default implementation of {@link WebClient.Builder}.
 *
 * @author Rossen Stoyanchev
 * @author Brian Clozel
 * @since 5.0
 */
final class DefaultWebClientBuilder implements WebClient.Builder {

	private static final boolean reactorClientPresent;

	private static final boolean jettyClientPresent;

	private static final boolean httpComponentsClientPresent;

	static {
		ClassLoader loader = DefaultWebClientBuilder.class.getClassLoader();
		reactorClientPresent = ClassUtils.isPresent("reactor.netty.http.client.HttpClient", loader);
		jettyClientPresent = ClassUtils.isPresent("org.eclipse.jetty.client.HttpClient", loader);
		httpComponentsClientPresent =
				ClassUtils.isPresent("org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient", loader) &&
						ClassUtils.isPresent("org.apache.hc.core5.reactive.ReactiveDataConsumer", loader);
	}


	@Nullable
	private String baseUrl;

	@Nullable
	private Map<String, ?> defaultUriVariables;

	@Nullable
	private UriBuilderFactory uriBuilderFactory;

	@Nullable
	private HttpHeaders defaultHeaders;

	@Nullable
	private MultiValueMap<String, String> defaultCookies;

	@Nullable
	private Consumer<WebClient.RequestHeadersSpec<?>> defaultRequest;

	@Nullable
	private List<ExchangeFilterFunction> filters;

	@Nullable
	private ClientHttpConnector connector;

	@Nullable
	private ExchangeStrategies strategies;

	@Nullable
	private List<Consumer<ExchangeStrategies.Builder>> strategiesConfigurers;

	@Nullable
	private ExchangeFunction exchangeFunction;


	public DefaultWebClientBuilder() {
	}

	public DefaultWebClientBuilder(DefaultWebClientBuilder other) {
		Assert.notNull(other, "DefaultWebClientBuilder must not be null");

		this.baseUrl = other.baseUrl;
		this.defaultUriVariables = (other.defaultUriVariables != null ?
				new LinkedHashMap<>(other.defaultUriVariables) : null);
		this.uriBuilderFactory = other.uriBuilderFactory;

		if (other.defaultHeaders != null) {
			this.defaultHeaders = new HttpHeaders();
			this.defaultHeaders.putAll(other.defaultHeaders);
		} else {
			this.defaultHeaders = null;
		}

		this.defaultCookies = (other.defaultCookies != null ?
				new LinkedMultiValueMap<>(other.defaultCookies) : null);
		this.defaultRequest = other.defaultRequest;
		this.filters = (other.filters != null ? new ArrayList<>(other.filters) : null);

		this.connector = other.connector;
		this.strategies = other.strategies;
		this.strategiesConfigurers = (other.strategiesConfigurers != null ?
				new ArrayList<>(other.strategiesConfigurers) : null);
		this.exchangeFunction = other.exchangeFunction;
	}


	@Override
	public WebClient.Builder baseUrl(String baseUrl) {
		this.baseUrl = baseUrl;
		return this;
	}

	@Override
	public WebClient.Builder defaultUriVariables(Map<String, ?> defaultUriVariables) {
		this.defaultUriVariables = defaultUriVariables;
		return this;
	}

	@Override
	public WebClient.Builder uriBuilderFactory(UriBuilderFactory uriBuilderFactory) {
		this.uriBuilderFactory = uriBuilderFactory;
		return this;
	}

	@Override
	public WebClient.Builder defaultHeader(String header, String... values) {
		initHeaders().put(header, Arrays.asList(values));
		return this;
	}

	@Override
	public WebClient.Builder defaultHeaders(Consumer<HttpHeaders> headersConsumer) {
		headersConsumer.accept(initHeaders());
		return this;
	}

	private HttpHeaders initHeaders() {
		if (this.defaultHeaders == null) {
			this.defaultHeaders = new HttpHeaders();
		}
		return this.defaultHeaders;
	}

	@Override
	public WebClient.Builder defaultCookie(String cookie, String... values) {
		initCookies().addAll(cookie, Arrays.asList(values));
		return this;
	}

	@Override
	public WebClient.Builder defaultCookies(Consumer<MultiValueMap<String, String>> cookiesConsumer) {
		cookiesConsumer.accept(initCookies());
		return this;
	}

	private MultiValueMap<String, String> initCookies() {
		if (this.defaultCookies == null) {
			this.defaultCookies = new LinkedMultiValueMap<>(3);
		}
		return this.defaultCookies;
	}

	@Override
	public WebClient.Builder defaultRequest(Consumer<WebClient.RequestHeadersSpec<?>> defaultRequest) {
		this.defaultRequest = this.defaultRequest != null ?
				this.defaultRequest.andThen(defaultRequest) : defaultRequest;
		return this;
	}

	@Override
	public WebClient.Builder filter(ExchangeFilterFunction filter) {
		Assert.notNull(filter, "ExchangeFilterFunction must not be null");
		initFilters().add(filter);
		return this;
	}

	@Override
	public WebClient.Builder filters(Consumer<List<ExchangeFilterFunction>> filtersConsumer) {
		filtersConsumer.accept(initFilters());
		return this;
	}

	private List<ExchangeFilterFunction> initFilters() {
		if (this.filters == null) {
			this.filters = new ArrayList<>();
		}
		return this.filters;
	}

	@Override
	public WebClient.Builder clientConnector(ClientHttpConnector connector) {
		this.connector = connector;
		return this;
	}

	@Override
	public WebClient.Builder codecs(Consumer<ClientCodecConfigurer> configurer) {
		if (this.strategiesConfigurers == null) {
			this.strategiesConfigurers = new ArrayList<>(4);
		}
		this.strategiesConfigurers.add(builder -> builder.codecs(configurer));
		return this;
	}

	@Override
	public WebClient.Builder exchangeStrategies(ExchangeStrategies strategies) {
		this.strategies = strategies;
		return this;
	}

	@Override
	@Deprecated
	public WebClient.Builder exchangeStrategies(Consumer<ExchangeStrategies.Builder> configurer) {
		if (this.strategiesConfigurers == null) {
			this.strategiesConfigurers = new ArrayList<>(4);
		}
		this.strategiesConfigurers.add(configurer);
		return this;
	}

	@Override
	public WebClient.Builder exchangeFunction(ExchangeFunction exchangeFunction) {
		this.exchangeFunction = exchangeFunction;
		return this;
	}

	@Override
	public WebClient.Builder apply(Consumer<WebClient.Builder> builderConsumer) {
		builderConsumer.accept(this);
		return this;
	}

	@Override
	public WebClient.Builder clone() {
		return new DefaultWebClientBuilder(this);
	}

	@Override
	public WebClient build() {
		ClientHttpConnector connectorToUse =
				(this.connector != null ? this.connector : initConnector());

		ExchangeFunction exchange = (this.exchangeFunction == null ?
				ExchangeFunctions.create(connectorToUse, initExchangeStrategies()) :
				this.exchangeFunction);

		ExchangeFunction filteredExchange = (this.filters != null ? this.filters.stream()
				.reduce(ExchangeFilterFunction::andThen)
				.map(filter -> filter.apply(exchange))
				.orElse(exchange) : exchange);

		HttpHeaders defaultHeaders = copyDefaultHeaders();

		MultiValueMap<String, String> defaultCookies = copyDefaultCookies();

		return new DefaultWebClient(filteredExchange, initUriBuilderFactory(),
				defaultHeaders,
				defaultCookies,
				this.defaultRequest, new DefaultWebClientBuilder(this));
	}

	private ClientHttpConnector initConnector() {
		if (reactorClientPresent) {
			return new ReactorClientHttpConnector();
		} else if (jettyClientPresent) {
			return new JettyClientHttpConnector();
		} else if (httpComponentsClientPresent) {
			return new HttpComponentsClientHttpConnector();
		}
		throw new IllegalStateException("No suitable default ClientHttpConnector found");
	}

	private ExchangeStrategies initExchangeStrategies() {
		if (CollectionUtils.isEmpty(this.strategiesConfigurers)) {
			return (this.strategies != null ? this.strategies : ExchangeStrategies.withDefaults());
		}
		ExchangeStrategies.Builder builder =
				(this.strategies != null ? this.strategies.mutate() : ExchangeStrategies.builder());
		this.strategiesConfigurers.forEach(configurer -> configurer.accept(builder));
		return builder.build();
	}

	private UriBuilderFactory initUriBuilderFactory() {
		if (this.uriBuilderFactory != null) {
			return this.uriBuilderFactory;
		}
		DefaultUriBuilderFactory factory = (this.baseUrl != null ?
				new DefaultUriBuilderFactory(this.baseUrl) : new DefaultUriBuilderFactory());
		factory.setDefaultUriVariables(this.defaultUriVariables);
		return factory;
	}

	@Nullable
	private HttpHeaders copyDefaultHeaders() {
		if (this.defaultHeaders != null) {
			HttpHeaders copy = new HttpHeaders();
			this.defaultHeaders.forEach((key, values) -> copy.put(key, new ArrayList<>(values)));
			return HttpHeaders.readOnlyHttpHeaders(copy);
		} else {
			return null;
		}
	}

	@Nullable
	private MultiValueMap<String, String> copyDefaultCookies() {
		if (this.defaultCookies != null) {
			MultiValueMap<String, String> copy = new LinkedMultiValueMap<>(this.defaultCookies.size());
			this.defaultCookies.forEach((key, values) -> copy.put(key, new ArrayList<>(values)));
			return CollectionUtils.unmodifiableMultiValueMap(copy);
		} else {
			return null;
		}
	}

}
