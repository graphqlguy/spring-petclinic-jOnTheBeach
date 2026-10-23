package org.springframework.samples.petclinic.upstream;

import java.util.Map;

import graphql.ErrorClassification;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.stereotype.Component;

/**
 * Turns failures of external services into GraphQL errors a client can act on. The error
 * names the service. The technical cause goes to the log only.
 */
@Component
class UpstreamExceptionResolver extends DataFetcherExceptionResolverAdapter {

	/**
	 * Classification of the error for a failed external service.
	 */
	static final ErrorClassification UPSTREAM_UNAVAILABLE = ErrorClassification
		.errorClassification("UPSTREAM_UNAVAILABLE");

	private static final Log logger = LogFactory.getLog(UpstreamExceptionResolver.class);

	@Override
	protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
		if (ex instanceof UpstreamException upstream) {
			logger.warn(upstream.getUpstream() + " failed for " + env.getExecutionStepInfo().getPath() + ": "
					+ upstream.getMessage());
			return GraphqlErrorBuilder.newError(env)
				.errorType(UPSTREAM_UNAVAILABLE)
				.message("%s is unavailable", upstream.getUpstream())
				.extensions(Map.of("upstream", upstream.getUpstream()))
				.build();
		}
		if (ex instanceof InvalidArgumentException) {
			return GraphqlErrorBuilder.newError(env).errorType(ErrorType.BAD_REQUEST).message(ex.getMessage()).build();
		}
		return null;
	}

}
