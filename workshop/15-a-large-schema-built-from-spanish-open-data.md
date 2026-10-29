# Chapter 15: A large schema built from Spanish open data

> **User story** · As a vet in Málaga, I want to ask the assistant about medicines, mosquitoes, dog parks and the neighbourhood of an owner, so that the answers come from official sources while I stay in the clinic's application.

The schema grows from the clinic's 8 root fields to 601, read from 16 public services of Spain, Andalusia and Málaga. Its 605,000 characters come to about 150,000 tokens, and the model on your laptop reads 8,192 tokens at a time. The code is ready on a branch: you switch to it, and ask the assistant questions that it answers with the three tools of chapter 14.

By the end of this chapter, you will:

- Run the application against 16 public services, live or from recorded answers
- Ask questions that the three tools answer from a schema of 150,000 tokens
- Know why a hint for the model goes in the description of a field

## 1. Switch to the checkpoint

Stop the backend. Commit your work of chapter 14, or set it aside with `git stash`, then fetch the branch of this chapter and switch to it:

```bash
git add -A
git commit -m "My chapter 14"
git fetch origin
git switch checkpoint-15-large-schema
```

Start the backend:

```bash
./mvnw spring-boot:run
```

At the first start, GATool embeds the new schema with the embedding model of chapter 14:

```text
INFO ... i.g.b.i.search.EmbeddingSchemaSearch     : GATool is embedding 3839 schema coordinates, which is one call to your embedding model for each of them. The result is cached, so later starts read it.
INFO ... i.g.b.i.search.EmbeddingSchemaSearch     : GATool cached them in /var/folders/.../T/gatool-<you>/vectors/<hash>.vectors.
INFO ... i.g.b.a.GAToolAutoConfiguration          : GATool publishes searchSchema, introspectType, executeGraphql, ranking 3839 schema coordinates with EMBEDDING and returning up to 2000 tokens of results per call.
```

The embedding took 41 seconds on our laptop, and later starts read the vectors from the cache. Every new field calls a service on the internet. On a slow or missing connection, start with the recorded answers of step 3.

## 2. What we integrated with

Each service has a schema file of its own in `src/main/resources/graphql`:

| File | What it reads | Who runs it |
|---|---|---|
| `malaga.graphqls` | Parks for dogs, drinking fountains and city buses of Málaga | City of Málaga |
| `nominatim.graphqls` | The geocoder of OpenStreetMap, for owners' addresses | OpenStreetMap Foundation |
| `catastro.graphqls` | The land registry, over SOAP: parcels, units and streets | Catastro |
| `cimavet.graphqls` | CIMAVet, the register of veterinary medicines | AEMPS |
| `cima.graphqls` | CIMA, the register of medicines for people, which a vet may prescribe when no veterinary medicine fits | AEMPS |
| `mosquitoalert.graphqls` | Reports of mosquitoes, bites and breeding sites | Mosquito Alert (CEAB-CSIC) |
| `junta.graphqls` | 42 datasets of the regional government, such as museums, holidays and the register of tourism businesses | Junta de Andalucía |
| `ctan.graphqls` | Lines, stops, timetables and fares of the regional buses | Transport consortia of Andalusia (CTAN) |
| `fuel.graphqls` | The prices at every filling station | Ministry for the Ecological Transition |
| `ine.graphqls` | Official statistics, such as the population of each municipality | National Statistics Institute (INE) |
| `boe.graphqls` | Consolidated laws and the daily official gazette | Boletín Oficial del Estado |
| `datosgob.graphqls` | The national catalogue of open data | datos.gob.es |
| `malagacatalog.graphqls` | The catalogue of open data of Málaga | City of Málaga |
| `ree.graphqls` | Demand, generation and prices of electricity | Red Eléctrica (REData) |
| `idee.graphqls` | Addresses, land use, rivers and transport networks of Spain | National Cartographic System (IDEE) |
| `ign.graphqls` | Administrative units, place names, towns and geodetic stations | National Geographic Institute (IGN) |

Together they add 593 root fields to the clinic's 8. We counted about 150,000 tokens with the `cl100k_base` encoding of [tiktoken](https://github.com/openai/tiktoken), about 18 times the 8,192 tokens that chapter 12 gave `granite4.1:8b`, and one `searchSchema` call returns at most 2,000 of them.

## 3. Without wifi

Stop the backend and start it with the `mock` profile:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=mock
```

```text
INFO ... o.s.s.p.u.MockUpstreamConfiguration      : External services are mocked with recorded responses on port 9091
```

A [WireMock](https://wiremock.org) server inside the application answers in place of the 16 services, from 10 MB of answers in `src/main/resources/mock-upstreams`, recorded on 22 and 23 October 2026. `application-mock.properties` points every client at it. A call with the arguments of a recording gets that recording. A call with other arguments gets the first recording of its field, which can belong to other arguments: an address outside the recordings finds the point of Calle Larios 12, for example.

The tests start the application with this profile, so they run without the internet.

## 4. Ask the assistant

The system prompt of chapter 14 names the clinic's data alone. In this chapter, its first line says where the clinic is, and three lines tell the model about the public services:

`src/main/java/org/springframework/samples/petclinic/chat/ChatController.java` (excerpt)

```java
		String system = """
				You are the assistant of a veterinary clinic in Málaga, Spain. Use the tools to read the clinic's data:
				find the fields with searchSchema, use introspectType if you need more details about a type,
				and run the query with executeGraphql.
				Some fields, such as an owner's telephone, need a signed-in staff member, so when a tool answers
				Forbidden, ask the user to sign in.
				The same API reads public services of Spain, Andalusia and Málaga, such as veterinary medicines, mosquito
				reports, dog parks, the land registry, statistics, and fuel and electricity prices. For a question about them,
				write a query in the same way.
				Today is %s. Answer briefly."""
			.formatted(LocalDate.now().format(DAY));
```

The system prompt reaches the assistant alone. An MCP client's model reads the server's **instructions**, which the server sends when the client connects. GATool's default instructions say how to use the three tools, without the topics of the API, so a model in an MCP client could decide that a question about medicines is outside a clinic's server. The checkpoint replaces them with a text that names the topics, and tells the model to search the schema first:

`src/main/resources/application.properties` (excerpt)

```properties
# What the model of an MCP client reads when it connects. This text replaces GATool's, so it names
# the topics of the API and then says how to use the three tools. Spring Boot reads this file as
# ISO-8859-1, so the value writes the á of Málaga as \u00e1.
spring.ai.mcp.server.instructions=This server answers questions about a veterinary clinic in M\u00e1laga, Spain, and about \
  public data on pets, animals and daily life in Spain, Andalusia and M\u00e1laga. \
  The API holds the clinic's owners with their addresses, their pets and visits, and its vets, and for an owner who \
  lives in M\u00e1laga, the dog parks and drinking fountains nearest to their home, with the distance to each. \
  It also reads public services: the dog parks, drinking fountains and city buses of M\u00e1laga; veterinary medicines \
  (CIMAVet) and medicines for people (CIMA); mosquito reports (Mosquito Alert); the land registry (Catastro); datasets \
  of the Junta de Andaluc\u00eda, such as museums and holidays; regional buses (CTAN); fuel prices; statistics (INE); \
  laws and the official gazette (BOE); the open data catalogues of Spain and M\u00e1laga; electricity demand and prices \
  (Red El\u00e9ctrica); addresses, places and maps (IDEE, IGN); and the OpenStreetMap geocoder, which turns an address \
  into latitude and longitude. \
  For a question on any of these topics, search the schema before you decide the API cannot answer it. \
  Find the fields with searchSchema, use introspectType if you need more details about a type, and run the query \
  with executeGraphql. Some fields, such as an owner's telephone, need a signed-in staff member.
```

A search result shows the description of a field, and its arguments by name and type alone, such as `cimaVetMedicines(name: String, activeIngredient: String, speciesId: Int, ...)`. The descriptions of the arguments stay out of the result, so a hint that the model needs goes in the description of the field:

`src/main/resources/graphql/cimavet.graphqls` (excerpt)

```graphql
    """
    Veterinary medicines authorized in Spain, 25 to a page, filtered by name, active ingredient, species, ATC code,
    laboratory, or whether they are on the market or are antibiotics. Give at least one filter. activeIngredient
    takes the Spanish name in capitals, such as MELOXICAM or AMOXICILINA, and speciesId takes -29 for dogs or -14
    for cats. For example, activeIngredient MELOXICAM with speciesId -29 lists the meloxicam medicines for dogs.
    cimaVetMedicine gives everything about one of them.
    """
```

In the same way, the descriptions of `Owner.nearestDogParks`, `Owner.nearestDrinkingFountains` and `Owner.location` say to reach an owner by name through `owners(filter: {lastName: ...})`, which works without the owner's id. Before these hints, the questions about amoxicillin for cats and about Lucía Navarro's dog parks were right in 0 of our 3 runs each: the model sent species ids of its own, and asked us for the owner's id.

Start the backend without the `mock` profile, open the assistant in the frontend, and ask the questions of these tables. You can ask them from an MCP client as well, which reads the instructions above. We asked each one three times with `granite4.1:8b`, each in a new conversation, against the live services on 26 October 2026, with the Microsoft Build of OpenJDK 21, for the reason in the setup's note on JDKs. Each answer took between 4 seconds and 3 minutes. Each table starts with the questions the model answered best.

About pets:

| Question | Service | Right answers in our 3 runs |
|---|---|---|
| Are there beaches for dogs in Málaga? | City of Málaga | 3 of 3, with Playa canina Arroyo Totalán, the city's one beach for dogs |
| Which dog parks are nearest to Lucía Navarro's home, and how far are they? | City of Málaga, Nominatim | 3 of 3, starting with Parque canino San Miguel, 1,236 metres away |
| What has the Junta de Andalucía published recently about pets? | Junta de Andalucía | 3 of 3, with the register of assistance dogs, published on 14 March 2026 |
| Which open datasets about dogs does the city of Málaga publish? | City of Málaga | 3 of 3, with `parques-caninos`, its one dataset about dogs |
| Have tiger mosquitoes been reported within 20 km of Málaga this year? | Mosquito Alert | 3 of 3 said yes, for this year |
| Are any veterinary medicines short in Spain right now? | CIMAVet | 3 of 3 said yes, and named packs among the 35 that are short |
| Which veterinary medicines with amoxicillin are authorised for cats in Spain? | CIMAVet | 3 of 3 listed medicines among the 32 |
| Which veterinary medicines with meloxicam are authorised for dogs in Spain? | CIMAVet | 3 of 3 listed medicines from the first page of 25. The register holds 28, and the answers left that number out. |
| Which dog parks in Málaga have a drinking fountain? | City of Málaga | 2 of 3 named all 16. The third named 12. |
| Is there a drinking fountain near Lucía Navarro's home for a walk with her dog? | City of Málaga, Nominatim | 2 of 3, with Plaza de la Merced, 167 metres away. The third listed the clinic's owners. |
| Which Spanish law protects the welfare of animals? | BOE | 2 of 3, with Ley 7/2023. The third reached the limit of 40 calls to `executeGraphql`. |
| Which rules of the Junta de Andalucía apply to pets? | Junta de Andalucía | 1 of 3 listed them. The other two ended with errors, or with a query for us to run. |

Other services:

| Question | Service | Right answers in our 3 runs |
|---|---|---|
| What is the population of the city of Málaga, according to the INE? | INE | 3 of 3, with 599,063 people |
| Where is the cheapest diesel in Torremolinos right now? | Fuel prices | 2 of 3, with 1.839 € a litre. The third searched Málaga. |
| Which local holidays does the city of Málaga have in 2026? | Junta de Andalucía | 2 of 3, with 19 August and 8 September. The third gave 8 September alone. |
| ¿Qué museos hay en la ciudad de Málaga? | Junta de Andalucía | 1 of 3. Two answers added museums of other towns, such as Rincón de la Victoria. Two of the three answered in Spanish. |
| What does the land registry say about the home of the owner Antonio Ruiz? Use the point of his address. | Catastro, Nominatim | 1 of 3, with the parcel 3148502UF7634N. The second answered about the owner's telephone, and the third said that the API lacks the land registry. |
| What was the price of electricity on the Spanish market yesterday? | Red Eléctrica | 0 of 3. Each answer gave the price of the first hour as the price of the day: the spot price of 215.80 € per MWh twice, and the regulated price of 261.99 once. The spot prices of that day averaged 111.88. |

The model finds these fields with `searchSchema`, and reads the schema 2,000 tokens at a time. Earlier rounds of these questions led to changes in the API, described in *When something fails* below.

## If you have time

These sections show how the fields were built, and what the application does when a service or the model fails.

### How the fields were built

The fields come in three kinds, by how much code stands behind them.

#### Hand-written: an HTTP interface for Málaga and Nominatim

The parks for dogs, the drinking fountains, the buses and the geocoder have Java code of their own. `MalagaOpenDataApi` declares the calls to the city's portal as an HTTP interface, and Spring creates the client from the base URL in `application.properties`:

`src/main/java/org/springframework/samples/petclinic/malaga/MalagaOpenDataApi.java` (excerpt)

```java
@HttpExchange(accept = MediaType.APPLICATION_JSON_VALUE)
interface MalagaOpenDataApi {

	@GetExchange("/recursos/ambiente/parquesCaninos/da_parquesCaninos-4326.geojson")
	FeatureCollection<DogParkProperties> dogParks();

	@GetExchange("/api/3/action/datastore_search")
	DatastoreAnswer datastoreSearch(@RequestParam("resource_id") String resourceId, @RequestParam int limit);

	@Configuration(proxyBeanMethods = false)
	@ImportHttpServices(group = "malaga", types = MalagaOpenDataApi.class)
	class Registration {

	}

}
```

`src/main/resources/application.properties` (excerpt)

```properties
spring.http.serviceclient.malaga.base-url=https://datosabiertos.malaga.eu
spring.http.serviceclient.malaga.connect-timeout=${petclinic.upstream.connect-timeout}
spring.http.serviceclient.malaga.read-timeout=${petclinic.upstream.read-timeout}
```

`MalagaGraphQLController` adds two fields to `Owner`. The owner's address goes to Nominatim once, the parks come from a copy the application keeps for an hour, and Java computes the distances:

`src/main/java/org/springframework/samples/petclinic/malaga/MalagaGraphQLController.java` (excerpt)

```java
	@SchemaMapping(typeName = "Owner")
	List<MalagaDogPark> nearestDogParks(Owner owner, @Argument int first) {
		return homeInMalaga(owner).map((home) -> nearestDogParks(home, first)).orElse(List.of());
	}
```

One field joins the clinic's data with two services, so a model asks for `nearestDogParks` and gets the answer in one call. This is the lesson of `visitCount` in chapter 14's exercise, with services in place of SQL.

#### SOAP: the Catastro

The Catastro, the land registry, serves its street directory and its coordinates over SOAP, described by two WSDL files. `catastro/operations.json` holds one entry for each root field, with the SOAP action and the elements of the request:

`src/main/resources/catastro/operations.json` (excerpt)

```json
{
 "field": "catastroProperty",
 "service": "callejero",
 "action": "http://tempuri.org/OVCServWeb/OVCCallejero/Consulta_DNPRC",
 "parts": [
  {"element": "Provincia", "argument": "province", "value": ""},
  {"element": "Municipio", "argument": "municipality", "value": ""},
  {"element": "RefCat", "argument": "reference", "value": ""}
 ]
}
```

`CatastroConfiguration` sends each request with the `WebServiceTemplate` of Spring Web Services, and `XmlMaps` reads the answer into nested maps, the shape a JSON answer has. A field such as `CatastroUnit.yearBuilt` reads the path `debi/ant` of the answer, and converts it to an integer.

#### Operation tables for thirteen services

For the other thirteen services, each root field is one entry in `<service>/operations.json`, and one class, `RestOperationFetcher`, serves them all: it builds the URL from the arguments, calls the service, and returns its answer. This is the entry of `juntaMuseums`:

`src/main/resources/junta/operations.json` (excerpt)

```json
{
 "field": "juntaMuseums",
 "path": "/museums/search",
 "arguments": [
  {"name": "province", "parameter": "province", "in": "query", "list": false,
   "values": {"ANY": "-", "MALAGA": "Málaga", "SEVILLA": "Sevilla", "...": "..."}},
  {"name": "municipality", "parameter": "municipality", "in": "query", "list": false},
  {"name": "kind", "parameter": "unit_type", "in": "query", "list": false}
 ],
 "fixed": {"order_by": "name", "mode": "ASC", "format": "json"},
 "paging": {"style": "limit", "start": null, "size": "size"}
}
```

The enum value `MALAGA` travels as `Málaga`, the text the Junta expects, and the fixed parameters go with every call. The same file maps the fields whose names differ from the keys of the answers, and converts their values:

```json
"JuntaMuseum": {
 "kind": {"key": "unit_type"},
 "latitude": {"key": "latitude", "conversion": "decimal-comma"},
 "openingHours": {"key": "opening_hours"}
}
```

The Junta writes the latitude as `"36,7213"`, and the field returns `36.7213`. `ValueConversion` holds the conversions the services need: a decimal comma, a yes or no in Spanish, a date in milliseconds, a list sent as a single object, and a few more.

A Python script wrote the fourteen tables, the Catastro's included, their schema files and the recorded answers of step 3. For each service, it read the OpenAPI document or the help page, called the service with sample arguments, and wrote a type for what came back. It reported every key of a recorded answer without a field, and every field without a key in the answers, and we fixed each report before writing the files. The English names and descriptions are written by hand. The script stays outside the repository, and its output is what you have.

### Run the fields

Open GraphiQL at http://localhost:8080/graphiql and run:

```graphql
{
  owners(filter: { lastName: "Navarro" }) {
    content {
      firstName
      address
      location { latitude longitude }
      nearestDogParks(first: 2) { name distanceInMetres }
      nearestDrinkingFountains(first: 1) { distanceInMetres }
    }
  }
}
```

```json
{
  "data": {
    "owners": {
      "content": [
        {
          "firstName": "Lucía",
          "address": "Calle Victoria 15",
          "location": { "latitude": 36.7240254, "longitude": -4.4161421 },
          "nearestDogParks": [
            { "name": "Parque canino San Miguel", "distanceInMetres": 1246 },
            { "name": "Parque canino Parque de los Angeles", "distanceInMetres": 1851 }
          ],
          "nearestDrinkingFountains": [ { "distanceInMetres": 154 } ]
        }
      ]
    }
  }
}
```

When the city's portal is down, as it was in one of our runs, the owner and the point still come back, and each field of the portal comes back as `null` with an error:

```json
{
  "errors": [
    {
      "message": "The open data portal of the city of Málaga is unavailable",
      "path": ["owners", "content", 0, "nearestDogParks"],
      "extensions": { "upstream": "The open data portal of the city of Málaga", "classification": "UPSTREAM_UNAVAILABLE" }
    },
    ...
```

Two more, a medicine register over REST and the land registry over SOAP:

```graphql
{
  cimaVetMedicines(activeIngredient: "MELOXICAM", speciesId: -29, marketed: true) {
    totalCount
    medicines { name }
  }
  catastroParcelAt(latitude: 36.7188122, longitude: -4.4216645) {
    parcels { reference address }
  }
  catastroProperty(reference: "3148502UF7634N0001BU") {
    unit { use surfaceSquareMetres yearBuilt }
  }
}
```

```json
{
  "data": {
    "cimaVetMedicines": {
      "totalCount": 28,
      "medicines": [
        { "name": "ANIMELOXAN 1,5 mg/ml SUSPENSION ORAL PARA PERROS" },
        { "name": "LOXITAB 1 mg COMPRIMIDOS PARA PERROS" },
        ...
      ]
    },
    "catastroParcelAt": {
      "parcels": [ { "reference": "3148502UF7634N", "address": "CL MARQUES DE LARIOS 12 MALAGA (MÁLAGA)" } ]
    },
    "catastroProperty": {
      "unit": { "use": "Comercial", "surfaceSquareMetres": 612, "yearBuilt": 1974 }
    }
  }
}
```

The register returns its medicines 25 to a page, and `totalCount` gives the number across all pages. The log shows each call to a service with its URL, what came back and how long it took:

```text
INFO ... o.s.s.petclinic.upstream.RestUpstreams   : REST GET https://nominatim.openstreetmap.org/search?q=Calle%20Victoria%2015%2C%20M%C3%A1laga%2C%20Espa%C3%B1a&format=jsonv2&limit=1&countrycodes=es answered 200 (134 ms)
INFO ... o.s.s.petclinic.upstream.RestUpstreams   : REST GET https://datosabiertos.malaga.eu/recursos/ambiente/parquesCaninos/da_parquesCaninos-4326.geojson answered 200 (448 ms)
INFO ... o.s.s.p.upstream.RestOperationFetcher    : REST GET https://cimavet.aemps.es/cimavet/rest/medicamentos?practiv1=MELOXICAM&idespecie=-29&comerc=1&pagina=1 returned 25 of 28 results (774 ms)
INFO ... o.s.s.p.upstream.SoapOperationTable      : SOAP http://tempuri.org/OVCServWeb/OVCCoordenadas/Consulta_RCCOOR [EPSG:4326, -4.4216645, 36.7188122] returned [consulta_coordenadas] (978 ms)
INFO ... o.s.s.p.upstream.SoapOperationTable      : SOAP http://tempuri.org/OVCServWeb/OVCCallejero/Consulta_DNPRC [, , 3148502UF7634N0001BU] returned [consulta_dnp] (202 ms)
```

The services change their data every day, so your numbers can differ from ours.

### When something fails

Public services fail in ways that a database on your laptop cannot. These are the failures we met while building the chapter, and what the application does about each:

| What happened | Service | What the application does |
|---|---|---|
| The portal drops connections and answers with empty bodies, several times a day | City of Málaga | `MalagaOpenData` retries once and keeps the parks and fountains for an hour. A field that still fails returns an error that names the portal. |
| An address that sends many requests in a row gets `403 Forbidden` for a while | Red Eléctrica | Nothing in the code. We recorded its answers three seconds apart. |
| The firewall turns away a request whose `User-Agent` does not name an application, with `400 Bad Request` | Catastro | Every client sends `petclinic.upstream.user-agent`, which names the workshop's repository. |
| The original host, `sedeaplicaciones.minetur.gob.es`, offers only `TLS_RSA` cipher suites, which JDK 17.0.19, 21.0.10, 25 and later turn off | Fuel prices | The client calls `energia.serviciosmin.gob.es`, which serves the same API with current cipher suites. |
| A search without results answers `400 Bad request` | Junta de Andalucía | The description of each search field says so. |
| A search of land cover took 30 seconds | IDEE | Its client waits 60 seconds, `petclinic.upstream.idee.read-timeout`. |
| A single feature by its id answers with its whole geometry, 92 MB for a river basin | IDEE | Five large collections have their search field alone, without a field by id. |

The model fails in its own ways. Each row is a failure we saw in an earlier round of step 4, and the change it led to:

| What the model did | Service | What the API does now |
|---|---|---|
| Sent `3` as the id of diesel, which is Gasolina 98 E5 | Fuel prices | The fuel is an enum, `FuelKind`, whose values carry names such as `DIESEL_A`. The operation table turns each one into the ministry's id. |
| Sent `2026-10-22` as the start and the end, and got *The requested data is currently unavailable* | Red Eléctrica | The arguments are of type `Date`, and the operation table writes them as `2026-10-22T00:00` and `2026-10-22T23:59`, the form the service takes. |
| Sent `Málaga` to a dataset that matches `MÁLAGA` alone, got the regional holidays without the local ones, and answered that Málaga lacks local holidays, or listed regional holidays as local | Junta de Andalucía | The operation table marks the argument with `"upperCase": true`, and the fetcher writes the value in capitals. |
| Sent a kind of museum the Junta does not know, got `422 Unprocessable Content`, which the application reported as the Junta being unavailable, and answered with dog beaches | Junta de Andalucía | A `422` comes back as a `BAD_REQUEST` error with the service's reason, which lists the kinds it takes. In the second round, the model sent `MUSEO`, read that list, and sent `Museo`. |
| Called the tiger mosquito, taxon 112, *Culex quinquefasciatus* | Mosquito Alert | The description of `taxonId` names 112 as the tiger mosquito (*Aedes albopictus*). |
| Sent the province as `28` (Madrid's code) and the street as `CALLE` and `Larios`, where the Catastro takes `MALAGA`, `CL` and `MARQUES DE LARIOS`. The Catastro reported each error inside a `200` answer, in a field the model did not select. | Catastro | The field returns a GraphQL error with the Catastro's message and a hint, such as *LA PROVINCIA NO EXISTE. catastroProvinces lists the provinces as the Catastro writes them, such as MALAGA*. The street type also takes the words `CALLE`, `AVENIDA` and a few more. |

Some failures stay with the model. It took the first id of an example in a description, Málaga's 4521, for Torremolinos. It searched for "this year" from 2023, although the system prompt gives today's date. It sometimes answered another question: diesel prices for the population of Málaga, and dog beaches for the museums. Asked for "the property at Calle Larios 12 in Málaga", it wrote the street as `Larios` or `Marquís de Larios` through more than 30 tool calls even with the hints, and gave up. The question of step 4 starts from the owner's point, which works without names. We also tried accepting province codes such as `29` in the Catastro's fields, and the model's belief that Málaga is `28` then sent its queries to Madrid without an error, so the fields take names alone.

### The tests

The chapter adds 22 tests: a test class for each kind of field, against the recorded answers, and unit tests for the conversions and the errors of the Catastro. Run them all:

```bash
./mvnw test
```

```text
[INFO] Tests run: 136, Failures: 0, Errors: 0, Skipped: 0
```

`MockUpstreamTests` starts the application once with the `mock` profile, on a free port for WireMock, and each test class extends it. A test can check what the application sent to a service:

`src/test/java/org/springframework/samples/petclinic/junta/JuntaMockUpstreamTests.java` (excerpt)

```java
		// the Junta matches the municipality in capitals alone
		mockUpstreams.verify(getRequestedFor(urlPathEqualTo("/junta/work-calendar/get/search_calendar"))
			.withQueryParam("municipality", equalTo("MÁLAGA")));
```

## Exercise: an address the Catastro understands

Ask the assistant:

```text
What does the land registry say about the property at Calle Larios 12 in Málaga?
```

In our 3 runs, the model found the building once, after 19 tool calls, and the other two runs ended without it, after 18 and 55 calls. Make it answer with a question that names the same building in a way the Catastro takes, without changing the code.

<details>
<summary>Show solution</summary>

Give the address as the Catastro writes it, which the descriptions of `catastroPropertiesAtAddress` show:

```text
What does the land registry say about the building at province MALAGA, municipality MALAGA, street type CL, street MARQUES DE LARIOS, number 12?
```

Or start from a point, which works without names, as a question of step 4 does through the owner who lives there:

```text
What does the land registry say about the home of the owner Antonio Ruiz? Use the point of his address.
```

The first form gave the parcel 3148502UF7634N, or the list of its units, in all 3 of our runs. The second form gave the parcel in 1 of our 3 runs, as the table of step 4 shows. A field that asks the Catastro with the owner's point, such as `Owner.parcel`, would answer it in one call, as `nearestDogParks` does for the parks.

</details>

## Recap

- 16 public services add 593 root fields to the schema, about 150,000 tokens in all, and the model reads at most 2,000 tokens of it per search.
- The `mock` profile answers from recordings, for the tests and for a conference without wifi.
- The server's instructions name the topics of the API, so the model of an MCP client searches the schema before it decides that a question is outside the clinic's server.
- A search result shows the arguments of a field by name and type alone, so a hint that the model needs goes in the description of the field.
- A hand-written field, such as `Owner.nearestDogParks`, joins the clinic's data with services in one call, and an enum, a date type or a value in capitals moves a choice from the model into the API.

This is the last chapter of the workshop.
