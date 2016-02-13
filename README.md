# Jurl
Jurl is a modern Java http client designed to make API integrations simpler.

## Why

The top StackOverflow answer to "How to make an HTTP request in Java" is terrifying: http://stackoverflow.com/a/1359700/2340222. Even Apache's `HttpClient` leaves an uncharacteristic trail of boilerplate code.

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
Jurl uses Jackson to parse json responses.
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

Otherwise, you can always check the response code use `getResponseCode()`.
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

### Asynchronous Usage
Jurl uses Java `Future`s to make requests asynchronously.
```
Future<Jurl> asyncJurl = new Jurl()
        .url("https://api.spotify.com/v1/artists/147jymD5t0TCXW0DbaXry0")
        .goAsync();
        
SpotifyArtist artist = asyncJurl.get().getResponseJsonObject(SpotifyArtist.class);
```

### Preserving Cookies / Session
After a request done, calling `newWithCookies()` will return a new `Jurl` instance with request cookies pre-filled, to preserve session.

## Notes
- Only UTF-8 encoded character request and response bodies are supported.
- `param()` calls for POST requests will be x-www-form-urlencoded in the body.
- `bodyJson()` calls will also set the `Content-Type` header to `application/json`.
