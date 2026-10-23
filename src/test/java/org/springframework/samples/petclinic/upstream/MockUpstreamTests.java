package org.springframework.samples.petclinic.upstream;

import com.github.tomakehurst.wiremock.WireMockServer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.tester.AutoConfigureGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.TestSocketUtils;

/**
 * Runs the application with the <code>mock</code> profile, for tests that query the
 * Spanish public services through GraphQL. The answers come from the responses recorded
 * in <code>src/main/resources/mock-upstreams</code>. Each service has a test class of its
 * own that extends this one, and they all share one application context.
 */
@SpringBootTest
@AutoConfigureGraphQlTester
@ActiveProfiles("mock")
public abstract class MockUpstreamTests {

	// the port is read twice, by the mock server and by the URLs of the clients, so it is
	// chosen once for every test class
	private static final int PORT = TestSocketUtils.findAvailableTcpPort();

	@Autowired
	protected GraphQlTester graphQl;

	@Autowired
	protected WireMockServer mockUpstreams;

	@DynamicPropertySource
	static void mockPort(DynamicPropertyRegistry registry) {
		registry.add("petclinic.mock.port", () -> PORT);
	}

}
