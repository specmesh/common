plugins {
    `java-library`
}

val kafkaVersion : String by extra
val spotBugsVersion : String by extra
val hamcrestVersion : String by extra
val log4jVersion : String by extra
val testcontainersVersion : String by extra
val lombokVersion : String by extra
val confluentVersion : String by extra


dependencies {

    implementation(project(":parser"))

    api("io.confluent:kafka-schema-registry-client:$confluentVersion")
    api("io.confluent:kafka-json-schema-provider:$confluentVersion")
    api("io.confluent:kafka-avro-serializer:$confluentVersion")
    api("io.confluent:kafka-json-schema-serializer:$confluentVersion")
    api("com.google.protobuf:protobuf-java:3.21.11")
    api("io.confluent:kafka-protobuf-provider:$confluentVersion")
    api("io.confluent:kafka-protobuf-serializer:$confluentVersion")
    api("io.confluent:kafka-streams-protobuf-serde:$confluentVersion")
    api("io.confluent:kafka-streams-avro-serde:$confluentVersion")


    api("org.hamcrest:hamcrest-all:$hamcrestVersion")

    implementation("com.hashicorp:cdktf:0.16.0-pre.3")
    implementation("software.constructs:constructs:10.0.15")
    implementation("com.google.guava:guava:28.0-jre")
    implementation("com.microsoft.terraform:terraform-client:1.0.0")


    implementation("org.apache.kafka:kafka-streams:$kafkaVersion")
    implementation("org.apache.kafka:kafka-clients:$kafkaVersion")
    implementation("commons-io:commons-io:2.11.0")

    testImplementation("org.projectlombok:lombok:1.18.22")


    compileOnly("org.projectlombok:lombok:$lombokVersion")
    annotationProcessor("org.projectlombok:lombok:$lombokVersion")
    testAnnotationProcessor("org.projectlombok:lombok:$lombokVersion")

    implementation("com.fasterxml.jackson.core:jackson-annotations:2.13.4")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.13.4")
    implementation("com.github.spotbugs:spotbugs-annotations:$spotBugsVersion")

    implementation("org.testcontainers:testcontainers:$testcontainersVersion")
    testImplementation("org.testcontainers:junit-jupiter:$testcontainersVersion")
    implementation("org.testcontainers:kafka:$testcontainersVersion")

    testImplementation("org.apache.logging.log4j:log4j-api:$log4jVersion")
    testImplementation("org.apache.logging.log4j:log4j-core:$log4jVersion")
    testImplementation("org.apache.logging.log4j:log4j-slf4j18-impl:$log4jVersion")
}