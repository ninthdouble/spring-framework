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

package org.springframework.context.annotation;

import org.springframework.beans.factory.parsing.Problem;
import org.springframework.beans.factory.parsing.ProblemReporter;
import org.springframework.core.type.MethodMetadata;
import org.springframework.lang.Nullable;

/**
 * Represents a {@link Configuration @Configuration} class method annotated with
 * {@link Bean @Bean}.
 *
 * @author Chris Beams
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @see ConfigurationClass
 * @see ConfigurationClassParser
 * @see ConfigurationClassBeanDefinitionReader
 * @since 3.0
 */
final class BeanMethod extends ConfigurationMethod {

	BeanMethod(MethodMetadata metadata, ConfigurationClass configurationClass) {
		super(metadata, configurationClass);
	}

	@Override
	public void validate(ProblemReporter problemReporter) {
		if (getMetadata().isStatic()) {
			// static @Bean methods have no constraints to validate -> return immediately
			return;
		}

		if (this.configurationClass.getMetadata().isAnnotated(Configuration.class.getName())) {
			if (!getMetadata().isOverridable()) {
				// instance @Bean methods within @Configuration classes must be overridable to accommodate CGLIB
				problemReporter.error(new NonOverridableMethodError());
			}
		}
	}

	@Override
	public boolean equals(@Nullable Object obj) {
		return ((this == obj) || ((obj instanceof BeanMethod) &&
				this.metadata.equals(((BeanMethod) obj).metadata)));
	}

	@Override
	public int hashCode() {
		return this.metadata.hashCode();
	}

	@Override
	public String toString() {
		return "BeanMethod: " + this.metadata;
	}

	private class NonOverridableMethodError extends Problem {

		NonOverridableMethodError() {
			super(String.format("@Bean method '%s' must not be private or final; change the method's modifiers to continue",
					getMetadata().getMethodName()), getResourceLocation());
		}
	}

}
