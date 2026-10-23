package org.springframework.samples.petclinic.upstream;

/**
 * A point on the map, in degrees of latitude and longitude (WGS84).
 *
 * @param latitude the latitude, such as 36.72 in Málaga
 * @param longitude the longitude, such as -4.42 in Málaga
 */
public record GeoPoint(double latitude, double longitude) {

	private static final double EARTH_RADIUS_IN_METRES = 6_371_000;

	/**
	 * The distance to another point along the surface of the Earth, by the haversine
	 * formula, rounded to the metre.
	 */
	public int distanceInMetres(GeoPoint other) {
		double dLat = Math.toRadians(other.latitude - this.latitude);
		double dLon = Math.toRadians(other.longitude - this.longitude);
		double a = Math.pow(Math.sin(dLat / 2), 2) + Math.cos(Math.toRadians(this.latitude))
				* Math.cos(Math.toRadians(other.latitude)) * Math.pow(Math.sin(dLon / 2), 2);
		return (int) Math.round(2 * EARTH_RADIUS_IN_METRES * Math.asin(Math.sqrt(a)));
	}

}
