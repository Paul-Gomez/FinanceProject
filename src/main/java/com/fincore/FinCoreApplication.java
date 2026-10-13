package com.fincore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

// Sin UserDetailsService: la autenticación es nuestra (JWT), y así Spring no crea un usuario
// "user" con contraseña generada en el log.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class FinCoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(FinCoreApplication.class, args);
    }
}
