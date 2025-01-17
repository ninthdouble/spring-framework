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

package org.springframework.r2dbc.core;

import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import io.r2dbc.spi.Statement;
import org.springframework.r2dbc.core.binding.BindMarkersFactory;
import org.springframework.util.Assert;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A non-blocking, reactive client for performing database calls requests with
 * Reactive Streams back pressure. Provides a higher level, common API over
 * R2DBC client libraries.
 *
 * <p>Use one of the static factory methods {@link #create(ConnectionFactory)}
 * or obtain a {@link DatabaseClient#builder()} to create an instance.
 * <p>
 * Usage example:
 * <pre class="code">
 * ConnectionFactory factory = …
 *
 * DatabaseClient client = DatabaseClient.create(factory);
 * Mono&gtActor;lt actor = client.sql("select first_name, last_name from t_actor")
 *     .map(row -> new Actor(row.get("first_name, String.class"),
 *     row.get("last_name, String.class")))
 *     .first();
 * </pre>
 *
 * @author Mark Paluch
 * @since 5.3
 */
public interface DatabaseClient extends ConnectionAccessor {

	/**
	 * Create a {@code DatabaseClient} that will use the provided {@link ConnectionFactory}.
	 *
	 * @param factory the {@code ConnectionFactory} to use for obtaining connections
	 * @return a new {@code DatabaseClient}. Guaranteed to be not {@code null}.
	 */
	static DatabaseClient create(ConnectionFactory factory) {
		return new DefaultDatabaseClientBuilder().connectionFactory(factory).build();
	}

	/**
	 * Obtain a {@code DatabaseClient} builder.
	 */
	static DatabaseClient.Builder builder() {
		return new DefaultDatabaseClientBuilder();
	}

	/**
	 * Return the {@link ConnectionFactory} that this client uses.
	 *
	 * @return the connection factory
	 */
	ConnectionFactory getConnectionFactory();


	// Static factory methods

	/**
	 * Specify a static {@code sql} statement to run. Contract for specifying a
	 * SQL call along with options leading to the execution. The SQL string can
	 * contain either native parameter bind markers or named parameters (e.g.
	 * {@literal :foo, :bar}) when {@link NamedParameterExpander} is enabled.
	 *
	 * @param sql the SQL statement
	 * @return a new {@link GenericExecuteSpec}
	 * @see NamedParameterExpander
	 * @see DatabaseClient.Builder#namedParameters(boolean)
	 */
	GenericExecuteSpec sql(String sql);

	/**
	 * Specify a {@link Supplier SQL supplier} that provides SQL to run.
	 * Contract for specifying an SQL call along with options leading to
	 * the execution. The SQL string can contain either native parameter
	 * bind markers or named parameters (e.g. {@literal :foo, :bar}) when
	 * {@link NamedParameterExpander} is enabled.
	 * <p>Accepts {@link PreparedOperation} as SQL and binding {@link Supplier}
	 *
	 * @param sqlSupplier a supplier for the SQL statement
	 * @return a new {@link GenericExecuteSpec}
	 * @see NamedParameterExpander
	 * @see DatabaseClient.Builder#namedParameters(boolean)
	 * @see PreparedOperation
	 */
	GenericExecuteSpec sql(Supplier<String> sqlSupplier);


	/**
	 * A mutable builder for creating a {@link DatabaseClient}.
	 */
	interface Builder {

		/**
		 * Configure the {@link BindMarkersFactory BindMarkers} to be used.
		 */
		Builder bindMarkers(BindMarkersFactory bindMarkers);

		/**
		 * Configure the {@link ConnectionFactory R2DBC connector}.
		 */
		Builder connectionFactory(ConnectionFactory factory);

		/**
		 * Configure a {@link ExecuteFunction} to execute {@link Statement} objects.
		 *
		 * @see Statement#execute()
		 */
		Builder executeFunction(ExecuteFunction executeFunction);

		/**
		 * Configure whether to use named parameter expansion.
		 * Defaults to {@code true}.
		 *
		 * @param enabled {@code true} to use named parameter expansion;
		 *                {@code false} to disable named parameter expansion
		 * @see NamedParameterExpander
		 */
		Builder namedParameters(boolean enabled);

		/**
		 * Configures a {@link Consumer} to configure this builder.
		 */
		Builder apply(Consumer<Builder> builderConsumer);

		/**
		 * Builder the {@link DatabaseClient} instance.
		 */
		DatabaseClient build();
	}


	/**
	 * Contract for specifying an SQL call along with options leading to the execution.
	 */
	interface GenericExecuteSpec {

		/**
		 * Bind a non-{@code null} value to a parameter identified by its
		 * {@code index}. {@code value} can be either a scalar value or {@link Parameter}.
		 *
		 * @param index zero based index to bind the parameter to
		 * @param value either a scalar value or {@link Parameter}
		 */
		GenericExecuteSpec bind(int index, Object value);

		/**
		 * Bind a {@code null} value to a parameter identified by its {@code index}.
		 *
		 * @param index zero based index to bind the parameter to
		 * @param type  the parameter type
		 */
		GenericExecuteSpec bindNull(int index, Class<?> type);

		/**
		 * Bind a non-{@code null} value to a parameter identified by its {@code name}.
		 *
		 * @param name  the name of the parameter
		 * @param value the value to bind
		 */
		GenericExecuteSpec bind(String name, Object value);

		/**
		 * Bind a {@code null} value to a parameter identified by its {@code name}.
		 *
		 * @param name the name of the parameter
		 * @param type the parameter type
		 */
		GenericExecuteSpec bindNull(String name, Class<?> type);

		/**
		 * Add the given filter to the end of the filter chain.
		 * <p>Filter functions are typically used to invoke methods on the Statement
		 * before it is executed. For example:
		 * <pre class="code">
		 * DatabaseClient client = …;
		 * client.sql("SELECT book_id FROM book").filter(statement -> statement.fetchSize(100))
		 * </pre>
		 *
		 * @param filterFunction the filter to be added to the chain
		 */
		default GenericExecuteSpec filter(Function<? super Statement, ? extends Statement> filterFunction) {
			Assert.notNull(filterFunction, "Filter function must not be null");
			return filter((statement, next) -> next.execute(filterFunction.apply(statement)));
		}

		/**
		 * Add the given filter to the end of the filter chain.
		 * <p>Filter functions are typically used to invoke methods on the Statement
		 * before it is executed. For example:
		 * <pre class="code">
		 * DatabaseClient client = …;
		 * client.sql("SELECT book_id FROM book").filter((statement, next) -> next.execute(statement.fetchSize(100)))
		 * </pre>
		 *
		 * @param filter the filter to be added to the chain
		 */
		GenericExecuteSpec filter(StatementFilterFunction filter);

		/**
		 * Configure a result mapping {@link Function function} and enter the execution stage.
		 *
		 * @param mappingFunction a function that maps from {@link Row} to the result type
		 * @param <R>             the result type
		 * @return a {@link FetchSpec} for configuration what to fetch
		 */
		default <R> RowsFetchSpec<R> map(Function<Row, R> mappingFunction) {
			Assert.notNull(mappingFunction, "Mapping function must not be null");
			return map((row, rowMetadata) -> mappingFunction.apply(row));
		}

		/**
		 * Configure a result mapping {@link BiFunction function} and enter the execution stage.
		 *
		 * @param mappingFunction a function that maps from {@link Row} and {@link RowMetadata}
		 *                        to the result type
		 * @param <R>             the result type
		 * @return a {@link FetchSpec} for configuration what to fetch
		 */
		<R> RowsFetchSpec<R> map(BiFunction<Row, RowMetadata, R> mappingFunction);

		/**
		 * Perform the SQL call and retrieve the result by entering the execution stage.
		 */
		FetchSpec<Map<String, Object>> fetch();

		/**
		 * Perform the SQL call and return a {@link Mono} that completes without result
		 * on statement completion.
		 *
		 * @return a {@link Mono} ignoring its payload (actively dropping)
		 */
		Mono<Void> then();
	}

}
