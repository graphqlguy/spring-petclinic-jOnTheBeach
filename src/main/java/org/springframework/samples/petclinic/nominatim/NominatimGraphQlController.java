package org.springframework.samples.petclinic.nominatim;

import java.util.List;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.upstream.InvalidArgumentException;
import org.springframework.stereotype.Controller;

@Controller
class NominatimGraphQlController {

	static final int MAX_PLACES = 10;

	private final Geocoder geocoder;

	NominatimGraphQlController(Geocoder geocoder) {
		this.geocoder = geocoder;
	}

	/**
	 * Resolves <code>Owner.location</code>. The first time an address is asked for it
	 * costs one call to Nominatim, and later questions reuse the answer.
	 */
	@SchemaMapping(typeName = "Owner")
	GeoPlace location(Owner owner) {
		return this.geocoder.locate(owner).orElse(null);
	}

	@QueryMapping
	List<GeoPlace> nominatimSearch(@Argument String query, @Argument int first) {
		if (first < 1 || first > MAX_PLACES) {
			throw new InvalidArgumentException("first must be between 1 and " + MAX_PLACES);
		}
		return this.geocoder.search(query, first);
	}

	@QueryMapping
	GeoPlace nominatimReverse(@Argument double latitude, @Argument double longitude) {
		return this.geocoder.reverse(latitude, longitude);
	}

}
