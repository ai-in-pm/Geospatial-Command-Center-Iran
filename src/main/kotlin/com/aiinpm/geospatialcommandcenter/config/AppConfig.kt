package com.aiinpm.geospatialcommandcenter.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
@EnableCaching
@EnableScheduling
@EnableConfigurationProperties(GccProperties::class)
class AppConfig : WebMvcConfigurer {

    /** Redirect /graphiql ? self-hosted, CDN-free explorer at /graphiql.html */
    override fun addViewControllers(registry: ViewControllerRegistry) {
        registry.addRedirectViewController("/graphiql", "/graphiql.html")
    }

    @Bean
    fun webClient(): WebClient = WebClient.builder()
        .codecs { configurer ->
            configurer.defaultCodecs().maxInMemorySize(8 * 1024 * 1024)
        }
        .build()
}

