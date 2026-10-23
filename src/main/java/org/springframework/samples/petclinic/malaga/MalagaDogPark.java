package org.springframework.samples.petclinic.malaga;

import org.springframework.samples.petclinic.upstream.GeoPoint;

/**
 * A park or beach for dogs of the city of Málaga.
 *
 * @param id the id of the park in the city's register
 * @param name the name, such as Parque canino San Miguel
 * @param address the address
 * @param beach whether it is a beach for dogs
 * @param surfaceSquareMetres the surface, or null when the city has not measured it
 * @param hasFountain whether it has a drinking fountain
 * @param twoZones whether it has separate zones for small and large dogs
 * @param openingHours the opening hours, in Spanish, or null
 * @param accessible whether it is adapted to people with reduced mobility
 * @param point where it is
 * @param distanceInMetres how far it is from the point of a search, or null
 */
public record MalagaDogPark(String id, String name, String address, boolean beach, Integer surfaceSquareMetres,
		Boolean hasFountain, Boolean twoZones, String openingHours, Boolean accessible, GeoPoint point,
		Integer distanceInMetres) {

	MalagaDogPark withDistanceFrom(GeoPoint from) {
		return new MalagaDogPark(this.id, this.name, this.address, this.beach, this.surfaceSquareMetres,
				this.hasFountain, this.twoZones, this.openingHours, this.accessible, this.point,
				this.point.distanceInMetres(from));
	}

	public double latitude() {
		return this.point.latitude();
	}

	public double longitude() {
		return this.point.longitude();
	}

}
