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

package org.springframework.jdbc.core;

import org.springframework.beans.BeanWrapper;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.jdbc.core.test.*;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.support.SQLStateSQLExceptionTranslator;

import java.math.BigDecimal;
import java.sql.*;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Mock object based abstract class for RowMapper tests.
 * Initializes mock objects and verifies results.
 *
 * @author Thomas Risberg
 */
public abstract class AbstractRowMapperTests {

	protected void verifyPerson(Person person) {
		assertThat(person.getName()).isEqualTo("Bubba");
		assertThat(person.getAge()).isEqualTo(22L);
		assertThat(person.getBirth_date()).usingComparator(Date::compareTo).isEqualTo(new java.util.Date(1221222L));
		assertThat(person.getBalance()).isEqualTo(new BigDecimal("1234.56"));
		verifyPersonViaBeanWrapper(person);
	}

	protected void verifyPerson(ConcretePerson person) {
		assertThat(person.getName()).isEqualTo("Bubba");
		assertThat(person.getAge()).isEqualTo(22L);
		assertThat(person.getBirth_date()).usingComparator(Date::compareTo).isEqualTo(new java.util.Date(1221222L));
		assertThat(person.getBalance()).isEqualTo(new BigDecimal("1234.56"));
		verifyPersonViaBeanWrapper(person);
	}

	protected void verifyPerson(SpacePerson person) {
		assertThat(person.getLastName()).isEqualTo("Bubba");
		assertThat(person.getAge()).isEqualTo(22L);
		assertThat(person.getBirthDate()).isEqualTo(new Timestamp(1221222L).toLocalDateTime());
		assertThat(person.getBalance()).isEqualTo(new BigDecimal("1234.56"));
	}

	protected void verifyPerson(DatePerson person) {
		assertThat(person.getLastName()).isEqualTo("Bubba");
		assertThat(person.getAge()).isEqualTo(22L);
		assertThat(person.getBirthDate()).isEqualTo(new java.sql.Date(1221222L).toLocalDate());
		assertThat(person.getBalance()).isEqualTo(new BigDecimal("1234.56"));
	}

	protected void verifyPerson(ConstructorPerson person) {
		assertThat(person.name()).isEqualTo("Bubba");
		assertThat(person.age()).isEqualTo(22L);
		assertThat(person.birth_date()).usingComparator(Date::compareTo).isEqualTo(new java.util.Date(1221222L));
		assertThat(person.balance()).isEqualTo(new BigDecimal("1234.56"));
		verifyPersonViaBeanWrapper(person);
	}

	private void verifyPersonViaBeanWrapper(Object person) {
		BeanWrapper bw = PropertyAccessorFactory.forBeanPropertyAccess(person);
		assertThat(bw.getPropertyValue("name")).isEqualTo("Bubba");
		assertThat(bw.getPropertyValue("age")).isEqualTo(22L);
		assertThat((Date) bw.getPropertyValue("birth_date")).usingComparator(Date::compareTo).isEqualTo(new java.util.Date(1221222L));
		assertThat(bw.getPropertyValue("balance")).isEqualTo(new BigDecimal("1234.56"));
	}


	protected enum MockType {ONE, TWO, THREE}

	;


	protected static class Mock {

		private Connection connection;

		private ResultSetMetaData resultSetMetaData;

		private ResultSet resultSet;

		private Statement statement;

		private JdbcTemplate jdbcTemplate;

		public Mock() throws Exception {
			this(MockType.ONE);
		}

		@SuppressWarnings("unchecked")
		public Mock(MockType type) throws Exception {
			connection = mock(Connection.class);
			statement = mock(Statement.class);
			resultSet = mock(ResultSet.class);
			resultSetMetaData = mock(ResultSetMetaData.class);

			given(connection.createStatement()).willReturn(statement);
			given(statement.executeQuery(anyString())).willReturn(resultSet);
			given(resultSet.getMetaData()).willReturn(resultSetMetaData);

			given(resultSet.next()).willReturn(true, false);
			given(resultSet.getString(1)).willReturn("Bubba");
			given(resultSet.getLong(2)).willReturn(22L);
			given(resultSet.getTimestamp(3)).willReturn(new Timestamp(1221222L));
			given(resultSet.getObject(anyInt(), any(Class.class))).willThrow(new SQLFeatureNotSupportedException());
			given(resultSet.getDate(3)).willReturn(new java.sql.Date(1221222L));
			given(resultSet.getBigDecimal(4)).willReturn(new BigDecimal("1234.56"));
			given(resultSet.getObject(4)).willReturn(new BigDecimal("1234.56"));
			given(resultSet.wasNull()).willReturn(type == MockType.TWO);

			given(resultSetMetaData.getColumnCount()).willReturn(4);
			given(resultSetMetaData.getColumnLabel(1)).willReturn(
					type == MockType.THREE ? "Last Name" : "name");
			given(resultSetMetaData.getColumnLabel(2)).willReturn("age");
			given(resultSetMetaData.getColumnLabel(3)).willReturn("birth_date");
			given(resultSetMetaData.getColumnLabel(4)).willReturn("balance");

			given(resultSet.findColumn("name")).willReturn(1);
			given(resultSet.findColumn("age")).willReturn(2);
			given(resultSet.findColumn("birth_date")).willReturn(3);
			given(resultSet.findColumn("balance")).willReturn(4);

			jdbcTemplate = new JdbcTemplate();
			jdbcTemplate.setDataSource(new SingleConnectionDataSource(connection, false));
			jdbcTemplate.setExceptionTranslator(new SQLStateSQLExceptionTranslator());
			jdbcTemplate.afterPropertiesSet();
		}

		public JdbcTemplate getJdbcTemplate() {
			return jdbcTemplate;
		}

		public void verifyClosed() throws Exception {
			verify(resultSet).close();
			verify(statement).close();
		}
	}

}
