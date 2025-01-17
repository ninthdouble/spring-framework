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

package org.springframework.beans;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.propertyeditors.CustomDateEditor;
import org.springframework.beans.testfixture.beans.DerivedTestBean;
import org.springframework.beans.testfixture.beans.ITestBean;
import org.springframework.beans.testfixture.beans.TestBean;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceEditor;
import org.springframework.lang.Nullable;

import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.*;
import java.net.URI;
import java.net.URL;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link BeanUtils}.
 *
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @author Chris Beams
 * @author Sebastien Deleuze
 * @author Sam Brannen
 * @since 19.05.2003
 */
class BeanUtilsTests {

	@Test
	void instantiateClassGivenInterface() {
		assertThatExceptionOfType(FatalBeanException.class).isThrownBy(() ->
				BeanUtils.instantiateClass(List.class));
	}

	@Test
	void instantiateClassGivenClassWithoutDefaultConstructor() {
		assertThatExceptionOfType(FatalBeanException.class).isThrownBy(() ->
				BeanUtils.instantiateClass(CustomDateEditor.class));
	}

	@Test
		// gh-22531
	void instantiateClassWithOptionalNullableType() throws NoSuchMethodException {
		Constructor<BeanWithNullableTypes> ctor = BeanWithNullableTypes.class.getDeclaredConstructor(
				Integer.class, Boolean.class, String.class);
		BeanWithNullableTypes bean = BeanUtils.instantiateClass(ctor, null, null, "foo");
		assertThat(bean.getCounter()).isNull();
		assertThat(bean.isFlag()).isNull();
		assertThat(bean.getValue()).isEqualTo("foo");
	}

	@Test
		// gh-22531
	void instantiateClassWithOptionalPrimitiveType() throws NoSuchMethodException {
		Constructor<BeanWithPrimitiveTypes> ctor = BeanWithPrimitiveTypes.class.getDeclaredConstructor(int.class, boolean.class, String.class);
		BeanWithPrimitiveTypes bean = BeanUtils.instantiateClass(ctor, null, null, "foo");
		assertThat(bean.getCounter()).isEqualTo(0);
		assertThat(bean.isFlag()).isEqualTo(false);
		assertThat(bean.getValue()).isEqualTo("foo");
	}

	@Test
		// gh-22531
	void instantiateClassWithMoreArgsThanParameters() throws NoSuchMethodException {
		Constructor<BeanWithPrimitiveTypes> ctor = BeanWithPrimitiveTypes.class.getDeclaredConstructor(int.class, boolean.class, String.class);
		assertThatExceptionOfType(BeanInstantiationException.class).isThrownBy(() ->
				BeanUtils.instantiateClass(ctor, null, null, "foo", null));
	}

	@Test
	void instantiatePrivateClassWithPrivateConstructor() throws NoSuchMethodException {
		Constructor<PrivateBeanWithPrivateConstructor> ctor = PrivateBeanWithPrivateConstructor.class.getDeclaredConstructor();
		BeanUtils.instantiateClass(ctor);
	}

	@Test
	void getPropertyDescriptors() throws Exception {
		PropertyDescriptor[] actual = Introspector.getBeanInfo(TestBean.class).getPropertyDescriptors();
		PropertyDescriptor[] descriptors = BeanUtils.getPropertyDescriptors(TestBean.class);
		assertThat(descriptors).as("Descriptors should not be null").isNotNull();
		assertThat(descriptors.length).as("Invalid number of descriptors returned").isEqualTo(actual.length);
	}

	@Test
	void beanPropertyIsArray() {
		PropertyDescriptor[] descriptors = BeanUtils.getPropertyDescriptors(ContainerBean.class);
		for (PropertyDescriptor descriptor : descriptors) {
			if ("containedBeans".equals(descriptor.getName())) {
				assertThat(descriptor.getPropertyType().isArray()).as("Property should be an array").isTrue();
				assertThat(ContainedBean.class).isEqualTo(descriptor.getPropertyType().getComponentType());
			}
		}
	}

	@Test
	void findEditorByConvention() {
		assertThat(BeanUtils.findEditorByConvention(Resource.class).getClass()).isEqualTo(ResourceEditor.class);
	}

	@Test
	void copyProperties() throws Exception {
		TestBean tb = new TestBean();
		tb.setName("rod");
		tb.setAge(32);
		tb.setTouchy("touchy");
		TestBean tb2 = new TestBean();
		assertThat(tb2.getName() == null).as("Name empty").isTrue();
		assertThat(tb2.getAge() == 0).as("Age empty").isTrue();
		assertThat(tb2.getTouchy() == null).as("Touchy empty").isTrue();
		BeanUtils.copyProperties(tb, tb2);
		assertThat(tb2.getName().equals(tb.getName())).as("Name copied").isTrue();
		assertThat(tb2.getAge() == tb.getAge()).as("Age copied").isTrue();
		assertThat(tb2.getTouchy().equals(tb.getTouchy())).as("Touchy copied").isTrue();
	}

	@Test
	void copyPropertiesWithDifferentTypes1() throws Exception {
		DerivedTestBean tb = new DerivedTestBean();
		tb.setName("rod");
		tb.setAge(32);
		tb.setTouchy("touchy");
		TestBean tb2 = new TestBean();
		assertThat(tb2.getName() == null).as("Name empty").isTrue();
		assertThat(tb2.getAge() == 0).as("Age empty").isTrue();
		assertThat(tb2.getTouchy() == null).as("Touchy empty").isTrue();
		BeanUtils.copyProperties(tb, tb2);
		assertThat(tb2.getName().equals(tb.getName())).as("Name copied").isTrue();
		assertThat(tb2.getAge() == tb.getAge()).as("Age copied").isTrue();
		assertThat(tb2.getTouchy().equals(tb.getTouchy())).as("Touchy copied").isTrue();
	}

	@Test
	void copyPropertiesWithDifferentTypes2() throws Exception {
		TestBean tb = new TestBean();
		tb.setName("rod");
		tb.setAge(32);
		tb.setTouchy("touchy");
		DerivedTestBean tb2 = new DerivedTestBean();
		assertThat(tb2.getName() == null).as("Name empty").isTrue();
		assertThat(tb2.getAge() == 0).as("Age empty").isTrue();
		assertThat(tb2.getTouchy() == null).as("Touchy empty").isTrue();
		BeanUtils.copyProperties(tb, tb2);
		assertThat(tb2.getName().equals(tb.getName())).as("Name copied").isTrue();
		assertThat(tb2.getAge() == tb.getAge()).as("Age copied").isTrue();
		assertThat(tb2.getTouchy().equals(tb.getTouchy())).as("Touchy copied").isTrue();
	}

	@Test
	void copyPropertiesHonorsGenericTypeMatches() {
		IntegerListHolder1 integerListHolder1 = new IntegerListHolder1();
		integerListHolder1.getList().add(42);
		IntegerListHolder2 integerListHolder2 = new IntegerListHolder2();

		BeanUtils.copyProperties(integerListHolder1, integerListHolder2);
		assertThat(integerListHolder1.getList()).containsOnly(42);
		assertThat(integerListHolder2.getList()).containsOnly(42);
	}

	@Test
	void copyPropertiesDoesNotHonorGenericTypeMismatches() {
		IntegerListHolder1 integerListHolder = new IntegerListHolder1();
		integerListHolder.getList().add(42);
		LongListHolder longListHolder = new LongListHolder();

		BeanUtils.copyProperties(integerListHolder, longListHolder);
		assertThat(integerListHolder.getList()).containsOnly(42);
		assertThat(longListHolder.getList()).isEmpty();
	}

	@Test
		// gh-26531
	void copyPropertiesIgnoresGenericsIfSourceOrTargetHasUnresolvableGenerics() throws Exception {
		Order original = new Order("test", Arrays.asList("foo", "bar"));

		// Create a Proxy that loses the generic type information for the getLineItems() method.
		OrderSummary proxy = proxyOrder(original);
		assertThat(OrderSummary.class.getDeclaredMethod("getLineItems").toGenericString())
				.contains("java.util.List<java.lang.String>");
		assertThat(proxy.getClass().getDeclaredMethod("getLineItems").toGenericString())
				.contains("java.util.List")
				.doesNotContain("<java.lang.String>");

		// Ensure that our custom Proxy works as expected.
		assertThat(proxy.getId()).isEqualTo("test");
		assertThat(proxy.getLineItems()).containsExactly("foo", "bar");

		// Copy from proxy to target.
		Order target = new Order();
		BeanUtils.copyProperties(proxy, target);
		assertThat(target.getId()).isEqualTo("test");
		assertThat(target.getLineItems()).containsExactly("foo", "bar");
	}

	@Test
	void copyPropertiesWithEditable() throws Exception {
		TestBean tb = new TestBean();
		assertThat(tb.getName() == null).as("Name empty").isTrue();
		tb.setAge(32);
		tb.setTouchy("bla");
		TestBean tb2 = new TestBean();
		tb2.setName("rod");
		assertThat(tb2.getAge() == 0).as("Age empty").isTrue();
		assertThat(tb2.getTouchy() == null).as("Touchy empty").isTrue();

		// "touchy" should not be copied: it's not defined in ITestBean
		BeanUtils.copyProperties(tb, tb2, ITestBean.class);
		assertThat(tb2.getName() == null).as("Name copied").isTrue();
		assertThat(tb2.getAge() == 32).as("Age copied").isTrue();
		assertThat(tb2.getTouchy() == null).as("Touchy still empty").isTrue();
	}

	@Test
	void copyPropertiesWithIgnore() throws Exception {
		TestBean tb = new TestBean();
		assertThat(tb.getName() == null).as("Name empty").isTrue();
		tb.setAge(32);
		tb.setTouchy("bla");
		TestBean tb2 = new TestBean();
		tb2.setName("rod");
		assertThat(tb2.getAge() == 0).as("Age empty").isTrue();
		assertThat(tb2.getTouchy() == null).as("Touchy empty").isTrue();

		// "spouse", "touchy", "age" should not be copied
		BeanUtils.copyProperties(tb, tb2, "spouse", "touchy", "age");
		assertThat(tb2.getName() == null).as("Name copied").isTrue();
		assertThat(tb2.getAge() == 0).as("Age still empty").isTrue();
		assertThat(tb2.getTouchy() == null).as("Touchy still empty").isTrue();
	}

	@Test
	void copyPropertiesWithIgnoredNonExistingProperty() {
		NameAndSpecialProperty source = new NameAndSpecialProperty();
		source.setName("name");
		TestBean target = new TestBean();
		BeanUtils.copyProperties(source, target, "specialProperty");
		assertThat("name").isEqualTo(target.getName());
	}

	@Test
	void copyPropertiesWithInvalidProperty() {
		InvalidProperty source = new InvalidProperty();
		source.setName("name");
		source.setFlag1(true);
		source.setFlag2(true);
		InvalidProperty target = new InvalidProperty();
		BeanUtils.copyProperties(source, target);
		assertThat(target.getName()).isEqualTo("name");
		assertThat((boolean) target.getFlag1()).isTrue();
		assertThat(target.getFlag2()).isTrue();
	}

	@Test
	void resolveSimpleSignature() throws Exception {
		Method desiredMethod = MethodSignatureBean.class.getMethod("doSomething");
		assertSignatureEquals(desiredMethod, "doSomething");
		assertSignatureEquals(desiredMethod, "doSomething()");
	}

	@Test
	void resolveInvalidSignatureEndParen() {
		assertThatIllegalArgumentException().isThrownBy(() ->
				BeanUtils.resolveSignature("doSomething(", MethodSignatureBean.class));
	}

	@Test
	void resolveInvalidSignatureStartParen() {
		assertThatIllegalArgumentException().isThrownBy(() ->
				BeanUtils.resolveSignature("doSomething)", MethodSignatureBean.class));
	}

	@Test
	void resolveWithAndWithoutArgList() throws Exception {
		Method desiredMethod = MethodSignatureBean.class.getMethod("doSomethingElse", String.class, int.class);
		assertSignatureEquals(desiredMethod, "doSomethingElse");
		assertThat(BeanUtils.resolveSignature("doSomethingElse()", MethodSignatureBean.class)).isNull();
	}

	@Test
	void resolveTypedSignature() throws Exception {
		Method desiredMethod = MethodSignatureBean.class.getMethod("doSomethingElse", String.class, int.class);
		assertSignatureEquals(desiredMethod, "doSomethingElse(java.lang.String, int)");
	}

	@Test
	void resolveOverloadedSignature() throws Exception {
		// test resolve with no args
		Method desiredMethod = MethodSignatureBean.class.getMethod("overloaded");
		assertSignatureEquals(desiredMethod, "overloaded()");

		// resolve with single arg
		desiredMethod = MethodSignatureBean.class.getMethod("overloaded", String.class);
		assertSignatureEquals(desiredMethod, "overloaded(java.lang.String)");

		// resolve with two args
		desiredMethod = MethodSignatureBean.class.getMethod("overloaded", String.class, BeanFactory.class);
		assertSignatureEquals(desiredMethod, "overloaded(java.lang.String, org.springframework.beans.factory.BeanFactory)");
	}

	@Test
	void resolveSignatureWithArray() throws Exception {
		Method desiredMethod = MethodSignatureBean.class.getMethod("doSomethingWithAnArray", String[].class);
		assertSignatureEquals(desiredMethod, "doSomethingWithAnArray(java.lang.String[])");

		desiredMethod = MethodSignatureBean.class.getMethod("doSomethingWithAMultiDimensionalArray", String[][].class);
		assertSignatureEquals(desiredMethod, "doSomethingWithAMultiDimensionalArray(java.lang.String[][])");
	}

	@Test
	void spr6063() {
		PropertyDescriptor[] descrs = BeanUtils.getPropertyDescriptors(Bean.class);

		PropertyDescriptor keyDescr = BeanUtils.getPropertyDescriptor(Bean.class, "value");
		assertThat(keyDescr.getPropertyType()).isEqualTo(String.class);
		for (PropertyDescriptor propertyDescriptor : descrs) {
			if (propertyDescriptor.getName().equals(keyDescr.getName())) {
				assertThat(propertyDescriptor.getPropertyType()).as(propertyDescriptor.getName() + " has unexpected type").isEqualTo(keyDescr.getPropertyType());
			}
		}
	}

	@ParameterizedTest
	@ValueSource(classes = {
			boolean.class, char.class, byte.class, short.class, int.class, long.class, float.class, double.class,
			Boolean.class, Character.class, Byte.class, Short.class, Integer.class, Long.class, Float.class, Double.class,
			DayOfWeek.class, String.class, LocalDateTime.class, Date.class, URI.class, URL.class, Locale.class, Class.class
	})
	void isSimpleValueType(Class<?> type) {
		assertThat(BeanUtils.isSimpleValueType(type)).as("Type [" + type.getName() + "] should be a simple value type").isTrue();
	}

	@ParameterizedTest
	@ValueSource(classes = {int[].class, Object.class, List.class, void.class, Void.class})
	void isNotSimpleValueType(Class<?> type) {
		assertThat(BeanUtils.isSimpleValueType(type)).as("Type [" + type.getName() + "] should not be a simple value type").isFalse();
	}

	@ParameterizedTest
	@ValueSource(classes = {
			boolean.class, char.class, byte.class, short.class, int.class, long.class, float.class, double.class,
			Boolean.class, Character.class, Byte.class, Short.class, Integer.class, Long.class, Float.class, Double.class,
			DayOfWeek.class, String.class, LocalDateTime.class, Date.class, URI.class, URL.class, Locale.class, Class.class,
			boolean[].class, Boolean[].class, LocalDateTime[].class, Date[].class
	})
	void isSimpleProperty(Class<?> type) {
		assertThat(BeanUtils.isSimpleProperty(type)).as("Type [" + type.getName() + "] should be a simple property").isTrue();
	}

	@ParameterizedTest
	@ValueSource(classes = {Object.class, List.class, void.class, Void.class})
	void isNotSimpleProperty(Class<?> type) {
		assertThat(BeanUtils.isSimpleProperty(type)).as("Type [" + type.getName() + "] should not be a simple property").isFalse();
	}

	private void assertSignatureEquals(Method desiredMethod, String signature) {
		assertThat(BeanUtils.resolveSignature(signature, MethodSignatureBean.class)).isEqualTo(desiredMethod);
	}

	private OrderSummary proxyOrder(Order order) {
		return (OrderSummary) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{OrderSummary.class}, new OrderInvocationHandler(order));
	}

	private interface MapEntry<K, V> {

		K getKey();

		void setKey(V value);

		V getValue();

		void setValue(V value);
	}

	private interface OrderSummary {

		String getId();

		List<String> getLineItems();
	}

	@SuppressWarnings("unused")
	private static class IntegerListHolder1 {

		private List<Integer> list = new ArrayList<>();

		public List<Integer> getList() {
			return list;
		}

		public void setList(List<Integer> list) {
			this.list = list;
		}
	}

	@SuppressWarnings("unused")
	private static class IntegerListHolder2 {

		private List<Integer> list = new ArrayList<>();

		public List<Integer> getList() {
			return list;
		}

		public void setList(List<Integer> list) {
			this.list = list;
		}
	}

	@SuppressWarnings("unused")
	private static class LongListHolder {

		private List<Long> list = new ArrayList<>();

		public List<Long> getList() {
			return list;
		}

		public void setList(List<Long> list) {
			this.list = list;
		}
	}

	@SuppressWarnings("unused")
	private static class NameAndSpecialProperty {

		private String name;

		private int specialProperty;

		public String getName() {
			return this.name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public int getSpecialProperty() {
			return specialProperty;
		}

		public void setSpecialProperty(int specialProperty) {
			this.specialProperty = specialProperty;
		}
	}

	@SuppressWarnings("unused")
	private static class InvalidProperty {

		private String name;

		private String value;

		private boolean flag1;

		private boolean flag2;

		public String getName() {
			return this.name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getValue() {
			return this.value;
		}

		public void setValue(int value) {
			this.value = Integer.toString(value);
		}

		public Boolean getFlag1() {
			return this.flag1;
		}

		public void setFlag1(boolean flag1) {
			this.flag1 = flag1;
		}

		public boolean getFlag2() {
			return this.flag2;
		}

		public void setFlag2(Boolean flag2) {
			this.flag2 = flag2;
		}
	}

	@SuppressWarnings("unused")
	private static class ContainerBean {

		private ContainedBean[] containedBeans;

		public ContainedBean[] getContainedBeans() {
			return containedBeans;
		}

		public void setContainedBeans(ContainedBean[] containedBeans) {
			this.containedBeans = containedBeans;
		}
	}

	@SuppressWarnings("unused")
	private static class ContainedBean {

		private String name;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}

	@SuppressWarnings("unused")
	private static class MethodSignatureBean {

		public void doSomething() {
		}

		public void doSomethingElse(String s, int x) {
		}

		public void overloaded() {
		}

		public void overloaded(String s) {
		}

		public void overloaded(String s, BeanFactory beanFactory) {
		}

		public void doSomethingWithAnArray(String[] strings) {
		}

		public void doSomethingWithAMultiDimensionalArray(String[][] strings) {
		}
	}

	private static class Bean implements MapEntry<String, String> {

		private String key;

		private String value;

		@Override
		public String getKey() {
			return key;
		}

		@Override
		public void setKey(String aKey) {
			key = aKey;
		}

		@Override
		public String getValue() {
			return value;
		}

		@Override
		public void setValue(String aValue) {
			value = aValue;
		}
	}

	private static class BeanWithNullableTypes {

		private Integer counter;

		private Boolean flag;

		private String value;

		@SuppressWarnings("unused")
		public BeanWithNullableTypes(@Nullable Integer counter, @Nullable Boolean flag, String value) {
			this.counter = counter;
			this.flag = flag;
			this.value = value;
		}

		@Nullable
		public Integer getCounter() {
			return counter;
		}

		@Nullable
		public Boolean isFlag() {
			return flag;
		}

		public String getValue() {
			return value;
		}
	}

	private static class BeanWithPrimitiveTypes {

		private int counter;

		private boolean flag;

		private String value;

		@SuppressWarnings("unused")
		public BeanWithPrimitiveTypes(int counter, boolean flag, String value) {
			this.counter = counter;
			this.flag = flag;
			this.value = value;
		}

		public int getCounter() {
			return counter;
		}

		public boolean isFlag() {
			return flag;
		}

		public String getValue() {
			return value;
		}
	}

	private static class PrivateBeanWithPrivateConstructor {

		private PrivateBeanWithPrivateConstructor() {
		}
	}

	@SuppressWarnings("unused")
	private static class Order {

		private String id;
		private List<String> lineItems;


		Order() {
		}

		Order(String id, List<String> lineItems) {
			this.id = id;
			this.lineItems = lineItems;
		}

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}

		public List<String> getLineItems() {
			return this.lineItems;
		}

		public void setLineItems(List<String> lineItems) {
			this.lineItems = lineItems;
		}

		@Override
		public String toString() {
			return "Order [id=" + this.id + ", lineItems=" + this.lineItems + "]";
		}
	}

	private static class OrderInvocationHandler implements InvocationHandler {

		private final Order order;


		OrderInvocationHandler(Order order) {
			this.order = order;
		}

		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			try {
				// Ignore args since OrderSummary doesn't declare any methods with arguments,
				// and we're not supporting equals(Object), etc.
				return Order.class.getDeclaredMethod(method.getName()).invoke(this.order);
			} catch (InvocationTargetException ex) {
				throw ex.getTargetException();
			}
		}
	}

}
