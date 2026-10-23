package org.springframework.samples.petclinic.upstream;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Reads XML as the nested maps that the fields of the schema read, the same shape a JSON
 * answer has. An element with child elements becomes a map from the local names of the
 * children to their values, a name that repeats becomes a list, and an element with text
 * alone becomes the text. Attributes and namespaces are left out, since the SOAP services
 * behind the schema keep their data in elements.
 */
public final class XmlMaps {

	private XmlMaps() {
	}

	public static Object read(Element element) {
		Map<String, List<Object>> children = new LinkedHashMap<>();
		for (Node node = element.getFirstChild(); node != null; node = node.getNextSibling()) {
			if (node instanceof Element child) {
				children.computeIfAbsent(child.getLocalName(), (name) -> new ArrayList<>()).add(read(child));
			}
		}
		if (children.isEmpty()) {
			return element.getTextContent();
		}
		Map<String, Object> map = new LinkedHashMap<>();
		children.forEach((name, values) -> map.put(name, (values.size() == 1) ? values.get(0) : values));
		return map;
	}

}
