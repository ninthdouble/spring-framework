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

package org.springframework.test.context.event;

import java.lang.annotation.*;

/**
 * {@code @RecordApplicationEvents} is a class-level annotation that is used to
 * instruct the <em>Spring TestContext Framework</em> to record all
 * {@linkplain org.springframework.context.ApplicationEvent application events}
 * that are published in the {@link org.springframework.context.ApplicationContext
 * ApplicationContext} during the execution of a single test.
 *
 * <p>The recorded events can be accessed via the {@link ApplicationEvents} API
 * within your tests.
 *
 * <p>This annotation may be used as a <em>meta-annotation</em> to create custom
 * <em>composed annotations</em>.
 *
 * @author Sam Brannen
 * @see ApplicationEvents
 * @see ApplicationEventsTestExecutionListener
 * @since 5.3.3
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface RecordApplicationEvents {
}
