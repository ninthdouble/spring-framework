/*
 * Copyright 2002-2018 the original author or authors.
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

package org.springframework.jca.cci;

import org.springframework.dao.InvalidDataAccessResourceUsageException;

import javax.resource.ResourceException;

/**
 * Exception thrown when the creating of a CCI Record failed because
 * the connector doesn't support the desired CCI Record type.
 *
 * @author Juergen Hoeller
 * @since 1.2
 * @deprecated as of 5.3, in favor of specific data access APIs
 * (or native CCI usage if there is no alternative)
 */
@Deprecated
@SuppressWarnings("serial")
public class RecordTypeNotSupportedException extends InvalidDataAccessResourceUsageException {

	/**
	 * Constructor for RecordTypeNotSupportedException.
	 *
	 * @param msg message
	 * @param ex  the root ResourceException cause
	 */
	public RecordTypeNotSupportedException(String msg, ResourceException ex) {
		super(msg, ex);
	}

}
