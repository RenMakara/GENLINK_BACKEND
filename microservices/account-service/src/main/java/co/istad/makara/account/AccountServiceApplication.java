package co.istad.makara.account;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class AccountServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(AccountServiceApplication.class, args);
	}

//
//    @Bean
//    CommandLineRunner run(
//            @Value("${service.name}") String name,
//            @Value("${secret.weak-password}") String weakPassword,
//            @Value("${secret.strong-password}") String strongPassword
//    ){
//        return args -> {
//            System.out.println("Name : " +  name);
//            System.out.println("Weak password : "  +  weakPassword);
//            System.out.println("Strong password : "  +  strongPassword);
//        };
//    }

}
