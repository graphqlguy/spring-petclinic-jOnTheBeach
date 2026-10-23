package org.springframework.samples.petclinic.malaga;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.registry.ImportHttpServices;

/**
 * The open data of the city of Málaga, at datosabiertos.malaga.eu: the parks for dogs and
 * the buses of the EMT as files the city updates, and the drinking fountains as a table
 * of its CKAN datastore.
 */
@HttpExchange(accept = MediaType.APPLICATION_JSON_VALUE)
interface MalagaOpenDataApi {

	/**
	 * The parks for dogs, as GeoJSON points in WGS84.
	 */
	@GetExchange("/recursos/ambiente/parquesCaninos/da_parquesCaninos-4326.geojson")
	FeatureCollection<DogParkProperties> dogParks();

	/**
	 * The rows of a table of the datastore. The drinking fountains are the table
	 * <code>2513b555-cd58-4a19-a9df-6bcec9bf58fc</code>, whose GeoJSON file is empty.
	 * @param resourceId the id of the table
	 * @param limit the largest number of rows to return
	 */
	@GetExchange("/api/3/action/datastore_search")
	DatastoreAnswer datastoreSearch(@RequestParam("resource_id") String resourceId, @RequestParam int limit);

	/**
	 * Where each bus of the EMT is now. The city sends a list of features, and the
	 * coordinates as text.
	 */
	@GetExchange("/recursos/transporte/EMT/EMTlineasUbicaciones/lineasyubicaciones.geojson")
	List<BusFeature> buses();

	record FeatureCollection<P>(List<Feature<P>> features) {

	}

	record Feature<P>(PointGeometry geometry, P properties) {

	}

	/**
	 * A point, with the longitude first and the latitude second.
	 */
	record PointGeometry(String type, List<Double> coordinates) {

	}

	/**
	 * A park for dogs, as the city describes it.
	 *
	 * @param id the id of the park in the city's register
	 * @param name the name, such as <code>Parque canino San Miguel</code>
	 * @param address the address, in capitals
	 * @param details the fountain, the surface and whether the park has separate zones
	 * for small and large dogs
	 * @param openingHours the opening hours, in Spanish, or null
	 * @param accessible <code>Si</code> when the park is adapted to people with reduced
	 * mobility
	 * @param ownership <code>MUNICIPAL</code> for the parks of the city
	 */
	record DogParkProperties(@JsonProperty("ID") int id, @JsonProperty("NOMBRE") String name,
			@JsonProperty("DIRECCION") String address, @JsonProperty("INFOESP") List<Map<String, String>> details,
			@JsonProperty("HORARIOS") String openingHours, @JsonProperty("ACCESOPMR") String accessible,
			@JsonProperty("TITULARIDAD") String ownership) {

	}

	record DatastoreAnswer(boolean success, DatastoreResult result) {

	}

	record DatastoreResult(int total, List<Map<String, Object>> records) {

	}

	/**
	 * A bus of the EMT. The service sends the same values at the top of the feature and
	 * in its properties.
	 *
	 * @param geometry where the bus is, with the coordinates as text
	 * @param properties the bus, its line, its direction, the stop it last left, and when
	 * it last reported
	 */
	record BusFeature(BusGeometry geometry, BusProperties properties) {

	}

	record BusGeometry(String type, List<String> coordinates) {

	}

	record BusProperties(String codBus, String codLinea, String sentido, String codParIni,
			@JsonProperty("last_update") String lastUpdate) {

	}

	/**
	 * Registers the interface as an HTTP service in the group <code>malaga</code>, whose
	 * base URL and timeouts come from <code>spring.http.serviceclient.malaga.*</code>.
	 */
	@Configuration(proxyBeanMethods = false)
	@ImportHttpServices(group = "malaga", types = MalagaOpenDataApi.class)
	class Registration {

	}

}
