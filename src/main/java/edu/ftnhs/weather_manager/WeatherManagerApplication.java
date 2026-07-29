package edu.ftnhs.weather_manager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling; 

@SpringBootApplication
@EnableScheduling 
public class WeatherManagerApplication {

    public static void main(String[] args) {
        SpringApplication.run(WeatherManagerApplication.class, args);
    }

}