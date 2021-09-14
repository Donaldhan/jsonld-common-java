package foundation.identity.jsonld;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import foundation.identity.jsonld.normalization.NormalizationAlgorithm;

import javax.json.Json;
import javax.json.JsonObject;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;


public class JsonLDObject {

    public static final URI[] DEFAULT_JSONLD_CONTEXTS = new URI[]{};
    public static final String[] DEFAULT_JSONLD_TYPES = new String[]{};
    public static final String DEFAULT_JSONLD_PREDICATE = null;
    public static final String CONTEXT = "@context";
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final ObjectWriter objectWriterDefault = objectMapper.writer();
    private static final ObjectWriter objectWriterPretty = objectMapper.writerWithDefaultPrettyPrinter();

    private final Map<String, Object> jsonObject;

    @JsonCreator
    public JsonLDObject() {
        this(new LinkedHashMap<String, Object>());
    }

    protected JsonLDObject(Map<String, Object> jsonObject) {
        this.jsonObject = jsonObject;
    }

    /*
     * Factory methods
     */

    public static class Builder<B extends Builder<B>> {

        private JsonLDObject base = null;
        private boolean defaultContexts = false;
        private boolean defaultTypes = false;
        private boolean forceContextsArray = false;
        private List<URI> contexts = null;
        private List<String> types = null;
        private URI id = null;

        private boolean isBuilt = false;
        protected JsonLDObject jsonLDObject;

        protected Builder(JsonLDObject jsonLDObject) {
            this.jsonLDObject = jsonLDObject;
        }

        public JsonLDObject build() {

            if (this.isBuilt) {
                throw new IllegalStateException("JSON-LD object has already been built.");
            }
            this.isBuilt = true;

            // add JSON-LD properties
            if (this.base != null) {
                JsonLDUtils.jsonLdAddAll(this.jsonLDObject, this.base.getJsonObject());
            }
            if (this.defaultContexts) {
                List<URI> contexts = new ArrayList<>(JsonLDObject.getDefaultJsonLDContexts(this.jsonLDObject.getClass()));
                if (this.contexts != null) {
                    contexts.addAll(this.contexts);
                }
                this.contexts = contexts;
            }
            if (this.contexts != null) {
                if (this.forceContextsArray) {
                    JsonLDUtils.jsonLdAddList(this.jsonLDObject, CONTEXT, this.contexts.stream().map(JsonLDUtils::uriToString).collect(Collectors.toList()));
                } else {
                    JsonLDUtils.jsonLdAdd(this.jsonLDObject, CONTEXT, this.contexts.stream().map(JsonLDUtils::uriToString).collect(Collectors.toList()));
                }
            }
            if (this.defaultTypes) {
                List<String> types = new ArrayList<>(JsonLDObject.getDefaultJsonLDTypes(this.jsonLDObject.getClass()));
                if (this.types != null) {
                    types.addAll(this.types);
                }
                this.types = types;
            }
            if (this.types != null) {
                JsonLDUtils.jsonLdAddList(this.jsonLDObject, JsonLDKeywords.JSONLD_TERM_TYPE, this.types);
            }
            if (this.id != null) {
                JsonLDUtils.jsonLdAdd(this.jsonLDObject, JsonLDKeywords.JSONLD_TERM_ID, JsonLDUtils.uriToString(this.id));
            }

            return this.jsonLDObject;
        }

        public B base(JsonLDObject base) {
            this.base = base;
            return (B) this;
        }

        public B defaultContexts(boolean defaultContexts) {
            this.defaultContexts = defaultContexts;
            return (B) this;
        }

        public B defaultTypes(boolean defaultTypes) {
            this.defaultTypes = defaultTypes;
            return (B) this;
        }

        public B contexts(List<URI> contexts) {
            this.contexts = contexts;
            return (B) this;
        }

        public B context(URI context) {
            return this.contexts(context == null ? null : Collections.singletonList(context));
        }

        public B types(List<String> types) {
            this.types = types;
            return (B) this;
        }

        public B type(String type) {
            return this.types(type == null ? null : Collections.singletonList(type));
        }

        public B id(URI id) {
            this.id = id;
            return (B) this;
        }
    }

    public static Builder<? extends Builder<?>> builder() {
        return new Builder(new JsonLDObject());
    }

    public static JsonLDObject fromJsonObject(Map<String, Object> jsonObject) {
        return new JsonLDObject(jsonObject);
    }

    public static JsonLDObject fromJson(Reader reader) {
        return new JsonLDObject(readJson(reader));
    }

    public static JsonLDObject fromJson(String json) {
        return new JsonLDObject(readJson(json));
    }

    /*
     * Adding, getting, and removing the JSON-LD object
     */

    public void addToJsonLDObject(JsonLDObject jsonLdObject) {
        String term = getDefaultJsonLDPredicate(this.getClass());
        JsonLDUtils.jsonLdAdd(jsonLdObject, term, this.getJsonObject());
    }

    public static <C extends JsonLDObject> C getFromJsonLDObject(Class<C> cl, JsonLDObject jsonLdObject) {
        String term = getDefaultJsonLDPredicate(cl);
        Map<String, Object> jsonObject = JsonLDUtils.jsonLdGetJsonObject(jsonLdObject.getJsonObject(), term);
        if (jsonObject == null) {
            return null;
        }
        try {
            Method method = cl.getMethod("fromJsonObject", Map.class);
            return (C) method.invoke(null, jsonObject);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException ex) {
            throw new Error(ex);
        }
    }

    public static JsonLDObject getFromJsonLDObject(JsonLDObject jsonLdObject) {
        return getFromJsonLDObject(JsonLDObject.class, jsonLdObject);
    }

    public static <C extends JsonLDObject> void removeFromJsonLdObject(Class<C> cl, JsonLDObject jsonLdObject) {
        String term = getDefaultJsonLDPredicate(cl);
        JsonLDUtils.jsonLdRemove(jsonLdObject, term);
    }

    public static void removeFromJsonLdObject(JsonLDObject jsonLdObject) {
        removeFromJsonLdObject(JsonLDObject.class, jsonLdObject);
    }


    @JsonValue
    public Map<String, Object> getJsonObject() {
        return this.jsonObject;
    }

    @JsonAnySetter
    public void setJsonObjectKeyValue(String key, Object value) {

        this.getJsonObject().put(key, value);
    }

    public final List<String> getTypes() {
        return JsonLDUtils.jsonLdGetStringList(this.getJsonObject(), JsonLDKeywords.JSONLD_TERM_TYPE);
    }

    public final String getType() {
        return JsonLDUtils.jsonLdGetString(this.getJsonObject(), JsonLDKeywords.JSONLD_TERM_TYPE);
    }

    public final boolean isType(String type) {
        return JsonLDUtils.jsonLdContainsString(this.getJsonObject(), JsonLDKeywords.JSONLD_TERM_TYPE, type);
    }

    public final URI getId() {
        return JsonLDUtils.stringToUri(JsonLDUtils.jsonLdGetString(this.getJsonObject(), JsonLDKeywords.JSONLD_TERM_ID));
    }

    /*
     * Reading the JSON-LD object
     */

    protected static Map<String, Object> readJson(Reader reader) {
        try {
            return objectMapper.readValue(reader, Map.class);
        } catch (IOException ex) {
            throw new RuntimeException("Cannot read JSON: " + ex.getMessage(), ex);
        }
    }

    protected static Map<String, Object> readJson(String json) {
        return readJson(new StringReader(json));
    }

    public String toJson(boolean pretty) {

        ObjectWriter objectWriter = pretty ? objectWriterPretty : objectWriterDefault;
        try {
            return objectWriter.writeValueAsString(this.getJsonObject());
        } catch (JsonProcessingException ex) {
            throw new RuntimeException("Cannot write JSON: " + ex.getMessage(), ex);
        }
    }

    public String toJson() {

        return this.toJson(false);
    }


    public synchronized JsonObject toJsonObject() {
        return Json.createObjectBuilder(this.getJsonObject()).build();
    }


    public static <C extends JsonLDObject> List<URI> getDefaultJsonLDContexts(Class<C> cl) {
        try {
            Field field = cl.getField("DEFAULT_JSONLD_CONTEXTS");
            return Arrays.asList((URI[]) field.get(null));
        } catch (IllegalAccessException | NoSuchFieldException ex) {
            throw new Error(ex);
        }
    }

    public static <C extends JsonLDObject> List<String> getDefaultJsonLDTypes(Class<C> cl) {
        try {
            Field field = cl.getField("DEFAULT_JSONLD_TYPES");
            return Arrays.asList((String[]) field.get(null));
        } catch (IllegalAccessException | NoSuchFieldException ex) {
            throw new Error(ex);
        }
    }

    public static <C extends JsonLDObject> String getDefaultJsonLDPredicate(Class<C> cl) {
        try {
            Field field = cl.getField("DEFAULT_JSONLD_PREDICATE");
            return (String) field.get(null);
        } catch (IllegalAccessException | NoSuchFieldException ex) {
            throw new Error(ex);
        }
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
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        JsonLDObject that = (JsonLDObject) o;
        return Objects.equals(this.getJsonObject(), that.getJsonObject());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getJsonObject());
    }
}