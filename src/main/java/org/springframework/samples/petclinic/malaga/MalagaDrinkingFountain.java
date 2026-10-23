package org.springframework.samples.petclinic.malaga;

import org.springframework.samples.petclinic.upstream.GeoPoint;

/**
 * A public drinking fountain of the city of Málaga.
 *
 * @param name the name, usually the street or park it stands in
 * @param point where it is
 * @param distanceInMetres how far it is from the point of a search, or null
 */
public record MalagaDrinkingFountain(String name, GeoPoint point, Integer distanceInMetres) {

	MalagaDrinkingFountain withDistanceFrom(GeoPoint from) {
		return new MalagaDrinkingFountain(name, point, point.distanceInMetres(from));
	}

	public double latitude() {
		return point.latitude();
	}

	public double longitude() {
		return point.longitude();
	}

}
