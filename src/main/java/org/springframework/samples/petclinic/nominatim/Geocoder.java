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
		return locate(owner.getAddress(), owner.getCity());
	}

	/**
	 * The point of an address in the given city, or empty when Nominatim cannot find it.
	 */
	public Optional<GeoPoint> point(String address, String city) {
		return locate(address, city).map((place) -> new GeoPoint(place.latitude(), place.longitude()));
	}

	private Optional<GeoPlace> locate(String address, String city) {
		String query = address.strip() + ", " + city.strip() + ", España";
		return places.computeIfAbsent(query, (text) -> search(text, 1).stream().findFirst());
	}

	List<GeoPlace> search(String text, int limit) {
		return call(() -> api.search(text, FORMAT, limit, SPAIN)).stream().map(GeoPlace::of).toList();
	}

	GeoPlace reverse(double latitude, double longitude) {
		NominatimApi.Place place = call(() -> api.reverse(latitude, longitude, FORMAT));
		return (place != null && place.latitude() != null) ? GeoPlace.of(place) : null;
	}

	private synchronized <T> T call(Supplier<T> call) {
		long wait = PAUSE.toMillis() - Duration.ofNanos(System.nanoTime() - lastCall).toMillis();
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
			lastCall = System.nanoTime();
		}
	}

}
