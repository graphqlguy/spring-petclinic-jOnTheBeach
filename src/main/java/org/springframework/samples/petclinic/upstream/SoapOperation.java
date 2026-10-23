package org.springframework.samples.petclinic.upstream;

import java.util.List;
import java.util.Map;

/**
 * One operation of a SOAP service with a document/literal binding, which the schema
 * exposes as a root field. The body of the request holds one element for each part of the
 * operation's input message, in the order of the WSDL, and the answer is read as nested
 * maps, see {@link XmlMaps}.
 *
 * @param field the root field on <code>Query</code>
 * @param service the key of the endpoint that serves the operation, such as
 * <code>callejero</code>
 * @param action the SOAPAction of the operation, from the WSDL
 * @param parts the parts of the input message
 */
public record SoapOperation(String field, String service, String action, List<Part> parts) {

	/**
	 * A part of the input message: an element whose text comes from an argument of the
	 * field, or a fixed text.
	 *
	 * @param element the local name of the element, in the namespace of the service
	 * @param argument the argument of the field that fills the element, or null for a
	 * fixed text
	 * @param value the text of the element when the argument is left out, or the fixed
	 * text. The services take an empty element for a part that does not apply.
	 * @param values the text the service expects for each value of the argument, such as
	 * <code>CL</code> for the enum value <code>STREET</code>, or null
	 */
	public record Part(String element, String argument, String value, Map<String, String> values) {

	}

}
