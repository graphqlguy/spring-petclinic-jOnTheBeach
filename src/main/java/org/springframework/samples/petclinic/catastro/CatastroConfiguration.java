package org.springframework.samples.petclinic.catastro;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.xml.soap.SOAPBody;
import jakarta.xml.soap.SOAPException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.webservices.client.WebServiceMessageSenderFactory;
import org.springframework.boot.webservices.client.WebServiceTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;
import org.springframework.samples.petclinic.upstream.SoapOperationTable;
import org.springframework.samples.petclinic.upstream.SoapOperationTable.SoapCaller;
import org.springframework.samples.petclinic.upstream.UpstreamClients;
import org.springframework.samples.petclinic.upstream.UpstreamException;
import org.springframework.samples.petclinic.upstream.XmlMaps;
import org.springframework.ws.WebServiceException;
import org.springframework.ws.client.core.WebServiceMessageCallback;
import org.springframework.ws.client.core.WebServiceMessageExtractor;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.soap.SoapMessage;
import org.springframework.ws.soap.saaj.SaajSoapMessage;
import org.springframework.ws.transport.HeadersAwareSenderWebServiceConnection;
import org.springframework.ws.transport.context.TransportContextHolder;

/**
 * The fields of the Catastro, the land registry of Spain, over SOAP: its street directory
 * (OVCCallejero) and its service of coordinates (OVCCoordenadas). One fetcher serves them
 * all from an operation table, <code>catastro/operations.json</code>, which a generator
 * wrote from the two WSDLs. The bindings are document/literal with several parts, so the
 * body of a request holds one element for each part, in the namespace of the Catastro.
 */
@Configuration(proxyBeanMethods = false)
class CatastroConfiguration {

	/**
	 * The name of the service in errors, in words a client can read.
	 */
	static final String UPSTREAM = "The Catastro, the land registry of Spain";

	private static final String NAMESPACE = "http://www.catastro.meh.es/";

	/**
	 * What a client can do about an error of the Catastro, by the code of the error. A
	 * model that sends a province as a number, such as 29, gets LA PROVINCIA NO EXISTE,
	 * and finds the names the Catastro takes with the field that the hint names.
	 */
	private static final Map<String, String> HINTS = Map.of("12",
			"catastroProvinces lists the provinces as the Catastro writes them, such as MALAGA", "22",
			"catastroMunicipalities lists the municipalities of a province as the Catastro writes them, such as MALAGA or TORREMOLINOS",
			"33",
			"catastroStreets finds the type and the name of a street as the Catastro writes them, such as CL and MARQUES DE LARIOS for Calle Larios",
			"43", "catastroStreetNumbers lists the numbers of a street", "5",
			"catastroStreets finds the name of a street as the Catastro writes it, and catastroParcelAt finds the parcel at a point");

	@Bean
	RuntimeWiringConfigurer catastroOperations(WebServiceTemplateBuilder builder, UpstreamClients clients,
			@Value("${petclinic.upstream.catastro.callejero-url}") String callejeroUrl,
			@Value("${petclinic.upstream.catastro.coordenadas-url}") String coordenadasUrl,
			@Value("${petclinic.upstream.connect-timeout}") Duration connectTimeout,
			@Value("${petclinic.upstream.read-timeout}") Duration readTimeout,
			@Value("${petclinic.upstream.user-agent}") String userAgent) {
		WebServiceTemplate soap = builder
			.httpMessageSenderFactory(WebServiceMessageSenderFactory
				.http(HttpClientSettings.defaults().withTimeouts(connectTimeout, readTimeout)))
			.build();
		Map<String, String> endpoints = Map.of("callejero", callejeroUrl, "coordenadas", coordenadasUrl);
		SoapOperationTable table = SoapOperationTable.read(new ClassPathResource("catastro/operations.json"),
				clients.mapper());
		SoapCaller caller = (service, action, parts) -> {
			WebServiceMessageCallback request = (message) -> {
				((SoapMessage) message).setSoapAction(action);
				// the firewall of the Catastro turns away requests that do not name their
				// application
				((HeadersAwareSenderWebServiceConnection) TransportContextHolder.getTransportContext().getConnection())
					.addRequestHeader("User-Agent", userAgent);
				writeParts((SaajSoapMessage) message, parts);
			};
			WebServiceMessageExtractor<Object> answer = (message) -> XmlMaps
				.read(firstElement((SaajSoapMessage) message));
			try {
				return soap.sendAndReceive(endpoints.get(service), request, answer);
			}
			catch (WebServiceException ex) {
				// covers SOAP faults, timeouts and connection failures
				throw new UpstreamException(UPSTREAM, ex);
			}
		};
		return (wiring) -> table.register(wiring, caller, CatastroConfiguration::errors);
	}

	/**
	 * The errors the Catastro reports in an answer, in <code>lerr/err</code>, such as LA
	 * VÍA NO EXISTE for an unknown street, each with a hint when one helps. The answer is
	 * the element of the operation, such as <code>Callejero</code>, which holds the
	 * result, such as <code>consulta_callejero</code>.
	 */
	static List<String> errors(Object answer) {
		List<String> messages = new ArrayList<>();
		if (answer instanceof Map<?, ?> operation) {
			for (Object result : operation.values()) {
				if (result instanceof Map<?, ?> fields && fields.get("lerr") instanceof Map<?, ?> lerr) {
					Object err = lerr.get("err");
					// one error is an element of its own, several are a list
					List<?> errors = (err instanceof List<?> list) ? list : (err != null) ? List.of(err) : List.of();
					for (Object error : errors) {
						if (error instanceof Map<?, ?> map && map.get("des") != null) {
							String message = UPSTREAM + ", answered: " + map.get("des").toString().strip();
							String hint = HINTS.get(String.valueOf(map.get("cod")).strip());
							messages.add((hint != null) ? message.replaceAll("\\.$", "") + ". " + hint : message);
						}
					}
				}
			}
		}
		return messages;
	}

	private static void writeParts(SaajSoapMessage message, Map<String, String> parts) throws IOException {
		try {
			SOAPBody body = message.getSaajMessage().getSOAPBody();
			for (Map.Entry<String, String> part : parts.entrySet()) {
				body.addChildElement(part.getKey(), "c", NAMESPACE).addTextNode(part.getValue());
			}
		}
		catch (SOAPException ex) {
			throw new IOException("Cannot write the body of the request", ex);
		}
	}

	private static Element firstElement(SaajSoapMessage message) throws IOException {
		try {
			for (Node node = message.getSaajMessage().getSOAPBody().getFirstChild(); node != null; node = node
				.getNextSibling()) {
				if (node instanceof Element element) {
					return element;
				}
			}
		}
		catch (SOAPException ex) {
			throw new IOException("Cannot read the body of the answer", ex);
		}
		throw new IOException("The answer of the Catastro has an empty body");
	}

}
