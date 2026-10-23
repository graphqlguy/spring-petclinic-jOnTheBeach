package org.springframework.samples.petclinic.malaga;

import org.springframework.samples.petclinic.upstream.GeoPoint;

/**
 * A bus of the EMT, the city buses of Málaga, where it was at its last report.
 *
 * @param busId the number of the bus
 * @param line the line it runs, such as 1 or C1
 * @param direction 1 for the outbound trip, 2 for the return
 * @param lastStopId the code of the stop it last left
 * @param updatedAt when the bus last reported, in the local time of Málaga
 * @param point where it was
 */
public record MalagaBus(String busId, String line, Integer direction, String lastStopId, String updatedAt,
		GeoPoint point) {

	public double latitude() {
		return point.latitude();
	}

	public double longitude() {
		return point.longitude();
	}

}
