package dev.diyar68.slotify_backend;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SlotifyBackendApplication {

    @Bean
    public Clock applicationClock(@Value("${slotify.time-zone:Europe/Berlin}") String timeZone) {
        return Clock.system(ZoneId.of(timeZone));
    }

	public static void main(String[] args) {
		SpringApplication.run(SlotifyBackendApplication.class, args);
	}

}
