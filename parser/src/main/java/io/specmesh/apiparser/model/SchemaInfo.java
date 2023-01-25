package io.specmesh.apiparser.model;


import lombok.Value;
import lombok.experimental.Accessors;

/**
 * Pojo representing a schmea
 */
@Value
@Accessors(fluent = true)
public class SchemaInfo {
    private final String schemaRef;
    private final String schemaFormat;
    private final String schemaIdLocation;
    private String schemaIdPayloadLocation;
    private final String contentType;
    private String schemaLookupStrategy;

    /**
     * @param schemaRef
     *            location of schema
     * @param schemaFormat
     *            format of schema
     * @param schemaIdLocation
     *            ???
     * @param schemaIdPayloadLocation
     *            ???
     * @param contentType
     *            content type of schema
     * @param schemaLookupStrategy
     *            schema lookup strategy
     */
    public SchemaInfo(final String schemaRef, final String schemaFormat, final String schemaIdLocation,
            final String schemaIdPayloadLocation, final String contentType, final String schemaLookupStrategy) {
        this.schemaRef = schemaRef;
        this.schemaFormat = schemaFormat;
        this.schemaIdLocation = schemaIdLocation;
        this.schemaIdPayloadLocation = schemaIdPayloadLocation;
        this.contentType = contentType;
        this.schemaLookupStrategy = schemaLookupStrategy;
    }
}
