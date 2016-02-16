package com.alexwyler.jurl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Created by alexwyler on 2/12/16.
 */
public class JurlReadmeExamples {

    public static class EatStreetSigninRequest {
        public String email;
        public String password;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EatStreetUser {
        public String email;
        public String phone;
        public String api_key;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SpotifyArtist {
        public String name;
        public String href;
        public List<String> genres;
    }

    @Test
    public void runExamples() throws ExecutionException, InterruptedException {

        String html = new Jurl()
                .url("https://www.google.com/")
                        // force google to display in spanish
                .param("hl", "es")
                .go()
                .getResponseBody();


        SpotifyArtist artist = new Jurl()
                .url("https://api.spotify.com/v1/artists/147jymD5t0TCXW0DbaXry0")
                .go()
                .getResponseJsonObject(SpotifyArtist.class);

        Map<String, Object> geocodeResults = new Jurl()
                .url("https://maps.googleapis.com/maps/api/geocode/json")
                .param("address", "131 West Wilson Street, Madison WI")
                .go()
                .getResponseJsonMap();

        try {
            SpotifyArtist artist2 = new Jurl()
                    .url("https://api.spotify.com/v1/artists/not-an-artist")
                    .throwOnNon200(true)
                    .go()
                    .getResponseJsonObject(SpotifyArtist.class);
        } catch (JurlHttpStatusCodeException e) {
            Jurl jurlInstance = e.getJurlInstance();
            // ...
        }

        Jurl jurl = new Jurl()
                .url("https://api.spotify.com/v1/artists/not-an-artist")
                .go();

        if (jurl.getResponseCode() == 200) {
            SpotifyArtist artist3 = jurl.getResponseJsonObject(SpotifyArtist.class);
        } else {
            // ...
        }

        EatStreetSigninRequest signinRequest = new EatStreetSigninRequest();
        signinRequest.email = "person@gmail.com";
        signinRequest.password = "hunter2";

        EatStreetUser user = new Jurl()
                .url("https://eatstreet.com/publicapi/v1/signin")
                .method("POST")
                .header("X-Access-Token", "__API_EXPLORER_AUTH_KEY__")
                .bodyJson(signinRequest)
                .go()
                .getResponseJsonObject(EatStreetUser.class);

        Future<Jurl> asyncJurl = new Jurl()
                .url("https://api.spotify.com/v1/artists/147jymD5t0TCXW0DbaXry0")
                .goAsync();

        SpotifyArtist artist4 = asyncJurl.get().getResponseJsonObject(SpotifyArtist.class);

    }

}