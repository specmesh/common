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

package io.specmesh.kafka;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.iterableWithSize;

import io.specmesh.apiparser.AsyncApiParser;
import io.specmesh.apiparser.model.ApiSpec;
import io.specmesh.apiparser.model.SchemaInfo;
import java.util.List;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.acl.AclBinding;
import org.junit.jupiter.api.Test;

public class KafkaAPISpecTest {
    final KafkaApiSpec apiSpec = new KafkaApiSpec(getAPISpecFromResource());

    @Test
    public void shouldListAppOwnedTopics() {
        final List<NewTopic> newTopics = apiSpec.listDomainOwnedTopics();
        assertThat(newTopics.size(), is(3));
        // For adminClient.createTopics()
    }

    @Test
    public void shouldGenerateACLsSoDomainOwnersCanWrite() {
        final List<AclBinding> acls = apiSpec.listACLsForDomainOwnedTopics();

        assertThat(acls, iterableWithSize(6));

        assertThat(
                "Protected ACL was not created",
                acls.get(0).toString(),
                is(
                        "(pattern=ResourcePattern(resourceType=TOPIC, "
                                + "name=london.hammersmith.olympia.bigdatalondon._protected.retail.subway.food.purchase, "
                                + "patternType=PREFIXED), entry=(principal=User:.some.other.domain.root, host=*, operation=READ, "
                                + "permissionType=ALLOW))"));
        // For adminClient.createAcls(acls);
    }

    @Test
    public void shouldGetSchemaInfoForOwnedTopics() {
        final List<NewTopic> newTopics = apiSpec.listDomainOwnedTopics();
        final SchemaInfo schemaInfo = apiSpec.schemaInfoForTopic(newTopics.get(0).name());
        assertThat(schemaInfo.schemaIdLocation(), is("header"));
        // For adminClient.createTopics()
    }

    private ApiSpec getAPISpecFromResource() {
        try {
            return new AsyncApiParser()
                    .loadResource(
                            getClass()
                                    .getClassLoader()
                                    .getResourceAsStream("bigdatalondon-api.yaml"));
        } catch (Throwable t) {
            throw new RuntimeException("Failed to load test resource", t);
        }
    }
}
