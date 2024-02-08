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

package io.specmesh.cli;

import static picocli.CommandLine.Command;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.specmesh.kafka.Clients;
import io.specmesh.kafka.KafkaApiSpec;
import io.specmesh.kafka.admin.SmAdminClient;
import io.specmesh.kafka.admin.SmAdminClient.ConsumerGroup;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.apache.kafka.clients.admin.NewTopic;
import picocli.CommandLine;
import picocli.CommandLine.Option;

/** Spec consumers stats for those groups consuming topic data */
@Command(
        name = "consumption",
        description = "Given a spec, break down the consumption volume against each of its topic")
@Getter
@Accessors(fluent = true)
@Builder
@SuppressFBWarnings
public class Consumption implements Callable<Integer> {

    private Map<String, ConsumerGroup> state;

    /**
     * Main method
     *
     * @param args args
     */
    public static void main(final String[] args) {
        System.exit(new CommandLine(Consumption.builder().build()).execute(args));
    }

    @Option(
            names = {"-bs", "--bootstrap-server"},
            description = "Kafka bootstrap server url")
    @Builder.Default
    private String brokerUrl = "";

    @Option(
            names = {"-spec", "--spec"},
            description = "specmesh specification file")
    private String spec;

    @Option(
            names = {"-u", "--username"},
            description = "username or api key for the cluster connection")
    private String username;

    @Option(
            names = {"-s", "--secret"},
            description = "secret credential for the cluster connection")
    private String secret;

    @Option(
            names = "-D",
            mapFallbackValue = "",
            description =
                    "Specify Java runtime system properties for Apache Kafka. Note: bulk properties"
                            + " can be set via '-Dconfig.properties=somefile.properties"
                            + " ") // allow -Dkey
    void setProperty(final Map<String, String> props) {
        props.forEach((k, v) -> System.setProperty(k, v));
    }

    @Override
    public Integer call() throws Exception {

        final var client = SmAdminClient.create(Clients.adminClient(brokerUrl, username, secret));

        final var apiSpec = specMeshSpec();
        final var topics =
                apiSpec.listDomainOwnedTopics().stream()
                        .map(NewTopic::name)
                        .collect(Collectors.toList());

        final var results = new TreeMap<String, ConsumerGroup>();

        topics.forEach(
                topic -> {
                    final var groups = client.groupsForTopicPrefix(topic);
                    groups.forEach(group -> results.put(topic, group));
                });

        final var mapper =
                new ObjectMapper()
                        .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
                        .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        System.out.println(mapper.writeValueAsString(results));
        this.state = results;
        return 0;
    }

    /**
     * get processed state
     *
     * @return processed state
     */
    @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "meh")
    public Map<String, ConsumerGroup> state() {
        return state;
    }

    private KafkaApiSpec specMeshSpec() {
        return KafkaApiSpec.loadFromClassPath(spec, Consumption.class.getClassLoader());
    }
}
