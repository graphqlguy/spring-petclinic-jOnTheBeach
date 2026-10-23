package org.springframework.samples.petclinic.upstream;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Starts a local server that answers in place of the external services when the
 * <code>mock</code> profile is active. The answers are responses recorded from the real
 * services, kept in <code>src/main/resources/mock-upstreams</code>.
 * <p>
 * The clients are the same in both modes. Only the URLs they call differ, see
 * <code>application-mock.properties</code>.
 */
@Configuration(proxyBeanMethods = false)
@Profile("mock")
class MockUpstreamConfiguration {

	private static final Log logger = LogFactory.getLog(MockUpstreamConfiguration.class);

	@Bean(initMethod = "start", destroyMethod = "stop")
	WireMockServer mockUpstreams(@Value("${petclinic.mock.port}") int port) {
		logger.info("External services are mocked with recorded responses on port " + port);
		return new WireMockServer(
				WireMockConfiguration.options().port(port).usingFilesUnderClasspath("mock-upstreams"));
	}

}
