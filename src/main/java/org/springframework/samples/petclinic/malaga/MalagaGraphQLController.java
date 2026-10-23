package org.springframework.samples.petclinic.malaga;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.samples.petclinic.nominatim.Geocoder;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.system.Text;
import org.springframework.samples.petclinic.upstream.GeoPoint;
import org.springframework.samples.petclinic.upstream.InvalidArgumentException;
import org.springframework.stereotype.Controller;

@Controller
class MalagaGraphQLController {

	static final int MAX_RESULTS = 20;

	private static final String MALAGA = "Málaga";

	private final MalagaOpenData openData;

	private final Geocoder geocoder;

	MalagaGraphQLController(MalagaOpenData openData, Geocoder geocoder) {
		this.openData = openData;
		this.geocoder = geocoder;
	}

	/**
	 * Resolves <code>Owner.nearestDogParks</code>. An owner in the city of Málaga,
	 * written with or without the accent, costs a call to Nominatim the first time, and
	 * the parks come from the copy the application keeps. An owner in another town gets
	 * an empty list without a call, because the city lists its own parks only.
	 */
	@SchemaMapping(typeName = "Owner")
	List<MalagaDogPark> nearestDogParks(Owner owner, @Argument int first) {
		return homeInMalaga(owner).map((home) -> nearestDogParks(home, first)).orElse(List.of());
	}

	@SchemaMapping(typeName = "Owner")
	List<MalagaDrinkingFountain> nearestDrinkingFountains(Owner owner, @Argument int first) {
		return homeInMalaga(owner).map((home) -> nearestFountains(home, first)).orElse(List.of());
	}

	@QueryMapping
	List<MalagaDogPark> malagaDogParks(@Argument Boolean hasFountain, @Argument Boolean twoZones,
			@Argument Boolean beach) {
		return openData.dogParks()
			.stream()
			.filter((park) -> wanted(hasFountain, park.hasFountain()) && wanted(twoZones, park.twoZones())
					&& wanted(beach, park.beach()))
			.toList();
	}

	@QueryMapping
	List<MalagaDogPark> malagaDogParksNear(@Argument double latitude, @Argument double longitude, @Argument int first) {
		return nearestDogParks(new GeoPoint(latitude, longitude), first);
	}

	@QueryMapping
	List<MalagaDrinkingFountain> malagaDrinkingFountainsNear(@Argument double latitude, @Argument double longitude,
			@Argument int first) {
		return nearestFountains(new GeoPoint(latitude, longitude), first);
	}

	@QueryMapping
	List<MalagaBus> malagaBuses(@Argument String line) {
		return openData.buses()
			.stream()
			.filter((bus) -> line == null || line.strip().equalsIgnoreCase(bus.line()))
			.toList();
	}

	/**
	 * The point of an owner who lives in Málaga, written in any case and with or without
	 * the accent. Nominatim gets the city as Málaga, the spelling of the answers recorded
	 * for the <code>mock</code> profile.
	 */
	private Optional<GeoPoint> homeInMalaga(Owner owner) {
		if (owner.getCity() == null || !Text.withoutAccents(owner.getCity()).equals(Text.withoutAccents(MALAGA))) {
			return Optional.empty();
		}
		return geocoder.point(owner.getAddress(), MALAGA);
	}

	private List<MalagaDogPark> nearestDogParks(GeoPoint from, int first) {
		checkFirst(first);
		return openData.dogParks()
			.stream()
			.map((park) -> park.withDistanceFrom(from))
			.sorted(Comparator.comparing(MalagaDogPark::distanceInMetres))
			.limit(first)
			.toList();
	}

	private List<MalagaDrinkingFountain> nearestFountains(GeoPoint from, int first) {
		checkFirst(first);
		return openData.fountains()
			.stream()
			.map((fountain) -> fountain.withDistanceFrom(from))
			.sorted(Comparator.comparing(MalagaDrinkingFountain::distanceInMetres))
			.limit(first)
			.toList();
	}

	private static void checkFirst(int first) {
		if (first < 1 || first > MAX_RESULTS) {
			throw new InvalidArgumentException("first must be between 1 and " + MAX_RESULTS);
		}
	}

	/**
	 * Whether a park passes a yes or no filter. A null filter passes every park, and a
	 * park the city has not recorded a value for passes only a null filter.
	 */
	private static boolean wanted(Boolean filter, Boolean value) {
		return filter == null || filter.equals(value);
	}

}
