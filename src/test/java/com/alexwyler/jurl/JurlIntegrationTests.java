package com.alexwyler.jurl;

import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.*;

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
        Assert.assertTrue(!jurl.getResponse().isEmpty());
        Assert.assertEquals(200, jurl.getResponseCode());
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
            Jurl jurl2 = new Jurl().url("https://eatstreet.com/api/v2/not-an-endpoint");
            jurl2.throwOnNon200(true);
            jurl2.go();
        } catch (JurlHttpStatusCodeException e) {
            thrown = e;
        }

        Assert.assertNotNull(thrown);
        Assert.assertTrue(!thrown.getJurlInstance().getResponse().isEmpty());
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
        Assert.assertTrue(!jurl.getResponse().isEmpty());
    }

    @Test
    public void testAsync() throws ExecutionException, InterruptedException {
        Future<Jurl> future = new Jurl().url("https://eatstreet.com/api/v2/CitiesByState.json").goAsync();
        Jurl jurl = future.get();
        Assert.assertTrue(!jurl.getResponse().isEmpty());
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
        TypeReference<HashMap<String,Object>> typeRef = new TypeReference<HashMap<String,Object>>() {};
        Map<String, Object> responseJsonMap = jurl2.getResponseJsonObject(typeRef);
        Assert.assertNotNull(responseJsonMap);
        Assert.assertEquals(jsessionId, responseJsonMap.get("session_id"));
    }

    @Test
    public void testRequestHeaders() {
        Jurl jurl = new Jurl().url("https://eatstreet.com/publicapi/v1/restaurant/358/menu").go();
        Assert.assertEquals(401, jurl.getResponseCode());
        Jurl jurl2 = new Jurl().url("https://eatstreet.com/publicapi/v1/restaurant/358/menu").header("X-Access-Token", "__API_EXPLORER_AUTH_KEY__").go();
        Assert.assertEquals(200, jurl2.getResponseCode());
        Assert.assertTrue(!jurl2.getResponse().isEmpty());
    }

    @Test
    public void testResponseHeaders() {
        Jurl jurl = new Jurl().url("https://eatstreet.com/").go();
        Assert.assertTrue(!jurl.getResponseHeaders().isEmpty());
        Assert.assertEquals("text/html;charset=utf-8", jurl.getResponseHeader("Content-Type"));
    }
}