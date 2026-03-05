# Read Me First
The following was discovered as part of building this project:

* The JVM level was changed from '17' to '21' as the Kotlin version does not support Java 17 yet.

# Getting Started

### Reference Documentation
For further reference, please consider the following sections:

* [Official Gradle documentation](https://docs.gradle.org)
* [Spring Boot Gradle Plugin Reference Guide](https://docs.spring.io/spring-boot/4.0.3/gradle-plugin)
* [Create an OCI image](https://docs.spring.io/spring-boot/4.0.3/gradle-plugin/packaging-oci-image.html)
* [Coroutines section of the Spring Framework Documentation](https://docs.spring.io/spring-framework/reference/7.0.5/languages/kotlin/coroutines.html)
* [GraalVM Native Image Support](https://docs.spring.io/spring-boot/4.0.3/reference/packaging/native-image/introducing-graalvm-native-images.html)
* [Azure MySQL support](https://aka.ms/spring/msdocs/mysql)
* [Azure PostgreSQL support](https://aka.ms/spring/msdocs/postgresql)
* [Azure Active Directory](https://microsoft.github.io/spring-cloud-azure/current/reference/html/index.html#spring-security-with-azure-active-directory)
* [Azure Cosmos DB](https://microsoft.github.io/spring-cloud-azure/current/reference/html/index.html#spring-data-support)
* [Azure Key Vault](https://microsoft.github.io/spring-cloud-azure/current/reference/html/index.html#secret-management)
* [Azure Storage](https://microsoft.github.io/spring-cloud-azure/current/reference/html/index.html#resource-handling)
* [Spring Cloud Azure developer guide](https://aka.ms/spring/msdocs/developer-guide)
* [Spring Configuration Processor](https://docs.spring.io/spring-boot/4.0.3/specification/configuration-metadata/annotation-processor.html)
* [Spring Data JDBC](https://docs.spring.io/spring-boot/4.0.3/reference/data/sql.html#data.sql.jdbc)
* [Spring Data JPA](https://docs.spring.io/spring-boot/4.0.3/reference/data/sql.html#data.sql.jpa-and-spring-data)
* [Spring Data LDAP](https://docs.spring.io/spring-boot/4.0.3/reference/data/nosql.html#data.nosql.ldap)
* [Spring Data R2DBC](https://docs.spring.io/spring-boot/4.0.3/reference/data/sql.html#data.sql.r2dbc)
* [Rest Repositories](https://docs.spring.io/spring-boot/4.0.3/how-to/data-access.html#howto.data-access.exposing-spring-data-repositories-as-rest)
* [Spring Boot DevTools](https://docs.spring.io/spring-boot/4.0.3/reference/using/devtools.html)
* [Docker Compose Support](https://docs.spring.io/spring-boot/4.0.3/reference/features/dev-services.html#features.dev-services.docker-compose)
* [Flyway Migration](https://docs.spring.io/spring-boot/4.0.3/how-to/data-initialization.html#howto.data-initialization.migration-tool.flyway)
* [Apache Freemarker](https://docs.spring.io/spring-boot/4.0.3/reference/web/servlet.html#web.servlet.spring-mvc.template-engines)
* [Spring for GraphQL](https://docs.spring.io/spring-boot/4.0.3/reference/web/spring-graphql.html)
* [Groovy Templates](https://docs.spring.io/spring-boot/4.0.3/reference/web/servlet.html#web.servlet.spring-mvc.template-engines)
* [Spring HATEOAS](https://docs.spring.io/spring-boot/4.0.3/reference/web/spring-hateoas.html)
* [htmx](https://github.com/wimdeblauwe/htmx-spring-boot)
* [JDBC API](https://docs.spring.io/spring-boot/4.0.3/reference/data/sql.html)
* [Jersey](https://docs.spring.io/spring-boot/4.0.3/reference/web/servlet.html#web.servlet.jersey)
* [JOOQ Access Layer](https://docs.spring.io/spring-boot/4.0.3/reference/data/sql.html#data.sql.jooq)
* [jte](https://jte.gg/)
* [LDAP](https://docs.spring.io/spring-boot/4.0.3/reference/data/nosql.html#data.nosql.ldap)
* [Liquibase Migration](https://docs.spring.io/spring-boot/4.0.3/how-to/data-initialization.html#howto.data-initialization.migration-tool.liquibase)
* [Model Context Protocol Security [Experimental]](https://github.com/spring-ai-community/mcp-security?tab=readme-ov-file#mcp-security)
* [Spring Modulith](https://docs.spring.io/spring-modulith/reference/)
* [Mustache](https://docs.spring.io/spring-boot/4.0.3/reference/web/servlet.html#web.servlet.spring-mvc.template-engines)
* [MyBatis Framework](https://mybatis.org/spring-boot-starter/mybatis-spring-boot-autoconfigure/)
* [Netflix DGS](https://netflix.github.io/dgs/)
* [OAuth2 Authorization Server](https://docs.spring.io/spring-boot/4.0.3/reference/web/spring-security.html#web.security.oauth2.authorization-server)
* [OAuth2 Client](https://docs.spring.io/spring-boot/4.0.3/reference/web/spring-security.html#web.security.oauth2.client)
* [OAuth2 Resource Server](https://docs.spring.io/spring-boot/4.0.3/reference/web/spring-security.html#web.security.oauth2.server)
* [R2DBC API](https://docs.spring.io/spring-boot/4.0.3/reference/data/sql.html#data.sql.r2dbc)
* [Spring Security](https://docs.spring.io/spring-boot/4.0.3/reference/web/spring-security.html)
* [Spring Session for Spring Data Redis](https://docs.spring.io/spring-session/reference/)
* [Spring Session for JDBC](https://docs.spring.io/spring-session/reference/)
* [Anthropic Claude](https://docs.spring.io/spring-ai/reference/api/chat/anthropic-chat.html)
* [Azure OpenAI](https://docs.spring.io/spring-ai/reference/api/chat/azure-openai-chat.html)
* [Amazon Bedrock](https://docs.spring.io/spring-ai/reference/api/bedrock-chat.html)
* [Amazon Bedrock Converse](https://docs.spring.io/spring-ai/reference/api/bedrock-converse.html)
* [Cassandra Chat Memory Repository](https://docs.spring.io/spring-ai/reference/api/chat-memory.html)
* [Azure Cosmos DB Chat Memory Repository](https://docs.spring.io/spring-ai/reference/api/chat-memory.html)
* [In-memory Chat Memory Repository](https://docs.spring.io/spring-ai/reference/api/chat-memory.html)
* [JDBC Chat Memory Repository](https://docs.spring.io/spring-ai/reference/api/chat-memory.html)
* [Neo4j Chat Memory Repository](https://docs.spring.io/spring-ai/reference/api/chat-memory.html)
* [Redis Chat Memory Repository](https://docs.spring.io/spring-ai/reference/api/chat-memory.html)
* [JSoup Document Reader](https://docs.spring.io/spring-ai/reference/api/etl-pipeline.html)
* [Markdown Document Reader](https://docs.spring.io/spring-ai/reference/api/etl-pipeline.html#_markdown)
* [Model Context Protocol Client](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-client-boot-starter-docs.html)
* [Model Context Protocol Server](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-server-boot-starter-docs.html)
* [Mistral AI](https://docs.spring.io/spring-ai/reference/api/chat/mistralai-chat.html)
* [Ollama](https://docs.spring.io/spring-ai/reference/api/chat/ollama-chat.html)
* [OpenAI](https://docs.spring.io/spring-ai/reference/api/chat/openai-chat.html)
* [OpenAI SDK](https://docs.spring.io/spring-ai/reference/api/chat/openai-sdk-chat.html)
* [PDF Document Reader](https://docs.spring.io/spring-ai/reference/api/etl-pipeline.html#_pdf_page)
* [PostgresML](https://docs.spring.io/spring-ai/reference/api/embeddings/postgresml-embeddings.html)
* [Stability AI](https://docs.spring.io/spring-ai/reference/api/image/stabilityai-image.html)
* [Tika Document Reader](https://docs.spring.io/spring-ai/reference/api/etl-pipeline.html#_tika_docx_pptx_html)
* [Transformers (ONNX) Embeddings](https://docs.spring.io/spring-ai/reference/api/embeddings/onnx.html)
* [Azure AI Search](https://docs.spring.io/spring-ai/reference/api/vectordbs/azure.html)
* [Azure Cosmos DB Vector Store](https://docs.spring.io/spring-ai/reference/api/vectordbs/azure-cosmos-db.html)
* [Amazon Bedrock Knowledge Base](https://docs.spring.io/spring-ai/reference/api/vectordbs/bedrock-knowledgebase.html)
* [Apache Cassandra Vector Database](https://docs.spring.io/spring-ai/reference/api/vectordbs/apache-cassandra.html)
* [Chroma Vector Database](https://docs.spring.io/spring-ai/reference/api/vectordbs/chroma.html)
* [Elasticsearch Vector Database](https://docs.spring.io/spring-ai/reference/api/vectordbs/elasticsearch.html)
* [GemFire Vector Database](https://docs.spring.io/spring-ai/reference/api/vectordbs/gemfire.html)
* [MariaDB Vector Database](https://docs.spring.io/spring-ai/reference/api/vectordbs/mariadb.html)
* [Milvus Vector Database](https://docs.spring.io/spring-ai/reference/api/vectordbs/milvus.html)
* [MongoDB Atlas Vector Database](https://docs.spring.io/spring-ai/reference/api/vectordbs/mongodb.html)
* [Neo4j Vector Database](https://docs.spring.io/spring-ai/reference/api/vectordbs/neo4j.html)
* [Oracle Vector Database](https://docs.spring.io/spring-ai/reference/api/vectordbs/oracle.html)
* [PGvector Vector Database](https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html)
* [Pinecone Vector Database](https://docs.spring.io/spring-ai/reference/api/vectordbs/pinecone.html)
* [Qdrant Vector Database](https://docs.spring.io/spring-ai/reference/api/vectordbs/qdrant.html)
* [Redis Search and Query Vector Database](https://docs.spring.io/spring-ai/reference/api/vectordbs/redis.html)
* [S3 Vector Database](https://docs.spring.io/spring-ai/reference/api/vectordbs/s3.html)
* [Typesense Vector Database](https://docs.spring.io/spring-ai/reference/api/vectordbs/typesense.html)
* [Weaviate Vector Database](https://docs.spring.io/spring-ai/reference/api/vectordbs/weaviate.html)
* [Text embedding reference](https://docs.spring.io/spring-ai/reference/api/embeddings/vertexai-embeddings-text.html)
* [Multimodal embedding reference](https://docs.spring.io/spring-ai/reference/api/embeddings/vertexai-embeddings-multimodal.html)
* [Vertex AI Gemini](https://docs.spring.io/spring-ai/reference/api/chat/vertexai-gemini-chat.html)
* [HTTP Client](https://docs.spring.io/spring-boot/4.0.3/reference/io/rest-client.html#io.rest-client.restclient)
* [WebAuthn for Spring Security](https://docs.spring.io/spring-security/reference/servlet/authentication/passkeys.html)
* [Reactive HTTP Client](https://docs.spring.io/spring-boot/4.0.3/reference/io/rest-client.html#io.rest-client.webclient)
* [SpringDoc OpenAPI](https://springdoc.org/)
* [Thymeleaf](https://docs.spring.io/spring-boot/4.0.3/reference/web/servlet.html#web.servlet.spring-mvc.template-engines)
* [Vaadin](https://vaadin.com/docs)
* [Spring Web](https://docs.spring.io/spring-boot/4.0.3/reference/web/servlet.html)
* [Spring Web Services](https://docs.spring.io/spring-boot/4.0.3/reference/io/webservices.html)
* [Spring Reactive Web](https://docs.spring.io/spring-boot/4.0.3/reference/web/reactive.html)

### Guides
The following guides illustrate how to use some features concretely:

* [Securing a Java Web App with the Spring Boot Starter for Azure Active Directory](https://aka.ms/spring/msdocs/aad)
* [How to use Spring Boot Starter with Azure Cosmos DB SQL API](https://aka.ms/spring/msdocs/cosmos)
* [Read Secrets from Azure Key Vault in a Spring Boot Application](https://aka.ms/spring/msdocs/keyvault)
* [Securing Spring Boot Applications with Azure Key Vault Certificates](https://aka.ms/spring/msdocs/keyvault/certificates)
* [How to use the Spring Boot starter for Azure Storage](https://aka.ms/spring/msdocs/storage)
* [Deploying a Spring Boot app to Azure](https://spring.io/guides/gs/spring-boot-for-azure/)
* [Using Spring Data JDBC](https://github.com/spring-projects/spring-data-examples/tree/main/jdbc/basics)
* [Accessing Data with JPA](https://spring.io/guides/gs/accessing-data-jpa/)
* [Accessing data with R2DBC](https://spring.io/guides/gs/accessing-data-r2dbc/)
* [Accessing JPA Data with REST](https://spring.io/guides/gs/accessing-data-rest/)
* [Accessing Neo4j Data with REST](https://spring.io/guides/gs/accessing-neo4j-data-rest/)
* [Accessing MongoDB Data with REST](https://spring.io/guides/gs/accessing-mongodb-data-rest/)
* [Building a GraphQL service](https://spring.io/guides/gs/graphql-server/)
* [Building a Hypermedia-Driven RESTful Web Service](https://spring.io/guides/gs/rest-hateoas/)
* [htmx](https://www.youtube.com/watch?v=j-rfPoXe5aE)
* [Accessing Relational Data using JDBC with Spring](https://spring.io/guides/gs/relational-data-access/)
* [Managing Transactions](https://spring.io/guides/gs/managing-transactions/)
* [MyBatis Quick Start](https://github.com/mybatis/spring-boot-starter/wiki/Quick-Start)
* [Accessing data with MySQL](https://spring.io/guides/gs/accessing-data-mysql/)
* [Securing a Web Application](https://spring.io/guides/gs/securing-web/)
* [Spring Boot and OAuth2](https://spring.io/guides/tutorials/spring-boot-oauth2/)
* [Authenticating a User with LDAP](https://spring.io/guides/gs/authenticating-ldap/)
* [SpringDoc OpenAPI](https://github.com/springdoc/springdoc-openapi-demos/)
* [Handling Form Submission](https://spring.io/guides/gs/handling-form-submission/)
* [Creating CRUD UI with Vaadin](https://spring.io/guides/gs/crud-with-vaadin/)
* [Building a RESTful Web Service](https://spring.io/guides/gs/rest-service/)
* [Serving Web Content with Spring MVC](https://spring.io/guides/gs/serving-web-content/)
* [Building REST services with Spring](https://spring.io/guides/tutorials/rest/)
* [Producing a SOAP web service](https://spring.io/guides/gs/producing-web-service/)
* [Building a Reactive RESTful Web Service](https://spring.io/guides/gs/reactive-rest-service/)

### Additional Links
These additional references should also help you:

* [Gradle Build Scans – insights for your project's build](https://scans.gradle.com#gradle)
* [Configure AOT settings in Build Plugin](https://docs.spring.io/spring-boot/4.0.3/how-to/aot.html)
* [Azure Active Directory Sample](https://aka.ms/spring/samples/latest/aad)
* [Azure Cosmos DB Sample](https://aka.ms/spring/samples/latest/cosmos)
* [Azure Key Vault Sample](https://aka.ms/spring/samples/latest/keyvault)
* [Azure Storage Sample](https://aka.ms/spring/samples/latest/storage)
* [Azure Samples](https://aka.ms/spring/samples)
* [R2DBC Homepage](https://r2dbc.io)
* [R2DBC Homepage](https://r2dbc.io)

## GraphQL code generation with DGS

This project has been configured to use the Netflix DGS Codegen plugin.
This plugin can be used to generate client files for accessing remote GraphQL services.
The default setup assumes that the GraphQL schema file for the remote service is added to the `src/main/resources/graphql-client/` location.

You can learn more about the [plugin configuration options](https://netflix.github.io/dgs/generating-code-from-schema/#configuring-code-generation) and
[how to use the generated types](https://netflix.github.io/dgs/generating-code-from-schema/) to adapt the default setup.


### Docker Compose support
This project contains a Docker Compose file named `compose.yaml`.
In this file, the following services have been defined:

* azurite: [`mcr.microsoft.com/azure-storage/azurite:latest`](https://github.com/Azure/Azurite?tab=readme-ov-file#dockerhub)
* cassandra: [`cassandra:latest`](https://hub.docker.com/_/cassandra)
* chroma: [`chromadb/chroma:latest`](https://hub.docker.com/r/chromadb/chroma)
* elasticsearch: [`docker.elastic.co/elasticsearch/elasticsearch:7.17.10`](https://www.docker.elastic.co/r/elasticsearch)
* mariadb: [`mariadb:latest`](https://hub.docker.com/_/mariadb)
* mongodbatlas: [`mongodb/mongodb-atlas-local:latest`](https://hub.docker.com/r/mongodb/mongodb-atlas-local)
* mysql: [`mysql:latest`](https://hub.docker.com/_/mysql)
* neo4j: [`neo4j:latest`](https://hub.docker.com/_/neo4j)
* ollama: [`ollama/ollama:latest`](https://hub.docker.com/r/ollama/ollama)
* oracle: [`gvenzl/oracle-free:latest`](https://hub.docker.com/r/gvenzl/oracle-free)
* pgvector: [`pgvector/pgvector:pg16`](https://hub.docker.com/r/pgvector/pgvector)
* qdrant: [`qdrant/qdrant:latest`](https://hub.docker.com/r/qdrant/qdrant)
* redis: [`redis/redis-stack:latest`](https://hub.docker.com/r/redis/redis-stack)
* sqlserver: [`mcr.microsoft.com/mssql/server:latest`](https://mcr.microsoft.com/en-us/product/mssql/server/about/)
* typesense: [`typesense/typesense:30.1`](https://hub.docker.com/r/typesense/typesense)
* weaviate: [`semitechnologies/weaviate:latest`](https://hub.docker.com/r/semitechnologies/weaviate)

Please review the tags of the used images and set them to the same as you're running in production.

## GraalVM Native Support

This project has been configured to let you generate either a lightweight container or a native executable.
It is also possible to run your tests in a native image.

### Lightweight Container with Cloud Native Buildpacks
If you're already familiar with Spring Boot container images support, this is the easiest way to get started.
Docker should be installed and configured on your machine prior to creating the image.

To create the image, run the following goal:

```
$ ./gradlew bootBuildImage
```

Then, you can run the app like any other container:

```
$ docker run --rm -p 8080:8080 geospatialcommandcenter:0.0.1-SNAPSHOT
```

### Executable with Native Build Tools
Use this option if you want to explore more options such as running your tests in a native image.
The GraalVM `native-image` compiler should be installed and configured on your machine.

NOTE: GraalVM 25+ is required.

To create the executable, run the following goal:

```
$ ./gradlew nativeCompile
```

Then, you can run the app as follows:
```
$ build/native/nativeCompile/geospatialcommandcenter
```

You can also run your existing tests suite in a native image.
This is an efficient way to validate the compatibility of your application.

To run your existing tests in a native image, run the following goal:

```
$ ./gradlew nativeTest
```

### Gradle Toolchain support

There are some limitations regarding Native Build Tools and Gradle toolchains.
Native Build Tools disable toolchain support by default.
Effectively, native image compilation is done with the JDK used to execute Gradle.
You can read more about [toolchain support in the Native Build Tools here](https://graalvm.github.io/native-build-tools/latest/gradle-plugin.html#configuration-toolchains).

## jte

This project has been configured to use [jte precompiled templates](https://jte.gg/pre-compiling/).

However, to ease development, those are not enabled out of the box.
For production deployments, you should remove

```properties
gg.jte.development-mode=true
```

from the `application.properties` file and set

```properties
gg.jte.use-precompiled-templates=true
```

instead.
For more details, please take a look at [the official documentation](https://jte.gg/spring-boot-starter-4/).

### Deploy to Azure

This project can be deployed to Azure with Maven.

To get started, replace the following placeholder in your `pom.xml` with your specific Azure details:

- `subscriptionId`
- `resourceGroup`
- `appEnvironmentName`
- `region`

Now you can deploy your application:
```bash
./mvnw azure-container-apps:deploy
```

Learn more about [Java on Azure Container Apps](https://learn.microsoft.com/azure/container-apps/java-overview).
