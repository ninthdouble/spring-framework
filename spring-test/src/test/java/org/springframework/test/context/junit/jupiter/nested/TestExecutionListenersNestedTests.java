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

package org.springframework.test.context.junit.jupiter.nested;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.NestedTestConfiguration;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.junit.jupiter.nested.TestExecutionListenersNestedTests.FooTestExecutionListener;
import org.springframework.test.context.support.AbstractTestExecutionListener;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.context.NestedTestConfiguration.EnclosingConfiguration.INHERIT;
import static org.springframework.test.context.NestedTestConfiguration.EnclosingConfiguration.OVERRIDE;

/**
 * Integration tests that verify support for {@code @Nested} test classes using
 * {@link TestExecutionListeners @TestExecutionListeners} in conjunction with the
 * {@link SpringExtension} in a JUnit Jupiter environment.
 *
 * @author Sam Brannen
 * @since 5.3
 */
@SpringJUnitConfig
@TestExecutionListeners(FooTestExecutionListener.class)
@NestedTestConfiguration(OVERRIDE) // since INHERIT is now the global default
class TestExecutionListenersNestedTests {

	private static final String FOO = "foo";
	private static final String BAR = "bar";
	private static final String BAZ = "baz";
	private static final String QUX = "qux";

	private static final List<String> listeners = new ArrayList<>();


	@AfterEach
	void resetListeners() {
		listeners.clear();
	}

	@Test
	void test() {
		assertThat(listeners).containsExactly(FOO);
	}


	@TestExecutionListeners(QuxTestExecutionListener.class)
	interface TestInterface {
	}

	@Configuration
	static class Config {
		/* no user beans required for these tests */
	}

	private static abstract class BaseTestExecutionListener extends AbstractTestExecutionListener {

		protected abstract String name();

		@Override
		public final void beforeTestClass(TestContext testContext) {
			listeners.add(name());
		}
	}

	// -------------------------------------------------------------------------

	static class FooTestExecutionListener extends BaseTestExecutionListener {

		@Override
		protected String name() {
			return FOO;
		}
	}

	static class BarTestExecutionListener extends BaseTestExecutionListener {

		@Override
		protected String name() {
			return BAR;
		}
	}

	static class BazTestExecutionListener extends BaseTestExecutionListener {

		@Override
		protected String name() {
			return BAZ;
		}
	}

	static class QuxTestExecutionListener extends BaseTestExecutionListener {

		@Override
		protected String name() {
			return QUX;
		}
	}

	@Nested
	@NestedTestConfiguration(INHERIT)
	class InheritedConfigTests {

		@Test
		void test() {
			assertThat(listeners).containsExactly(FOO);
		}
	}

	@Nested
	@SpringJUnitConfig(Config.class)
	@TestExecutionListeners(BarTestExecutionListener.class)
	class ConfigOverriddenByDefaultTests {

		@Test
		void test() {
			assertThat(listeners).containsExactly(BAR);
		}
	}

	@Nested
	@NestedTestConfiguration(INHERIT)
	@SpringJUnitConfig(Config.class)
	@TestExecutionListeners(BarTestExecutionListener.class)
	class InheritedAndExtendedConfigTests {

		@Test
		void test() {
			assertThat(listeners).containsExactly(FOO, BAR);
		}


		@Nested
		@NestedTestConfiguration(OVERRIDE)
		@SpringJUnitConfig(Config.class)
		@TestExecutionListeners(BazTestExecutionListener.class)
		class DoubleNestedWithOverriddenConfigTests {

			@Test
			void test() {
				assertThat(listeners).containsExactly(BAZ);
			}


			@Nested
			@NestedTestConfiguration(INHERIT)
			@TestExecutionListeners(listeners = BarTestExecutionListener.class, inheritListeners = false)
			class TripleNestedWithInheritedConfigButOverriddenListenersTests {

				@Test
				void test() {
					assertThat(listeners).containsExactly(BAR);
				}
			}

			@Nested
			@NestedTestConfiguration(INHERIT)
			class TripleNestedWithInheritedConfigAndTestInterfaceTests implements TestInterface {

				@Test
				void test() {
					assertThat(listeners).containsExactly(BAZ, QUX);
				}
			}
		}

	}

}
