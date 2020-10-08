package foundation.identity.jsonld;

import com.apicatalog.jsonld.JsonLd;
import com.apicatalog.jsonld.api.JsonLdError;
import com.apicatalog.jsonld.api.impl.ToRdfApi;
import com.apicatalog.jsonld.document.JsonDocument;
import com.apicatalog.jsonld.http.media.MediaType;
import com.apicatalog.jsonld.lang.Keywords;
import com.apicatalog.jsonld.loader.DocumentLoader;
import com.apicatalog.rdf.RdfDataset;
import com.apicatalog.rdf.io.nquad.NQuadsWriter;
import foundation.identity.jsonld.normalization.NormalizationAlgorithm;

import javax.json.*;
import javax.json.stream.JsonGenerator;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.*;

public class JsonLDObject {

	private DocumentLoader documentLoader;
	private JsonObjectBuilder jsonObjectBuilder;
	private JsonObject jsonObject;

	protected JsonLDObject(DocumentLoader documentLoader) {
		this.documentLoader = documentLoader;
		this.jsonObjectBuilder = Json.createObjectBuilder();
		this.jsonObject = null;
	}

	public JsonLDObject(DocumentLoader documentLoader, JsonObject jsonObject) {
		this.documentLoader = documentLoader;
		this.jsonObjectBuilder = null;
		this.jsonObject = jsonObject;
	}

	protected JsonLDObject() {
		this((DocumentLoader) null);
	}

	public JsonLDObject(JsonObject jsonObject) {
		this((DocumentLoader) null, jsonObject);
	}

	/*
	 * Factory methods
	 */

	public static class Builder<T extends Builder<T, J>, J extends JsonLDObject> {

		private List<String> contexts;
		private String id;
		private List<String> types;

		protected J jsonLDObject;

		private Builder() {
			this((J) new JsonLDObject());
		}

		protected Builder(J jsonLDObject) {
			this.jsonLDObject = jsonLDObject;
		}

		public J build() {

			// add JSON-LD properties
			if (this.contexts != null) JsonLDUtils.jsonLdAddStringList(this.jsonLDObject.getJsonObjectBuilder(), Keywords.CONTEXT, this.contexts);
			if (this.id != null) JsonLDUtils.jsonLdAddString(this.jsonLDObject.getJsonObjectBuilder(), JsonLDKeywords.JSONLD_TERM_ID, this.id);
			if (this.types != null) JsonLDUtils.jsonLdAddStringList(this.jsonLDObject.getJsonObjectBuilder(), JsonLDKeywords.JSONLD_TERM_TYPE, this.types);

			return this.jsonLDObject;
		}

		public T template(J template) {
			JsonLDUtils.jsonLdAddAll(this.jsonLDObject.getJsonObjectBuilder(), template.getJsonObject());
			return (T) this;
		}

		public T remove(String term) {
			JsonLDUtils.jsonLdRemove(this.jsonLDObject.getJsonObjectBuilder(), term);
			return (T) this;
		}

		public T contexts(List<String> contexts) {
			this.contexts = new ArrayList<String> (contexts);
			return (T) this;
		}

		public T context(String context) {
			return this.contexts(Collections.singletonList(context));
		}

		public T id(String id) {
			this.id = id;
			return (T) this;
		}

		public T types(List<String> types) {
			this.types = new ArrayList<String> (types);
			return (T) this;
		}

		public T type(String type) {
			return this.types(Collections.singletonList(type));
		}
	}

	public static Builder builder() {

		return new Builder();
	}

	/*
	 * Serialization
	 */

	public static JsonLDObject fromJson(String json) {
		return fromJson(new StringReader(json));
	}

	public static JsonLDObject fromJson(Reader reader) {
		JsonObject jsonObject = Json.createReader(reader).readObject();
		return new JsonLDObject(jsonObject);
	}

	/*
	 * Getters and setters
	 */

	public DocumentLoader getDocumentLoader() {
		return this.documentLoader;
	}

	public void setDocumentLoader(DocumentLoader documentLoader) {
		this.documentLoader = documentLoader;
	}

	public JsonObjectBuilder getJsonObjectBuilder() {
		return this.jsonObjectBuilder;
	}

	public synchronized JsonObject getJsonObject() {
		if (this.jsonObject != null) return this.jsonObject;
		JsonObject jsonObject = this.jsonObjectBuilder.build();
		this.jsonObjectBuilder = Json.createObjectBuilder();
		JsonLDUtils.jsonLdAddAll(this.jsonObjectBuilder, jsonObject);
		return jsonObject;
	}

	public List<String> getContexts() {
		return JsonLDUtils.jsonLdGetStringList(this.getJsonObject(), Keywords.CONTEXT);
	}

	public final String getId() {
		return JsonLDUtils.jsonLdGetString(this.getJsonObject(), JsonLDKeywords.JSONLD_TERM_ID);
	}

	public final List<String> getTypes() {
		return JsonLDUtils.jsonLdGetStringList(this.getJsonObject(), JsonLDKeywords.JSONLD_TERM_TYPE);
	}

	public final String getType() {
		return JsonLDUtils.jsonLdGetString(this.getJsonObject(), JsonLDKeywords.JSONLD_TERM_TYPE);
	}

	public final boolean isType(String type) {
		return JsonLDUtils.jsonLdContainsString(this.getJsonObject(), type);

	}

	/*
	 * Serialization
	 */

	private static final JsonWriterFactory jsonWriterFactory;
	private static final JsonWriterFactory jsonWriterFactoryPretty;

	static {

		Map<String, Object> properties = new HashMap<>(1);
		Map<String, Object> propertiesPretty = new HashMap<>(1);
		propertiesPretty.put(JsonGenerator.PRETTY_PRINTING, true);
		jsonWriterFactory = Json.createWriterFactory(properties);
		jsonWriterFactoryPretty = Json.createWriterFactory(propertiesPretty);
	}

	public RdfDataset toDataset() throws JsonLdError {

		JsonDocument jsonDocument = JsonDocument.of(MediaType.JSON_LD, this.getJsonObject());
		ToRdfApi toRdfApi = JsonLd.toRdf(jsonDocument);
		if (this.getDocumentLoader() != null) toRdfApi.loader(this.getDocumentLoader());
		toRdfApi.ordered(true);
		return toRdfApi.get();
	}

	public String toNQuads() throws JsonLdError, IOException {

		RdfDataset rdfDataset = this.toDataset();
		StringWriter stringWriter = new StringWriter();
		NQuadsWriter nQuadsWriter = new NQuadsWriter(stringWriter);
		nQuadsWriter.write(rdfDataset);
		return stringWriter.toString();
	}

	public String normalize(NormalizationAlgorithm.Version version) throws JsonLdError, IOException {

		RdfDataset rdfDataset = this.toDataset();
		return new NormalizationAlgorithm(version).main(rdfDataset);
	}

	public String toJson(boolean pretty) {

		JsonWriterFactory jsonWriterFactory = pretty ? JsonLDObject.jsonWriterFactoryPretty : JsonLDObject.jsonWriterFactory;
		StringWriter stringWriter = new StringWriter();
		JsonWriter jsonWriter = jsonWriterFactory.createWriter(stringWriter);
		jsonWriter.writeObject(this.getJsonObject());
		jsonWriter.close();
		return stringWriter.toString();
	}

	public String toJson() {

		return this.toJson(false);
	}

	/*
	 * Object methods
	 */

	@Override
	public String toString() {
		return this.toJson(false);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		JsonLDObject that = (JsonLDObject) o;
		return Objects.equals(this.getJsonObject(), that.getJsonObject());
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.getJsonObject());
	}
}