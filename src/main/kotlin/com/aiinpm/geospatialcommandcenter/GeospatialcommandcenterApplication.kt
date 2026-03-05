package com.aiinpm.geospatialcommandcenter

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

@SpringBootApplication
@EnableScheduling
class GeospatialcommandcenterApplication

fun main(args: Array<String>) {
    runApplication<GeospatialcommandcenterApplication>(*args)
}

/** Serve the Leaflet command-center SPA from the root path. */
@Controller
class SpaController {
    @GetMapping("/", "/map")
    fun index() = "forward:/index.html"
}
