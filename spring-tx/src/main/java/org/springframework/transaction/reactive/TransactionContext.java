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

package org.springframework.transaction.reactive;

import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;
import org.springframework.util.function.SingletonSupplier;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Mutable transaction context that encapsulates transactional synchronizations and
 * resources in the scope of a single transaction. Transaction context is typically
 * held by an outer {@link TransactionContextHolder} or referenced directly within
 * from the subscriber context.
 *
 * @author Mark Paluch
 * @author Juergen Hoeller
 * @see TransactionContextManager
 * @see reactor.util.context.Context
 * @since 5.2
 */
public class TransactionContext {

	private final @Nullable
	TransactionContext parent;

	private final SingletonSupplier<UUID> contextId = SingletonSupplier.of(UUID::randomUUID);

	private final Map<Object, Object> resources = new LinkedHashMap<>();

	@Nullable
	private Set<TransactionSynchronization> synchronizations;

	private volatile @Nullable
	String currentTransactionName;

	private volatile boolean currentTransactionReadOnly;

	private volatile @Nullable
	Integer currentTransactionIsolationLevel;

	private volatile boolean actualTransactionActive;


	TransactionContext() {
		this(null);
	}

	TransactionContext(@Nullable TransactionContext parent) {
		this.parent = parent;
	}


	@Nullable
	public TransactionContext getParent() {
		return this.parent;
	}

	@Deprecated
	public String getName() {
		String name = getCurrentTransactionName();
		if (StringUtils.hasText(name)) {
			return getContextId() + ": " + name;
		}
		return getContextId().toString();
	}

	@Deprecated
	public UUID getContextId() {
		return this.contextId.obtain();
	}

	public Map<Object, Object> getResources() {
		return this.resources;
	}

	@Nullable
	public Set<TransactionSynchronization> getSynchronizations() {
		return this.synchronizations;
	}

	public void setSynchronizations(@Nullable Set<TransactionSynchronization> synchronizations) {
		this.synchronizations = synchronizations;
	}

	@Nullable
	public String getCurrentTransactionName() {
		return this.currentTransactionName;
	}

	public void setCurrentTransactionName(@Nullable String currentTransactionName) {
		this.currentTransactionName = currentTransactionName;
	}

	public boolean isCurrentTransactionReadOnly() {
		return this.currentTransactionReadOnly;
	}

	public void setCurrentTransactionReadOnly(boolean currentTransactionReadOnly) {
		this.currentTransactionReadOnly = currentTransactionReadOnly;
	}

	@Nullable
	public Integer getCurrentTransactionIsolationLevel() {
		return this.currentTransactionIsolationLevel;
	}

	public void setCurrentTransactionIsolationLevel(@Nullable Integer currentTransactionIsolationLevel) {
		this.currentTransactionIsolationLevel = currentTransactionIsolationLevel;
	}

	public boolean isActualTransactionActive() {
		return this.actualTransactionActive;
	}

	public void setActualTransactionActive(boolean actualTransactionActive) {
		this.actualTransactionActive = actualTransactionActive;
	}

	public void clear() {
		this.synchronizations = null;
		this.currentTransactionName = null;
		this.currentTransactionReadOnly = false;
		this.currentTransactionIsolationLevel = null;
		this.actualTransactionActive = false;
	}

}
