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

package org.springframework.test.context.support;

import org.springframework.beans.factory.support.BeanDefinitionReader;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.test.context.MergedContextConfiguration;
import org.springframework.util.ObjectUtils;

import java.util.Properties;

/**
 * Concrete implementation of {@link AbstractGenericContextLoader} that reads
 * bean definitions from Java {@link Properties} resources.
 *
 * @author Sam Brannen
 * @since 2.5
 * @deprecated as of 5.3, in favor of Spring's common bean definition formats
 * and/or custom loader implementations
 */
@Deprecated
public class GenericPropertiesContextLoader extends AbstractGenericContextLoader {

	/**
	 * Creates a new {@link org.springframework.beans.factory.support.PropertiesBeanDefinitionReader}.
	 *
	 * @return a new PropertiesBeanDefinitionReader
	 * @see org.springframework.beans.factory.support.PropertiesBeanDefinitionReader
	 */
	@Override
	protected BeanDefinitionReader createBeanDefinitionReader(final GenericApplicationContext context) {
		return new org.springframework.beans.factory.support.PropertiesBeanDefinitionReader(context);
	}

	/**
	 * Returns &quot;{@code -context.properties}&quot;.
	 */
	@Override
	protected String getResourceSuffix() {
		return "-context.properties";
	}

	/**
	 * Ensure that the supplied {@link MergedContextConfiguration} does not
	 * contain {@link MergedContextConfiguration#getClasses() classes}.
	 *
	 * @see AbstractGenericContextLoader#validateMergedContextConfiguration
	 * @since 4.0.4
	 */
	@Override
	protected void validateMergedContextConfiguration(MergedContextConfiguration mergedConfig) {
		if (mergedConfig.hasClasses()) {
			String msg = String.format(
					"Test class [%s] has been configured with @ContextConfiguration's 'classes' attribute %s, "
							+ "but %s does not support annotated classes.", mergedConfig.getTestClass().getName(),
					ObjectUtils.nullSafeToString(mergedConfig.getClasses()), getClass().getSimpleName());
			logger.error(msg);
			throw new IllegalStateException(msg);
		}
	}

}
