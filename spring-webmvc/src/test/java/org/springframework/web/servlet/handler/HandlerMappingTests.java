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

package org.springframework.web.servlet.handler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.Arguments;
import org.springframework.web.context.support.StaticWebApplicationContext;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.testfixture.servlet.MockHttpServletRequest;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link org.springframework.web.servlet.HandlerMapping}.
 *
 * @author Brian Clozel
 * @author Rossen Stoyanchev
 */
class HandlerMappingTests {

	@SuppressWarnings("unused")
	private static Stream<Arguments> pathPatternsArguments() {
		List<Function<String, MockHttpServletRequest>> factories =
				PathPatternsTestUtils.requestArguments().collect(Collectors.toList());
		return Stream.of(
				Arguments.arguments(new TestHandlerMapping(), factories.get(0)),
				Arguments.arguments(new TestHandlerMapping(), factories.get(1))
		);
	}


	@PathPatternsParameterizedTest
	void orderedInterceptors(
			TestHandlerMapping mapping, Function<String, MockHttpServletRequest> requestFactory)
			throws Exception {

		MappedInterceptor i1 = new MappedInterceptor(new String[]{"/**"}, mock(HandlerInterceptor.class));
		HandlerInterceptor i2 = mock(HandlerInterceptor.class);
		MappedInterceptor i3 = new MappedInterceptor(new String[]{"/**"}, mock(HandlerInterceptor.class));
		HandlerInterceptor i4 = mock(HandlerInterceptor.class);

		mapping.setInterceptors(i1, i2, i3, i4);
		mapping.setApplicationContext(new StaticWebApplicationContext());

		HandlerExecutionChain chain = mapping.getHandler(requestFactory.apply("/"));

		assertThat(chain).isNotNull();
		assertThat(chain.getInterceptorList()).contains(i1.getInterceptor(), i2, i3.getInterceptor(), i4);
	}

	@Test
		// gh-26546
	void abstractHandlerMappingEnsuresCachedLookupPath() throws Exception {
		MappedInterceptor interceptor = new MappedInterceptor(new String[]{"/**"}, mock(HandlerInterceptor.class));
		TestHandlerMapping mapping = new TestHandlerMapping();
		mapping.setInterceptors(interceptor);
		mapping.setApplicationContext(new StaticWebApplicationContext());

		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
		HandlerExecutionChain chain = mapping.getHandler(request);

		assertThat(chain).isNotNull();
		assertThat(chain.getInterceptorList()).contains(interceptor.getInterceptor());
	}


	private static class TestHandlerMapping extends AbstractHandlerMapping {

		@Override
		protected Object getHandlerInternal(HttpServletRequest request) {
			return new Object();
		}
	}

}
