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

package org.springframework.web.servlet;

import org.springframework.beans.BeansException;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.ui.context.Theme;
import org.springframework.ui.context.ThemeSource;
import org.springframework.ui.context.support.SimpleTheme;
import org.springframework.ui.context.support.UiApplicationContextUtils;
import org.springframework.web.context.support.StaticWebApplicationContext;
import org.springframework.web.servlet.handler.BeanNameUrlHandlerMapping;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;
import org.springframework.web.servlet.mvc.Controller;
import org.springframework.web.servlet.support.RequestContextUtils;
import org.springframework.web.servlet.theme.AbstractThemeResolver;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;

/**
 * @author Juergen Hoeller
 * @since 21.05.2003
 */
public class SimpleWebApplicationContext extends StaticWebApplicationContext {

	@Override
	@SuppressWarnings("deprecation")
	public void refresh() throws BeansException {
		registerSingleton("/locale.do", LocaleChecker.class);

		addMessage("test", Locale.ENGLISH, "test message");
		addMessage("test", Locale.CANADA, "Canadian & test message");
		addMessage("testArgs", Locale.ENGLISH, "test {0} message {1}");
		addMessage("testArgsFormat", Locale.ENGLISH, "test {0} message {1,number,#.##} X");

		registerSingleton(UiApplicationContextUtils.THEME_SOURCE_BEAN_NAME, DummyThemeSource.class);

		registerSingleton("handlerMapping", BeanNameUrlHandlerMapping.class);
		registerSingleton("viewResolver", InternalResourceViewResolver.class);

		MutablePropertyValues pvs = new MutablePropertyValues();
		pvs.add("location", "org/springframework/web/context/WEB-INF/sessionContext.xml");
		registerSingleton("viewResolver2", org.springframework.web.servlet.view.XmlViewResolver.class, pvs);

		super.refresh();
	}


	@SuppressWarnings("deprecation")
	public static class LocaleChecker implements Controller, org.springframework.web.servlet.mvc.LastModified {

		@Override
		public ModelAndView handleRequest(HttpServletRequest request, HttpServletResponse response)
				throws ServletException, IOException {

			if (!(RequestContextUtils.findWebApplicationContext(request) instanceof SimpleWebApplicationContext)) {
				throw new ServletException("Incorrect WebApplicationContext");
			}
			if (!(RequestContextUtils.getLocaleResolver(request) instanceof AcceptHeaderLocaleResolver)) {
				throw new ServletException("Incorrect LocaleResolver");
			}
			if (!Locale.CANADA.equals(RequestContextUtils.getLocale(request))) {
				throw new ServletException("Incorrect Locale");
			}
			return null;
		}

		@Override
		public long getLastModified(HttpServletRequest request) {
			return 1427846400000L;
		}
	}


	public static class DummyThemeSource implements ThemeSource {

		private StaticMessageSource messageSource;

		public DummyThemeSource() {
			this.messageSource = new StaticMessageSource();
			this.messageSource.addMessage("themetest", Locale.ENGLISH, "theme test message");
			this.messageSource.addMessage("themetestArgs", Locale.ENGLISH, "theme test message {0}");
		}

		@Override
		public Theme getTheme(String themeName) {
			if (AbstractThemeResolver.ORIGINAL_DEFAULT_THEME_NAME.equals(themeName)) {
				return new SimpleTheme(AbstractThemeResolver.ORIGINAL_DEFAULT_THEME_NAME, this.messageSource);
			} else {
				return null;
			}
		}
	}

}
