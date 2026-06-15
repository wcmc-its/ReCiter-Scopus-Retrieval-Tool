package reciter;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
    servers = {
        // Setting the server URL to the root context path forces the browser to resolve
        // the full URL using the current page's scheme (HTTPS), so springdoc "Try it out"
        // works behind the nginx/ALB TLS terminator instead of emitting an http:// URL.
        @Server(url = "/", description = "Relative context path (Forces HTTPS)")
    }
)
public class OpenApiConfig {}
