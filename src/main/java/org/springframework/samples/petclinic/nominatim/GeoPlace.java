package org.springframework.samples.petclinic.nominatim;

/**
 * A place on the map that Nominatim found, such as the building of an address.
 *
 * @param displayName the full name of the place, from the building to the country
 * @param latitude the latitude
 * @param longitude the longitude
 * @param category the kind of map feature, such as <code>amenity</code>
 * @param type the kind within the category, such as <code>bank</code>
 * @param addressType the part of an address the place is, such as <code>road</code> or
 * <code>building</code>
 * @param name the name of the place, or an empty text
 */
public record GeoPlace(String displayName, double latitude, double longitude, String category, String type,
		String addressType, String name) {

	static GeoPlace of(NominatimApi.Place place) {
		return new GeoPlace(place.displayName(), Double.parseDouble(place.latitude()),
				Double.parseDouble(place.longitude()), place.category(), place.type(), place.addressType(),
				place.name());
	}

}
