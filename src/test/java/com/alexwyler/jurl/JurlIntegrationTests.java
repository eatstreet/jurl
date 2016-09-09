package com.alexwyler.jurl;

import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Created by alexwyler on 2/12/16.
 */
public class JurlIntegrationTests {

    public static class EatStreetState {
        public String name;
        public List<EatStreetCity> cities;
    }

    public static class EatStreetCity {
        public String name;
        public String url;
    }

    public static class EatStreetApiError {
        public boolean error;
        public String errorDetails;
    }

    @Test
    public void testGoodPage() {
        Jurl jurl = new Jurl().url("https://eatstreet.com/").go();
        Assert.assertTrue(!jurl.getResponseBody().isEmpty());
        Assert.assertEquals(200, jurl.getResponseCode());
    }

    @Test
    public void testCurlGet() {
        String curl = new Jurl().url("https://eatstreet.com/").toCurl();
        Assert.assertEquals("curl -X GET -L 'https://eatstreet.com/'", curl);
    }

    @Test
    public void testJsonList() {
        Jurl jurl = new Jurl().url("https://eatstreet.com/api/v2/CitiesByState.json").go();
        Assert.assertNotNull(jurl.getResponseJsonList(EatStreetState.class));
    }

    @Test
    public void testJsonObject() {
        Jurl jurl = new Jurl().url("https://eatstreet.com/api/v2/not-an-endpoint").go();
        Assert.assertNotNull(jurl.getResponseJsonObject(EatStreetApiError.class));
    }

    @Test
    public void testFailNoThrow() {
        Jurl jurl = new Jurl().url("https://eatstreet.com/api/v2/not-an-endpoint");
        jurl.throwOnNon200(false);
        jurl.go();
        Assert.assertEquals(404, jurl.getResponseCode());
    }

    @Test
    public void testFailThrow() {
        JurlHttpStatusCodeException thrown = null;
        try {
            Jurl jurl2 = new Jurl().url("https://eatstreet.com/api/v2/invalid-endpoint");
            jurl2.throwOnNon200(true);
            jurl2.go();
        } catch (JurlHttpStatusCodeException e) {
            thrown = e;
        }

        Assert.assertNotNull(thrown);
        Assert.assertTrue(!thrown.getJurlInstance().getResponseBody().isEmpty());
        Assert.assertEquals(404, thrown.getJurlInstance().getResponseCode());
        Assert.assertNotNull(thrown.getJurlInstance().getResponseJsonObject(EatStreetApiError.class));
    }

    @Test
    public void testNoFollow301() {
        Jurl jurl = new Jurl().url("https://eatstreet.com/madison").followRedirects(false).go();
        Assert.assertEquals(301, jurl.getResponseCode());
        Assert.assertEquals("/madison/home", jurl.getResponseHeader("Location"));
    }

    @Test
    public void testFollow301() {
        Jurl jurl = new Jurl().url("https://eatstreet.com/madison").go();
        Assert.assertEquals(200, jurl.getResponseCode());
        Assert.assertTrue(!jurl.getResponseBody().isEmpty());
    }

    @Test
    public void testAsync() throws ExecutionException, InterruptedException {
        Future<Jurl> future = new Jurl().url("https://eatstreet.com/api/v2/CitiesByState.json").goAsync();
        Jurl jurl = future.get();
        Assert.assertTrue(!jurl.getResponseBody().isEmpty());
        Assert.assertNotNull(jurl.getResponseJsonList(EatStreetState.class));
        Assert.assertEquals(200, jurl.getResponseCode());
        Assert.assertNotNull(jurl.getResponseCookie("JSESSIONID"));
    }

    @Test
    public void testNewWithCookies() {
        Jurl jurl = new Jurl().url("https://eatstreet.com/api/v2/CitiesByState.json").go();
        Assert.assertNotNull(jurl.getResponseCookie("JSESSIONID"));
        String jsessionId = jurl.getResponseCookie("JSESSIONID").getValue();
        Jurl jurl2 = jurl.newWithCookies().url("https://eatstreet.com/ClientConfig.json").go();
        TypeReference<HashMap<String, Object>> typeRef = new TypeReference<HashMap<String, Object>>() {
        };
        Map<String, Object> responseJsonMap = jurl2.getResponseJsonObject(typeRef);
        Assert.assertNotNull(responseJsonMap);
        Assert.assertEquals(jsessionId, responseJsonMap.get("session_id"));
    }

    @Test
    public void testCurlCookies() {
        String curl = new Jurl().url("https://eatstreet.com/api/v2/CitiesByState.json")
                .cookie("test-cookie", "test-value")
                .cookie("test-cookie", "test-value2")
                .cookie("test-cookie3", "test-value4")
                .toCurl();

        Assert.assertEquals("curl -X GET -L --cookie \"test-cookie=test-value;test-cookie=test-value2;test-cookie3=test-value4\" 'https://eatstreet.com/api/v2/CitiesByState.json'", curl);
    }

    @Test
    public void testRequestHeaders() {
        Jurl jurl = new Jurl().url("https://eatstreet.com/publicapi/v1/restaurant/90fd4587554469b1f15b4f2e73e761809f4b4bcca52eedca/menu").go();
        Assert.assertEquals(401, jurl.getResponseCode());
        Jurl jurl2 = new Jurl().url("https://eatstreet.com/publicapi/v1/restaurant/90fd4587554469b1f15b4f2e73e761809f4b4bcca52eedca/menu").header("X-Access-Token", "__API_EXPLORER_AUTH_KEY__").go();
        Assert.assertEquals(200, jurl2.getResponseCode());
        Assert.assertTrue(!jurl2.getResponseBody().isEmpty());
    }

    @Test
    public void testCurlRequestHeaders() {
        String curl = new Jurl().url("https://eatstreet.com/publicapi/v1/restaurant/358/menu").header("X-Access-Token", "__API_EXPLORER_AUTH_KEY__").toCurl();
        Assert.assertEquals("curl -X GET -L -H \"X-Access-Token: __API_EXPLORER_AUTH_KEY__\" 'https://eatstreet.com/publicapi/v1/restaurant/358/menu'", curl);
    }

    @Test
    public void testResponseHeaders() {
        Jurl jurl = new Jurl().url("https://eatstreet.com/").go();
        Assert.assertTrue(!jurl.getResponseHeaders().isEmpty());
        Assert.assertEquals("text/html;charset=utf-8", jurl.getResponseHeader("Content-Type"));
    }

    @Test
    public void testJsonPost() throws IOException {
        JurlReadmeExamples.EatStreetSigninRequest signinRequest = new JurlReadmeExamples.EatStreetSigninRequest();
        signinRequest.email = "person@gmail.com";
        signinRequest.password = "hunter2";

        Jurl jurl = new Jurl();
        JurlReadmeExamples.EatStreetUser user = jurl
                .url("https://eatstreet.com/publicapi/v1/signin")
                .method("POST")
                .header("X-Access-Token", "__API_EXPLORER_AUTH_KEY__")
                .bodyJson(signinRequest)
                .go()
                .getResponseJsonObject(JurlReadmeExamples.EatStreetUser.class);

        Assert.assertNotNull(user);
        Assert.assertEquals(signinRequest.email, user.email);
    }

    @Test
    public void testUnicode() throws IOException {
        JurlReadmeExamples.EatStreetSigninRequest signinRequest = new JurlReadmeExamples.EatStreetSigninRequest();
        signinRequest.email = "person@gmail.com";
        signinRequest.password = "hunter2";

        Jurl jurl = new Jurl();
        String responseBody = jurl
                .url("https://eatstreet.com/publicapi/v1/signin")
                .method("POST")
                .header("X-Access-Token", "__API_EXPLORER_AUTH_KEY__")
                .bodyJson(signinRequest)
                .go()
                .getResponseBody();

        Assert.assertNotNull(responseBody);
        Assert.assertTrue(responseBody.contains("é"));
    }

    @Test
    public void testJsonPatch() throws IOException {
        JurlReadmeExamples.EatStreetSigninRequest signinRequest = new JurlReadmeExamples.EatStreetSigninRequest();
        signinRequest.email = "person@gmail.com";
        signinRequest.password = "hunter2";

        Jurl jurl = new Jurl();
        JurlReadmeExamples.EatStreetUser user = jurl
                .url("https://eatstreet.com/publicapi/v1/signin")
                .method("PATCH")
                .header("X-Access-Token", "__API_EXPLORER_AUTH_KEY__")
                .bodyJson(signinRequest)
                .go()
                .getResponseJsonObject(JurlReadmeExamples.EatStreetUser.class);

        Assert.assertNotNull(user);
        Assert.assertEquals(signinRequest.email, user.email);
    }

    @Test
    public void testCurlJsonPost() {
        JurlReadmeExamples.EatStreetSigninRequest signinRequest = new JurlReadmeExamples.EatStreetSigninRequest();
        signinRequest.email = "person@gmail.com";
        signinRequest.password = "hunter2";

        String curl = new Jurl()
                .url("https://eatstreet.com/publicapi/v1/signin")
                .method("POST")
                .header("X-Access-Token", "__API_EXPLORER_AUTH_KEY__")
                .bodyJson(signinRequest).toCurl();

        Assert.assertEquals("curl -X POST -L -H \"X-Access-Token: __API_EXPLORER_AUTH_KEY__\" -H \"Content-Type: application/json\" --data '{\"email\":\"person@gmail.com\",\"password\":\"hunter2\"}' 'https://eatstreet.com/publicapi/v1/signin'", curl);
    }
}