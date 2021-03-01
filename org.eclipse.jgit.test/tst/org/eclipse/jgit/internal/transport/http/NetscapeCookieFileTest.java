/*
 * Copyright (C) 2018, Konrad Windszus <konrad_w@gmx.de>
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.eclipse.jgit.internal.transport.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.net.HttpCookie;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jgit.internal.storage.file.LockFile;
import org.eclipse.jgit.internal.transport.http.NetscapeCookieFile;
import org.eclipse.jgit.util.http.HttpCookiesMatcher;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class NetscapeCookieFileTest {

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	private Path tmpFile;

	private URL baseUrl;

	/**
	 * This is the expiration date that is used in the test cookie files.
	 */
	private static final Instant TEST_EXPIRY_DATE = Instant
			.parse("2030-01-01T12:00:00.000Z");

	/** Earlier than TEST_EXPIRY_DATE. */
	private static final Instant TEST_DATE = TEST_EXPIRY_DATE.minus(180,
			ChronoUnit.DAYS);

	@Before
	public void setUp() throws IOException {
		// this will not only return a new file name but also create new empty
		// file!
		tmpFile = folder.newFile().toPath();
		baseUrl = new URL("http://domain.com/my/path");
	}

	@Test
	public void testMergeCookies() {
		Set<HttpCookie> cookieSet1 = new LinkedHashSet<>();
		HttpCookie cookie = new HttpCookie("key1", "valueFromSet1");
		cookieSet1.add(cookie);
		cookie = new HttpCookie("key2", "valueFromSet1");
		cookieSet1.add(cookie);

		Set<HttpCookie> cookieSet2 = new LinkedHashSet<>();
		cookie = new HttpCookie("key1", "valueFromSet2");
		cookieSet2.add(cookie);
		cookie = new HttpCookie("key3", "valueFromSet2");
		cookieSet2.add(cookie);

		Set<HttpCookie> cookiesExpectedMergedSet = new LinkedHashSet<>();
		cookie = new HttpCookie("key1", "valueFromSet1");
		cookiesExpectedMergedSet.add(cookie);
		cookie = new HttpCookie("key2", "valueFromSet1");
		cookiesExpectedMergedSet.add(cookie);
		cookie = new HttpCookie("key3", "valueFromSet2");
		cookiesExpectedMergedSet.add(cookie);

		Assert.assertThat(
				NetscapeCookieFile.mergeCookies(cookieSet1, cookieSet2),
				HttpCookiesMatcher.containsInOrder(cookiesExpectedMergedSet));

		Assert.assertThat(NetscapeCookieFile.mergeCookies(cookieSet1, null),
				HttpCookiesMatcher.containsInOrder(cookieSet1));
	}

	@Test
	public void testWriteToNewFile() throws IOException {
		Set<HttpCookie> cookies = new LinkedHashSet<>();
		cookies.add(new HttpCookie("key1", "value"));
		// first cookie is a session cookie (and should be ignored)

		HttpCookie cookie = new HttpCookie("key2", "value");
		cookie.setSecure(true);
		cookie.setDomain("mydomain.com");
		cookie.setPath("/");
		cookie.setMaxAge(1000);
		cookies.add(cookie);
		try (Writer writer = Files.newBufferedWriter(tmpFile,
				StandardCharsets.US_ASCII)) {
			NetscapeCookieFile.write(writer, cookies, baseUrl, TEST_DATE);
		}

		String expectedExpiration = String
				.valueOf(TEST_DATE.getEpochSecond() + cookie.getMaxAge());

		Assert.assertThat(
				Files.readAllLines(tmpFile, StandardCharsets.US_ASCII),
				CoreMatchers
						.equalTo(Arrays.asList("mydomain.com\tTRUE\t/\tTRUE\t"
								+ expectedExpiration + "\tkey2\tvalue")));
	}

	@Test
	public void testWriteToExistingFile() throws IOException {
		try (InputStream input = this.getClass()
				.getResourceAsStream("cookies-simple1.txt")) {
			Files.copy(input, tmpFile, StandardCopyOption.REPLACE_EXISTING);
		}

		Set<HttpCookie> cookies = new LinkedHashSet<>();
		HttpCookie cookie = new HttpCookie("key2", "value2");
		cookie.setMaxAge(1000);
		cookies.add(cookie);
		try (Writer writer = Files.newBufferedWriter(tmpFile,
				StandardCharsets.US_ASCII)) {
			NetscapeCookieFile.write(writer, cookies, baseUrl, TEST_DATE);
		}
		String expectedExpiration = String
				.valueOf(TEST_DATE.getEpochSecond() + cookie.getMaxAge());

		Assert.assertThat(
				Files.readAllLines(tmpFile, StandardCharsets.US_ASCII),
				CoreMatchers.equalTo(
						Arrays.asList("domain.com\tTRUE\t/my/path\tFALSE\t"
								+ expectedExpiration + "\tkey2\tvalue2")));
	}

	@Test(expected = IOException.class)
	public void testWriteWhileSomeoneIsHoldingTheLock()
			throws IllegalArgumentException, IOException, InterruptedException {
		try (InputStream input = this.getClass()
				.getResourceAsStream("cookies-simple1.txt")) {
			Files.copy(input, tmpFile, StandardCopyOption.REPLACE_EXISTING);
		}
		NetscapeCookieFile cookieFile = new NetscapeCookieFile(tmpFile);
		// now imitate another process/thread holding the lock file
		LockFile lockFile = new LockFile(tmpFile.toFile());
		try {
			Assert.assertTrue("Could not acquire lock", lockFile.lock());
			cookieFile.write(baseUrl);
		} finally {
			lockFile.unlock();
		}
	}

	@Test
	public void testReadCookieFileWithMilliseconds() throws IOException {
		try (InputStream input = this.getClass()
				.getResourceAsStream("cookies-with-milliseconds.txt")) {
			Files.copy(input, tmpFile, StandardCopyOption.REPLACE_EXISTING);
		}
		NetscapeCookieFile cookieFile = new NetscapeCookieFile(tmpFile,
				TEST_DATE);
		long expectedMaxAge = Duration.between(TEST_DATE, TEST_EXPIRY_DATE)
				.getSeconds();
		for (HttpCookie cookie : cookieFile.getCookies(true)) {
			Assert.assertEquals(expectedMaxAge, cookie.getMaxAge());
		}
	}

	@Test
	public void testWriteAfterAnotherJgitProcessModifiedTheFile()
			throws IOException, InterruptedException {
		try (InputStream input = this.getClass()
				.getResourceAsStream("cookies-simple1.txt")) {
			Files.copy(input, tmpFile, StandardCopyOption.REPLACE_EXISTING);
		}
		NetscapeCookieFile cookieFile = new NetscapeCookieFile(tmpFile,
				TEST_DATE);
		cookieFile.getCookies(true);
		// now modify file externally
		try (InputStream input = this.getClass()
				.getResourceAsStream("cookies-simple2.txt")) {
			Files.copy(input, tmpFile, StandardCopyOption.REPLACE_EXISTING);
		}
		// now try to write
		cookieFile.write(baseUrl);

		List<String> lines = Files.readAllLines(tmpFile,
				StandardCharsets.US_ASCII);

		Assert.assertEquals("Expected 3 lines", 3, lines.size());
		Assert.assertEquals(
				"some-domain1\tTRUE\t/some/path1\tFALSE\t1893499200\tkey1\tvalueFromSimple2",
				lines.get(0));
		Assert.assertEquals(
				"some-domain1\tTRUE\t/some/path1\tFALSE\t1893499200\tkey3\tvalueFromSimple2",
				lines.get(1));
		Assert.assertEquals(
				"some-domain1\tTRUE\t/some/path1\tFALSE\t1893499200\tkey2\tvalueFromSimple1",
				lines.get(2));
	}

	@Test
	public void testWriteAndReadCycle() throws IOException {
		Set<HttpCookie> cookies = new LinkedHashSet<>();

		HttpCookie cookie = new HttpCookie("key1", "value1");
		cookie.setPath("/some/path1");
		cookie.setDomain("some-domain1");
		cookie.setMaxAge(1000);
		cookies.add(cookie);
		cookie = new HttpCookie("key2", "value2");
		cookie.setSecure(true);
		cookie.setPath("/some/path2");
		cookie.setDomain("some-domain2");
		cookie.setMaxAge(1000);
		cookie.setHttpOnly(true);
		cookies.add(cookie);

		try (Writer writer = Files.newBufferedWriter(tmpFile,
				StandardCharsets.US_ASCII)) {
			NetscapeCookieFile.write(writer, cookies, baseUrl, TEST_DATE);
		}
		Set<HttpCookie> actualCookies = new NetscapeCookieFile(tmpFile,
				TEST_DATE)
				.getCookies(true);
		Assert.assertThat(actualCookies, HttpCookiesMatcher.containsInOrder(cookies));
	}

	@Test
	public void testReadAndWriteCycle() throws IOException {
		try (InputStream input = this.getClass()
				.getResourceAsStream("cookies-simple1.txt")) {
			Files.copy(input, tmpFile, StandardCopyOption.REPLACE_EXISTING);
		}
		Set<HttpCookie> cookies = new NetscapeCookieFile(tmpFile, TEST_DATE)
				.getCookies(true);
		Path tmpFile2 = folder.newFile().toPath();
		try (Writer writer = Files.newBufferedWriter(tmpFile2,
				StandardCharsets.US_ASCII)) {
			NetscapeCookieFile.write(writer, cookies, baseUrl, TEST_DATE);
		}
		// compare original file with newly written one, they should not differ
		Assert.assertEquals(Files.readAllLines(tmpFile),
				Files.readAllLines(tmpFile2));
	}

	@Test
	public void testReadWithEmptyAndCommentLines() throws IOException {
		try (InputStream input = this.getClass().getResourceAsStream(
				"cookies-with-empty-and-comment-lines.txt")) {
			Files.copy(input, tmpFile, StandardCopyOption.REPLACE_EXISTING);
		}

		Set<HttpCookie> cookies = new LinkedHashSet<>();

		HttpCookie cookie = new HttpCookie("key2", "value2");
		cookie.setDomain("some-domain2");
		cookie.setPath("/some/path2");
		cookie.setMaxAge(
				Duration.between(TEST_DATE, TEST_EXPIRY_DATE).getSeconds());
		cookie.setSecure(true);
		cookie.setHttpOnly(true);
		cookies.add(cookie);

		cookie = new HttpCookie("key3", "value3");
		cookie.setDomain("some-domain3");
		cookie.setPath("/some/path3");
		cookie.setMaxAge(
				Duration.between(TEST_DATE, TEST_EXPIRY_DATE).getSeconds());
		cookies.add(cookie);

		Set<HttpCookie> actualCookies = new NetscapeCookieFile(tmpFile,
				TEST_DATE).getCookies(true);
		Assert.assertThat(actualCookies, HttpCookiesMatcher.containsInOrder(cookies));
	}

	@Test
	public void testReadInvalidFile() throws IOException {
		try (InputStream input = this.getClass()
				.getResourceAsStream("cookies-invalid.txt")) {
			Files.copy(input, tmpFile, StandardCopyOption.REPLACE_EXISTING);
		}

		Assert.assertTrue(new NetscapeCookieFile(tmpFile, TEST_DATE).getCookies(true)
				.isEmpty());
	}
}
