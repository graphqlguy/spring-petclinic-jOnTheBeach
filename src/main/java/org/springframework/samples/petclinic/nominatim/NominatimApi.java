package org.springframework.samples.petclinic.nominatim;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.registry.ImportHttpServices;

/**
 * The geocoder of OpenStreetMap, Nominatim, which turns an address into a point and a
 * point into an address. Its usage policy allows one request a second, and asks every
 * application to name itself in the User-Agent header.
 */
@HttpExchange(accept = MediaType.APPLICATION_JSON_VALUE)
interface NominatimApi {

	/**
	 * Finds places by a text, such as <code>Calle Larios 12, Málaga</code>. A text
	 * without a match comes back as an empty list.
	 * @param query the text
	 * @param format <code>jsonv2</code>
	 * @param limit the largest number of places to return
	 * @param countryCodes the countries to search, <code>es</code> for Spain
	 */
	@GetExchange("/search")
	List<Place> search(@RequestParam("q") String query, @RequestParam String format, @RequestParam int limit,
			@RequestParam("countrycodes") String countryCodes);

	/**
	 * Finds the address of a point.
	 * @param latitude the latitude
	 * @param longitude the longitude
	 * @param format <code>jsonv2</code>
	 */
	@GetExchange("/reverse")
	Place reverse(@RequestParam("lat") double latitude, @RequestParam("lon") double longitude,
			@RequestParam String format);

	/**
	 * A place, as Nominatim sends it. The latitude and longitude come as text.
	 *
	 * @param id the id of the place in Nominatim
	 * @param latitude the latitude, such as <code>36.7188122</code>
	 * @param longitude the longitude, such as <code>-4.4216645</code>
	 * @param displayName the full name of the place, from the building to the country
	 * @param category the kind of map feature, such as <code>amenity</code> or
	 * <code>highway</code>
	 * @param type the kind within the category, such as <code>bank</code> or
	 * <code>residential</code>
	 * @param addressType the part of an address the place is, such as <code>road</code>
	 * @param name the name of the place, or an empty text
	 */
	record Place(@JsonProperty("place_id") long id, @JsonProperty("lat") String latitude,
			@JsonProperty("lon") String longitude, @JsonProperty("display_name") String displayName, String category,
			String type, @JsonProperty("addresstype") String addressType, String name) {

	}

	/**
	 * Registers the interface as an HTTP service in the group <code>nominatim</code>,
	 * whose base URL and timeouts come from
	 * <code>spring.http.serviceclient.nominatim.*</code>.
	 */
	@Configuration(proxyBeanMethods = false)
	@ImportHttpServices(group = "nominatim", types = NominatimApi.class)
	class Registration {

	}

}
