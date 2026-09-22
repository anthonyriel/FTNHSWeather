package edu.ftnhs.weather_manager.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.mockito.Mockito.*;

class WeatherApiClientTests {
    @Test
    void delayedTriggersStillFetchInEveryClockSlot() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        Clock clock = mock(Clock.class);
        when(clock.withZone(any())).thenAnswer(invocation -> Clock.fixed(clock.instant(), invocation.getArgument(0)));
        WeatherApiClient client = new WeatherApiClient(builder.build(), clock);
        expectWeather(server);
        expectWeather(server);
        expectWeather(server);
        // 11:00:26, 11:10:14, 11:20:15 Philippine time: cron delivery varies by seconds.
        for (String time : new String[]{"2026-09-18T03:00:26Z", "2026-09-18T03:10:14Z", "2026-09-18T03:20:15Z"}) {
            when(clock.instant()).thenReturn(Instant.parse(time));
            assertNotNull(client.fetch(false), "Missing scheduled slot at " + time);
            when(clock.instant()).thenReturn(Instant.parse(time).plusSeconds(30));
            assertNull(client.fetch(false), "Duplicate trigger in the same slot");
        }
        server.verify();
    }

    @Test
    void manualFetchDoesNotDelayTheNextClockSlot() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        Clock clock = mock(Clock.class);
        when(clock.withZone(any())).thenAnswer(invocation -> Clock.fixed(clock.instant(), invocation.getArgument(0)));
        WeatherApiClient client = new WeatherApiClient(builder.build(), clock);
        expectWeather(server);
        expectWeather(server);
        when(clock.instant()).thenReturn(Instant.parse("2026-09-18T03:09:50Z"));
        assertNotNull(client.fetch(true));
        assertNull(client.fetch(false));
        when(clock.instant()).thenReturn(Instant.parse("2026-09-18T03:10:14Z"));
        assertNotNull(client.fetch(false));
        server.verify();
    }

    @ParameterizedTest
    @CsvSource({
        "2026-09-16T20:59:59Z,false",
        "2026-09-16T21:00:00Z,true",
        "2026-09-17T09:50:00Z,true",
        "2026-09-17T09:59:59Z,true",
        "2026-09-17T10:00:00Z,false",
        "2026-09-17T16:00:00Z,false"
    })
    void automaticRequestsRespectManilaWindow(String instant, boolean allowed) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WeatherApiClient client = new WeatherApiClient(builder.build(),
                Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
        if (allowed) expectWeather(server);
        assertEquals(allowed, client.isAutomaticFetchWindow());
        assertEquals(allowed, client.fetch(false) != null);
        server.verify();
    }

    @Test
    void manualFetchWorksAtNightAndCachedReadsMakeNoRequests() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WeatherApiClient client = new WeatherApiClient(builder.build(),
                Clock.fixed(Instant.parse("2026-09-17T12:00:00Z"), ZoneOffset.UTC));
        expectWeather(server);
        expectWeather(server);
        var response = client.fetch(true);
        assertNotNull(response);
        assertSame(response, client.getCachedResponse());
        assertNull(client.fetch(false));
        assertSame(response, client.getCachedResponse());
        assertNotNull(client.fetch(true));
        server.verify();
    }

    @Test
    void failedRequestIsThrottledAndCanRetryAfterTenMinutes() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        Clock clock = mock(Clock.class);
        Instant start = Instant.parse("2026-09-17T00:00:00Z");
        when(clock.instant()).thenReturn(start);
        when(clock.withZone(any())).thenAnswer(invocation -> Clock.fixed(clock.instant(), invocation.getArgument(0)));
        WeatherApiClient client = new WeatherApiClient(builder.build(), clock);
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://api.open-meteo.com/")))
                .andRespond(withStatus(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS));
        expectWeather(server);
        assertThrows(org.springframework.web.client.HttpClientErrorException.TooManyRequests.class,
                () -> client.fetch(false));
        assertTrue(client.getLastResult().contains("HTTP 429"));
        assertTrue(client.getLastResult().contains("No forecast cached since startup"));
        when(clock.instant()).thenReturn(start.plusSeconds(599));
        assertNull(client.fetch(false));
        when(clock.instant()).thenReturn(start.plusSeconds(600));
        assertNotNull(client.fetch(false));
        server.verify();
    }

    @Test
    void repeatedAutomaticTriggersDoNotDuplicateRequests() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WeatherApiClient client = new WeatherApiClient(builder.build(),
                Clock.fixed(Instant.parse("2026-09-17T00:00:00Z"), ZoneOffset.UTC));
        expectWeather(server);
        assertNotNull(client.fetch(false));
        assertNull(client.fetch(false));
        server.verify();
    }

    private void expectWeather(MockRestServiceServer server) {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://api.open-meteo.com/v1/forecast?")))
                .andExpect(request -> {
                    var params = org.springframework.web.util.UriComponentsBuilder.fromUri(request.getURI())
                            .build().getQueryParams();
                    assertEquals(6, params.getFirst("current").split(",").length);
                    assertEquals(3, params.getFirst("hourly").split(",").length);
                })
                .andExpect(request -> assertEquals("Asia/Manila",
                        java.net.URLDecoder.decode(org.springframework.web.util.UriComponentsBuilder.fromUri(request.getURI())
                                .build().getQueryParams().getFirst("timezone"), java.nio.charset.StandardCharsets.UTF_8)))
                .andRespond(withSuccess("{\"current\":{\"temperature_2m\":28},\"hourly\":{\"time\":[]}}", MediaType.APPLICATION_JSON));
    }

    @Test
    void apiErrorShowsReasonAndRetainsCachedForecast() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WeatherApiClient client = new WeatherApiClient(builder.build(),
                Clock.fixed(Instant.parse("2026-09-17T00:00:00Z"), ZoneOffset.UTC));
        expectWeather(server);
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://api.open-meteo.com/")))
                .andRespond(withStatus(org.springframework.http.HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":true,\"reason\":\"Invalid timezone\"}"));
        var cached = client.fetch(true);
        assertThrows(org.springframework.web.client.HttpClientErrorException.BadRequest.class,
                () -> client.fetch(true));
        assertSame(cached, client.getCachedResponse());
        assertTrue(client.getLastResult().contains("HTTP 400"));
        assertTrue(client.getLastResult().contains("Invalid timezone"));
        assertTrue(client.getLastResult().contains("Previously fetched forecast retained"));
        server.verify();
    }
}
