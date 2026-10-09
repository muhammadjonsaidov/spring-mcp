package io.salesdoctor.spring_mcp;

import io.salesdoctor.spring_mcp.support.AppTime;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class SpringMcpApplication {

	public static void main(String[] args) {
		TimeZone.setDefault(TimeZone.getTimeZone(AppTime.ZONE));
		SpringApplication.run(SpringMcpApplication.class, args);
	}

}
