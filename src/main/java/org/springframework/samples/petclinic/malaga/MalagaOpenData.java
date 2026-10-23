package org.springframework.samples.petclinic.malaga;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.samples.petclinic.malaga.MalagaOpenDataApi.BusFeature;
import org.springframework.samples.petclinic.malaga.MalagaOpenDataApi.DogParkProperties;
import org.springframework.samples.petclinic.malaga.MalagaOpenDataApi.Feature;
import org.springframework.samples.petclinic.upstream.GeoPoint;
import org.springframework.samples.petclinic.upstream.RestUpstreams;
import org.springframework.samples.petclinic.upstream.UpstreamException;
import org.springframework.stereotype.Component;

/**
 * Reads the open data of Málaga and keeps the parks and the fountains for an hour, since
 * the city changes them rarely and its portal is slow at times. The buses are read on
 * every call. The portal drops a connection now and then, so a failed read is tried once
 * more.
 */
@Component
class MalagaOpenData {

	static final String UPSTREAM = "The open data portal of the city of Málaga";

	private static final String FOUNTAINS_TABLE = "2513b555-cd58-4a19-a9df-6bcec9bf58fc";

	private static final int MAX_FOUNTAINS = 1000;

	private static final Duration KEEP = Duration.ofHours(1);

	private static final Pattern POINT = Pattern.compile("POINT \\((\\S+) (\\S+)\\)");

	private static final Pattern SQUARE_METRES = Pattern.compile("(\\d+)\\s*m2");

	private final MalagaOpenDataApi api;

	private final Kept<List<MalagaDogPark>> dogParks;

	private final Kept<List<MalagaDrinkingFountain>> fountains;

	MalagaOpenData(MalagaOpenDataApi api) {
		this.api = api;
		this.dogParks = new Kept<>(this::readDogParks);
		this.fountains = new Kept<>(this::readFountains);
	}

	List<MalagaDogPark> dogParks() {
		return dogParks.get();
	}

	List<MalagaDrinkingFountain> fountains() {
		return fountains.get();
	}

	List<MalagaBus> buses() {
		return read(api::buses).stream().map(MalagaOpenData::bus).toList();
	}

	private List<MalagaDogPark> readDogParks() {
		return read(api::dogParks).features().stream().map(MalagaOpenData::dogPark).toList();
	}

	/**
	 * The table repeats many fountains, so each point is kept once.
	 */
	private List<MalagaDrinkingFountain> readFountains() {
		Map<GeoPoint, MalagaDrinkingFountain> byPoint = new LinkedHashMap<>();
		read(() -> api.datastoreSearch(FOUNTAINS_TABLE, MAX_FOUNTAINS)).result().records().forEach((row) -> {
			GeoPoint point = point(String.valueOf(row.get("wkb_geometry")));
			if (point != null) {
				byPoint.putIfAbsent(point,
						new MalagaDrinkingFountain(Objects.toString(row.get("nombre"), null), point, null));
			}
		});
		return List.copyOf(byPoint.values());
	}

	private static <T> T read(Supplier<T> call) {
		try {
			return RestUpstreams.call(UPSTREAM, call);
		}
		catch (UpstreamException ex) {
			return RestUpstreams.call(UPSTREAM, call);
		}
	}

	private static MalagaDogPark dogPark(Feature<DogParkProperties> feature) {
		DogParkProperties park = feature.properties();
		Map<String, String> details = (park.details() != null && !park.details().isEmpty()) ? park.details().get(0)
				: Map.of();
		List<Double> coordinates = feature.geometry().coordinates();
		return new MalagaDogPark(String.valueOf(park.id()), park.name(), strip(park.address()),
				park.name().toLowerCase().startsWith("playa"), squareMetres(details.get("Extension")),
				yes(details.get("Fuente")), yes(details.get("Bi-zona")), strip(park.openingHours()),
				yes(park.accessible()), new GeoPoint(coordinates.get(1), coordinates.get(0)), null);
	}

	private static MalagaBus bus(BusFeature feature) {
		MalagaOpenDataApi.BusProperties bus = feature.properties();
		List<String> coordinates = feature.geometry().coordinates();
		String line = bus.codLinea().endsWith(".0") ? bus.codLinea().substring(0, bus.codLinea().length() - 2)
				: bus.codLinea();
		return new MalagaBus(bus.codBus(), line, Integer.valueOf(bus.sentido()), bus.codParIni(), bus.lastUpdate(),
				new GeoPoint(Double.parseDouble(coordinates.get(1)), Double.parseDouble(coordinates.get(0))));
	}

	static GeoPoint point(String wkt) {
		Matcher matcher = POINT.matcher(wkt);
		return matcher.matches()
				? new GeoPoint(Double.parseDouble(matcher.group(2)), Double.parseDouble(matcher.group(1))) : null;
	}

	private static Integer squareMetres(String text) {
		Matcher matcher = SQUARE_METRES.matcher((text != null) ? text : "");
		return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
	}

	private static Boolean yes(String text) {
		return (text != null) ? text.strip().equalsIgnoreCase("si") : null;
	}

	private static String strip(String text) {
		return (text != null) ? text.strip().replace("\r\n", "\n") : null;
	}

	/**
	 * A value read from the portal and kept for an hour. A failed read is not kept, so
	 * the next question tries again.
	 */
	private static final class Kept<T> {

		private final Supplier<T> read;

		private T value;

		private Instant readAt = Instant.MIN;

		Kept(Supplier<T> read) {
			this.read = read;
		}

		synchronized T get() {
			if (value == null || readAt.plus(KEEP).isBefore(Instant.now())) {
				value = read.get();
				readAt = Instant.now();
			}
			return value;
		}

	}

}
