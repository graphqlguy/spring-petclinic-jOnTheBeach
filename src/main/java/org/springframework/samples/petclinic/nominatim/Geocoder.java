package org.springframework.samples.petclinic.nominatim;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.upstream.GeoPoint;
import org.springframework.samples.petclinic.upstream.RestUpstreams;
import org.springframework.samples.petclinic.upstream.UpstreamException;
import org.springframework.stereotype.Component;

/**
 * Finds addresses on the map with Nominatim. Each address is looked up once and then
 * kept, and the calls wait for each other so that at most one a second reaches Nominatim,
 * as its usage policy asks.
 */
@Component
public class Geocoder {

	static final String UPSTREAM = "Nominatim, the geocoder of OpenStreetMap";

	private static final String FORMAT = "jsonv2";

	private static final String SPAIN = "es";

	private static final Duration PAUSE = Duration.ofSeconds(1);

	private final NominatimApi api;

	private final Map<String, Optional<GeoPlace>> places = new ConcurrentHashMap<>();

	private long lastCall;

	Geocoder(NominatimApi api) {
		this.api = api;
	}

	/**
	 * The place of an owner's address, or empty when Nominatim cannot find it.
	 */
	public Optional<GeoPlace> locate(Owner owner) {
		String address = owner.getAddress().strip() + ", " + owner.getCity().strip() + ", España";
		return this.places.computeIfAbsent(address, (text) -> search(text, 1).stream().findFirst());
	}

	/**
	 * The point of an owner's address, or empty when Nominatim cannot find it.
	 */
	public Optional<GeoPoint> point(Owner owner) {
		return locate(owner).map((place) -> new GeoPoint(place.latitude(), place.longitude()));
	}

	List<GeoPlace> search(String text, int limit) {
		return call(() -> this.api.search(text, FORMAT, limit, SPAIN)).stream().map(GeoPlace::of).toList();
	}

	GeoPlace reverse(double latitude, double longitude) {
		NominatimApi.Place place = call(() -> this.api.reverse(latitude, longitude, FORMAT));
		return (place != null && place.latitude() != null) ? GeoPlace.of(place) : null;
	}

	private synchronized <T> T call(Supplier<T> call) {
		long wait = PAUSE.toMillis() - Duration.ofNanos(System.nanoTime() - this.lastCall).toMillis();
		try {
			if (wait > 0) {
				Thread.sleep(wait);
			}
			return RestUpstreams.call(UPSTREAM, call);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new UpstreamException(UPSTREAM, ex);
		}
		finally {
			this.lastCall = System.nanoTime();
		}
	}

}
