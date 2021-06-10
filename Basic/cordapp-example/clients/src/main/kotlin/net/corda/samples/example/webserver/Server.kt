package net.corda.samples.example.webserver

import org.springframework.boot.Banner
import org.springframework.boot.SpringApplication
import org.springframework.boot.WebApplicationType.SERVLET
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer
import org.springframework.boot.builder.SpringApplicationBuilder

/**
 * Our Spring Boot application.
 */
@SpringBootApplication
private open class Server: SpringBootServletInitializer() {
    override fun configure(
        builder: SpringApplicationBuilder
    ): SpringApplicationBuilder {
        return builder.sources(this::class.java)
    }
}

/**
 * Starts our Spring Boot application.
 */
fun main(args: Array<String>) {
    val app = SpringApplication(Server::class.java)
    app.setBannerMode(Banner.Mode.OFF)
    app.webApplicationType = SERVLET
    app.run(*args)
}
