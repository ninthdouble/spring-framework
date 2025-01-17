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

package org.springframework.mock.web;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.Part;
import java.io.IOException;
import java.util.*;

/**
 * Mock implementation of the
 * {@link org.springframework.web.multipart.MultipartHttpServletRequest} interface.
 *
 * <p>As of Spring 5.0, this set of mocks is designed on a Servlet 4.0 baseline.
 *
 * <p>Useful for testing application controllers that access multipart uploads.
 * {@link MockMultipartFile} can be used to populate these mock requests with files.
 *
 * @author Juergen Hoeller
 * @author Eric Crampton
 * @author Arjen Poutsma
 * @see MockMultipartFile
 * @since 2.0
 */
public class MockMultipartHttpServletRequest extends MockHttpServletRequest implements MultipartHttpServletRequest {

	private final MultiValueMap<String, MultipartFile> multipartFiles = new LinkedMultiValueMap<>();


	/**
	 * Create a new {@code MockMultipartHttpServletRequest} with a default
	 * {@link MockServletContext}.
	 *
	 * @see #MockMultipartHttpServletRequest(ServletContext)
	 */
	public MockMultipartHttpServletRequest() {
		this(null);
	}

	/**
	 * Create a new {@code MockMultipartHttpServletRequest} with the supplied {@link ServletContext}.
	 *
	 * @param servletContext the ServletContext that the request runs in
	 *                       (may be {@code null} to use a default {@link MockServletContext})
	 */
	public MockMultipartHttpServletRequest(@Nullable ServletContext servletContext) {
		super(servletContext);
		setMethod("POST");
		setContentType("multipart/form-data");
	}


	/**
	 * Add a file to this request. The parameter name from the multipart
	 * form is taken from the {@link MultipartFile#getName()}.
	 *
	 * @param file multipart file to be added
	 */
	public void addFile(MultipartFile file) {
		Assert.notNull(file, "MultipartFile must not be null");
		this.multipartFiles.add(file.getName(), file);
	}

	@Override
	public Iterator<String> getFileNames() {
		return this.multipartFiles.keySet().iterator();
	}

	@Override
	public MultipartFile getFile(String name) {
		return this.multipartFiles.getFirst(name);
	}

	@Override
	public List<MultipartFile> getFiles(String name) {
		List<MultipartFile> multipartFiles = this.multipartFiles.get(name);
		if (multipartFiles != null) {
			return multipartFiles;
		} else {
			return Collections.emptyList();
		}
	}

	@Override
	public Map<String, MultipartFile> getFileMap() {
		return this.multipartFiles.toSingleValueMap();
	}

	@Override
	public MultiValueMap<String, MultipartFile> getMultiFileMap() {
		return new LinkedMultiValueMap<>(this.multipartFiles);
	}

	@Override
	public String getMultipartContentType(String paramOrFileName) {
		MultipartFile file = getFile(paramOrFileName);
		if (file != null) {
			return file.getContentType();
		}
		try {
			Part part = getPart(paramOrFileName);
			if (part != null) {
				return part.getContentType();
			}
		} catch (ServletException | IOException ex) {
			// Should never happen (we're not actually parsing)
			throw new IllegalStateException(ex);
		}
		return null;
	}

	@Override
	public HttpMethod getRequestMethod() {
		return HttpMethod.resolve(getMethod());
	}

	@Override
	public HttpHeaders getRequestHeaders() {
		HttpHeaders headers = new HttpHeaders();
		Enumeration<String> headerNames = getHeaderNames();
		while (headerNames.hasMoreElements()) {
			String headerName = headerNames.nextElement();
			headers.put(headerName, Collections.list(getHeaders(headerName)));
		}
		return headers;
	}

	@Override
	public HttpHeaders getMultipartHeaders(String paramOrFileName) {
		MultipartFile file = getFile(paramOrFileName);
		if (file != null) {
			HttpHeaders headers = new HttpHeaders();
			if (file.getContentType() != null) {
				headers.add(HttpHeaders.CONTENT_TYPE, file.getContentType());
			}
			return headers;
		}
		try {
			Part part = getPart(paramOrFileName);
			if (part != null) {
				HttpHeaders headers = new HttpHeaders();
				for (String headerName : part.getHeaderNames()) {
					headers.put(headerName, new ArrayList<>(part.getHeaders(headerName)));
				}
				return headers;
			}
		} catch (Throwable ex) {
			throw new MultipartException("Could not access multipart servlet request", ex);
		}
		return null;
	}

}
