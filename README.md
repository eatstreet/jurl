# Jurl
An Easy Java Http Client

## Examples
### `GET`
```java
String html = new Jurl()
        .url("https://www.google.com/")
        // force google to display in spanish
        .param("hl", "es")
        .go()
        .getResponse();
```

### JSON `GET`
Jurl uses Jackson to easily parse json responses
```java
@JsonIgnoreProperties(ignoreUnknown = true)
public static class SpotifyArtist {
    public String name;
    public String href;
    public List<String> genres;
}

SpotifyArtist artist = new Jurl()
        .url("https://api.spotify.com/v1/artists/147jymD5t0TCXW0DbaXry0")
        .go()
        .getResponseJsonObject(SpotifyArtist.class);
```

### `Map<String, Object>` JSON `GET`
It may be expedient to parse JSON responses into `Map<String, Object>`.

```java

Map<String, Object> geocodeResults = new Jurl()
        .url("https://maps.googleapis.com/maps/api/geocode/json")
        .param("address", "131 West Wilson Street, Madison WI")
        .go()
        .getResponseJsonObject(new TypeReference<Map<String, Object>>() {});
```

### Error Handling
If `throwOnNon200(true)` is set, `go()` will throw `JurlHttpStatusCodeException` on non-`200` response codes.

```java
try {
    SpotifyArtist artist = new Jurl()
            .url("https://api.spotify.com/v1/artists/not-an-artist")
            .throwOnNon200(true)
            .go()
            .getResponseJsonObject(SpotifyArtist.class);
} catch (JurlHttpStatusCodeException e) {
    Jurl jurlInstance = e.getJurlInstance();
    // ...
}
```

Otherwise, you can always check the response code use `getResponseCode()`
```java
Jurl jurl3 = new Jurl()
        .url("https://api.spotify.com/v1/artists/not-an-artist")
        .go();

if (jurl.getResponseCode() == 200) {
    SpotifyArtist artist = jurl.getResponseJsonObject(SpotifyArtist.class);
} else {
    // ...
}
```

### JSON `POST`
Jurl also uses Jackson to serialize JSON request bodies.  Note also the calls to `.method()` to designate "POST" and `.header()` to set request headers.

```java
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

```

### Sessions / Preserving Cookies
After a request done, calling `newWithCookies()` will return a new `Jurl` instance with request cookies pre-filled, to preserve session.

## Notes
- Only supports UTF-8 character request and response bodies
- `param()` calls for POST requests will be x-www-form-urlencoded in the body
