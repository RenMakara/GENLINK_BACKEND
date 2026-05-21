package co.istad.makara.itp_configserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

@SpringBootApplication
@EnableConfigServer
public class ItpConfigserverApplication {

	public static void main(String[] args) {
		SpringApplication.run(ItpConfigserverApplication.class, args);
	}

}
