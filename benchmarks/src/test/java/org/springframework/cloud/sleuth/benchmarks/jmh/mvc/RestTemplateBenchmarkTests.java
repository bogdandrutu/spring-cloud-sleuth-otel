/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.sleuth.benchmarks.jmh.mvc;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletException;

import jmh.mbr.junit5.Microbenchmark;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import org.springframework.boot.SpringApplication;
import org.springframework.cloud.sleuth.benchmarks.app.mvc.SleuthBenchmarkingSpringApp;
import org.springframework.cloud.sleuth.benchmarks.jmh.TracerImplementation;
import org.springframework.cloud.sleuth.instrument.web.mvc.TracingClientHttpRequestInterceptor;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.web.client.MockMvcClientHttpRequestFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * We're checking how much overhead does the instrumentation of the RestTemplate take
 */
@Measurement(iterations = 5, time = 1)
@Warmup(iterations = 5, time = 1)
@Fork(2)
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Threads(Threads.MAX)
@Microbenchmark
public class RestTemplateBenchmarkTests {

	@Benchmark
	public void syncEndpointWithoutSleuth(BenchmarkContext context) throws IOException, ServletException {
		then(context.untracedTemplate.getForObject("/foo", String.class)).isEqualTo("foo");
	}

	@Benchmark
	public void syncEndpointWithSleuth(BenchmarkContext context) throws ServletException, IOException {
		then(context.tracedTemplate.getForObject("/foo", String.class)).isEqualTo("foo");
	}

	@State(Scope.Benchmark)
	public static class BenchmarkContext {

		volatile ConfigurableApplicationContext withSleuth;

		volatile MockMvc mockMvc;

		volatile RestTemplate tracedTemplate;

		volatile RestTemplate untracedTemplate;

		@Param
		private TracerImplementation tracerImplementation;

		@Setup
		public void setup() {
			this.withSleuth = new SpringApplication(SleuthBenchmarkingSpringApp.class).run("--spring.jmx.enabled=false",
					"--spring.application.name=withSleuth_" + this.tracerImplementation.name());
			this.mockMvc = MockMvcBuilders.standaloneSetup(this.withSleuth.getBean(SleuthBenchmarkingSpringApp.class))
					.build();
			this.tracedTemplate = new RestTemplate(new MockMvcClientHttpRequestFactory(this.mockMvc));
			this.tracedTemplate.setInterceptors(
					Collections.singletonList(this.withSleuth.getBean(TracingClientHttpRequestInterceptor.class)));
			this.untracedTemplate = new RestTemplate(new MockMvcClientHttpRequestFactory(this.mockMvc));
		}

		@TearDown
		public void clean() {
			this.withSleuth.getBean(SleuthBenchmarkingSpringApp.class).clean();
			this.withSleuth.close();
		}

	}

}
