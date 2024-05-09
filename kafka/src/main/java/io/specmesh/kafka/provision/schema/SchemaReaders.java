/*
 * Copyright 2023 SpecMesh Contributors (https://github.com/specmesh)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.specmesh.kafka.provision.schema;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.confluent.kafka.schemaregistry.ParsedSchema;
import io.confluent.kafka.schemaregistry.avro.AvroSchema;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.rest.entities.SchemaReference;
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;
import io.confluent.kafka.schemaregistry.json.JsonSchema;
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchema;
import io.specmesh.kafka.provision.Status;
import io.specmesh.kafka.provision.schema.SchemaProvisioner.Schema;
import io.specmesh.kafka.provision.schema.SchemaProvisioner.SchemaProvisioningException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.experimental.Accessors;

/** Readers for reading Schemas */
public final class SchemaReaders {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    static {
        objectMapper.configure(JsonParser.Feature.ALLOW_COMMENTS, true);
    }

    /** defensive */
    private SchemaReaders() {}

    public static final class FileSystemSchemaReader {

        public Collection<ParsedSchema> readLocal(final Path filePath) {
            try {
                final var schemaContent = Files.readString(filePath);
                final var results = new ArrayList<ParsedSchema>();

                final String filename =
                        Optional.ofNullable(filePath.getFileName())
                                .map(Objects::toString)
                                .orElse("");
                if (filename.endsWith(".avsc")) {
                    final var refs = resolveReferencesFor(filePath, schemaContent);
                    results.add(
                            new AvroSchema(
                                    schemaContent, refs.references, refs.resolvedReferences, -1));
                } else if (filename.endsWith(".yml")) {
                    results.add(new JsonSchema(schemaContent));
                } else if (filename.endsWith(".proto")) {
                    results.add(new ProtobufSchema(schemaContent));
                } else {
                    throw new UnsupportedOperationException("Unsupported schema file: " + filePath);
                }

                return results;
            } catch (Exception ex) {
                throw new SchemaProvisioningException(
                        "Failed to load: " + filePath + " from: " + filePath.toAbsolutePath(), ex);
            }
        }

        /**
         * Avro schema reference resolution
         *
         * @param filePath
         * @param schemaContent
         * @return
         */
        private SchemaReferences resolveReferencesFor(
                final Path filePath, final String schemaContent) {
            try {
                final SchemaReferences results = new SchemaReferences();
                final var refs = findJsonNodes(objectMapper.readTree(schemaContent), "subject");
                final var parent = filePath.toFile().getParent();
                refs.forEach(ref -> results.add(parent, ref));
                return results;
            } catch (JsonProcessingException e) {
                throw new SchemaProvisioningException(
                        "Cannot resolve SchemaReferences for:" + filePath, e);
            }
        }

        private List<JsonNode> findJsonNodes(final JsonNode node, final String searchFor) {
            if (node.has(searchFor)) {
                return List.of(node);
            }
            // Recursively traverse child nodes
            final var results = new ArrayList<JsonNode>();
            if (node.isArray()) {
                for (JsonNode child : node) {
                    results.addAll(findJsonNodes(child, searchFor));
                }
                return results;
            } else if (node.isObject()) {
                for (Iterator<Map.Entry<String, JsonNode>> it = node.fields(); it.hasNext(); ) {
                    results.addAll(findJsonNodes(it.next().getValue(), searchFor));
                }
            }
            return results;
        }
    }
    /** Read Schemas from registry for given prefix */
    public static final class SrSchemaReader implements SchemaReader {

        private final SchemaRegistryClient client;

        /**
         * defensive
         *
         * @param client - cluster connection
         */
        private SrSchemaReader(final SchemaRegistryClient client) {
            this.client = client;
        }

        /**
         * Read set of schemas for subject
         *
         * @param prefix to filter against
         * @return found acls with status set to READ
         */
        @Override
        public Collection<Schema> read(final String prefix) {

            try {
                final var subjects = client.getAllSubjectsByPrefix(prefix);
                final var schemas =
                        subjects.stream()
                                .collect(
                                        Collectors.toMap(
                                                subject -> subject,
                                                subject -> {
                                                    try {
                                                        return client.getSchemas(
                                                                subject, false, true);
                                                    } catch (IOException | RestClientException e) {
                                                        throw new SchemaProvisioningException(
                                                                "Failed to load schemas", e);
                                                    }
                                                }));

                return schemas.entrySet().stream()
                        .filter(entry -> !entry.getValue().isEmpty())
                        .map(
                                entry ->
                                        Schema.builder()
                                                .subject(entry.getKey())
                                                .type(entry.getValue().get(0).schemaType())
                                                .schemas(
                                                        resolvePayload(
                                                                entry.getValue()
                                                                        .get(0)
                                                                        .schemaType(),
                                                                entry.getValue()
                                                                        .get(0)
                                                                        .canonicalString()))
                                                .state(Status.STATE.READ)
                                                .build())
                        .collect(Collectors.toList());
            } catch (RestClientException | IOException e) {
                throw new SchemaProvisioningException("Failed to read schemas for:" + prefix, e);
            }
        }

        private List<ParsedSchema> resolvePayload(final String type, final String content) {
            return List.of(parsedSchema(type, content));
        }

        private ParsedSchema parsedSchema(final String type, final String payload) {
            if (type.endsWith(".avsc") || type.equals("AVRO")) {
                return new AvroSchema(payload, List.of(), Map.of(), -1);
            }
            if (type.endsWith(".yml") || type.equals("JSON")) {
                return new JsonSchema(payload);
            }
            if (type.endsWith(".proto") || type.equals("PROTOBUF")) {
                return new ProtobufSchema(payload);
            }
            return null;
        }
    }

    /** Read Acls API */
    interface SchemaReader {
        /**
         * read some acls
         *
         * @param prefix to read
         * @return updated status of acls
         */
        Collection<Schema> read(String prefix);
    }

    /**
     * berevity
     *
     * @return builder
     */
    public static SchemaReaderBuilder builder() {
        return SchemaReaderBuilder.builder();
    }

    /** builder */
    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "schema refs")
    public static final class SchemaReaderBuilder {

        private SchemaRegistryClient srClient;

        /** defensive */
        private SchemaReaderBuilder() {}

        /**
         * main builder
         *
         * @return builder
         */
        public static SchemaReaderBuilder builder() {
            return new SchemaReaderBuilder();
        }

        public SchemaReaderBuilder schemaRegistryClient(
                final SchemaRegistryClient schemaRegistryClient) {
            this.srClient = schemaRegistryClient;
            return this;
        }

        /**
         * build it
         *
         * @return the specified reader impl
         */
        public SchemaReader build() {
            return new SrSchemaReader(srClient);
        }
    }

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP",
            justification = "refs passed as param to prevent API pollution")
    @Data
    @Accessors(fluent = true)
    public static class SchemaReferences {
        final List<SchemaReference> references = new ArrayList<>();
        final Map<String, String> resolvedReferences = new HashMap<>();

        public void add(final String path, final JsonNode ref) {
            try {
                references.add(
                        new SchemaReference(
                                ref.get("name").asText(), ref.get("subject").asText(), -1));

                resolvedReferences.put(
                        ref.get("subject").asText(),
                        Files.readString(Path.of(path, ref.get("subject").asText() + ".avsc")));
            } catch (IOException e) {
                throw new SchemaProvisioningException(
                        "Cannot construct AVRO SchemaReference from:" + ref, e);
            }
        }
    }
}
