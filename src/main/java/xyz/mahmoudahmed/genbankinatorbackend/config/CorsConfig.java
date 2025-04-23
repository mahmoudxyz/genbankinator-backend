package xyz.mahmoudahmed.genbankconverter.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();

        // Option 1: Allow specific origins with credentials
        config.addAllowedOrigin("http://localhost:3000"); // React dev server
        config.addAllowedOrigin("http://localhost:5000"); // React production build served locally
        // Add other origins as needed

        // Option 2: Use allowedOriginPatterns instead (Spring Boot 2.4.0+)
        // config.addAllowedOriginPattern("*");

        // Option 3: Disable credentials and use wildcard (less secure)
        // config.addAllowedOrigin("*");
        // config.setAllowCredentials(false);

        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        config.setAllowCredentials(true); // Enable credentials (cookies, auth headers)

        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}