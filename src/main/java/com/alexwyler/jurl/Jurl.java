package com.alexwyler.jurl;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.NameValuePair;
import org.apache.http.annotation.Contract;
import org.apache.http.annotation.ThreadingBehavior;
import org.apache.http.client.methods.*;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Created by alexwyler on 2/12/16.
 */
public class Jurl {

    public static final String GET = "GET";
    public static final String DELETE = "DELETE";
    public static final String POST = "POST";
    public static final String PUT = "PUT";
    public static final String PATCH = "PATCH";
    public static final String EMPTY = "";

    public static ObjectMapper DEFAULT_OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.USE_LONG_FOR_INTS, true)
            .configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true)
            .configure(JsonParser.Feature.ALLOW_COMMENTS, true)
            .configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true)
            .configure(JsonParser.Feature.ALLOW_NUMERIC_LEADING_ZEROS, true);

    public static XmlMapper DEFAULT_XML_MAPPER = (XmlMapper) new XmlMapper()
            .configure(DeserializationFeature.USE_LONG_FOR_INTS, true);

    public static ExecutorService backgroundExecutor = Executors.newFixedThreadPool(100);

    public static void setBackgroundExecutor(ExecutorService backgroundExecutor) {
        Jurl.backgroundExecutor = backgroundExecutor;
    }

    public static void setDefaultObjectMapper(ObjectMapper defaultObjectMapper) {
        Jurl.DEFAULT_OBJECT_MAPPER = defaultObjectMapper;
    }

    boolean gone;
    URIBuilder builder = new URIBuilder();
    String method = GET;
    URL url = null;
    List<NameValuePair> parameters = new ArrayList<>();
    List<NameValuePair> requestHeaders = new ArrayList<>();
    List<NameValuePair> responseHeaders = new ArrayList<>();
    List<NameValuePair> requestCookies = new ArrayList<>();
    List<HttpCookie> responseCookies = new ArrayList<>();
    String requestBody = EMPTY;
    String responseBody = null;
    int responseCode;
    long timeout = TimeUnit.SECONDS.toMillis(60); // ms
    int maxAttempts = 1;
    long timeBetweenAttempts = 0; // ms
    boolean throwOnNon200 = false;
    boolean followRedirects = true;
    ObjectMapper jacksonObjectMapper = DEFAULT_OBJECT_MAPPER;
    XmlMapper jacksonXmlMapper = DEFAULT_XML_MAPPER;

    /**
     * Returns whether this request type is expected to send a resource in the body.  Namely, if it is PUT, POST, or PATCH.
     */
    public boolean maySendResource() {
        return POST.equals(method) || PUT.equals(method) || PATCH.equals(method);
    }

    public Jurl maxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
        return this;
    }

    public Jurl timeBetweenAttempts(long timeBetweenAttempts) {
        this.timeBetweenAttempts = timeBetweenAttempts;
        return this;
    }

    public Jurl throwOnNon200(boolean throwOnNon200) {
        this.throwOnNon200 = throwOnNon200;
        return this;
    }

    public Jurl url(String urlStr) {
        try {
            return this.url(new URL(urlStr));
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    public Jurl url(URL url) {
        this.url = url;
        try {
            this.builder = new URIBuilder(url.toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    public String getUrl() {
        return this.url.toString();
    }

    public Jurl method(String method) {
        this.method = method.toUpperCase();
        return this;
    }

    public String getMethod() {
        return method;
    }

    public Jurl param(String key, String value) {
        builder.addParameter(key, value);
        return this;
    }

    public Jurl param(String key, char value) {
        return param(key, String.valueOf(value));
    }

    public Jurl param(String key, int value) {
        return param(key, String.valueOf(value));
    }

    public Jurl param(String key, long value) {
        return param(key, String.valueOf(value));
    }

    public Jurl param(String key, float value) {
        return param(key, String.valueOf(value));
    }

    public Jurl param(String key, double value) {
        return param(key, String.valueOf(value));
    }

    public Jurl param(String key, boolean value) {
        return param(key, String.valueOf(value));
    }

    public Jurl params(Map<String, Object> params) {
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            param(entry.getKey(), String.valueOf(entry.getValue()));
        }
        return this;
    }

    public Jurl followRedirects(boolean followRedirects) {
        this.followRedirects = followRedirects;
        return this;
    }

    public Jurl basicHttpAuth(String username, String password) {
        String encoded = Base64.getEncoder().encodeToString((username + ':' + password).getBytes());
        header("Authorization", "Basic " + encoded);
        return this;
    }

    public Jurl header(String key, String value) {
        requestHeaders.add(new BasicNameValuePair(key, value));
        return this;
    }

    public Jurl cookie(String key, String value) {
        requestCookies.add(new BasicNameValuePair(key, value));
        return this;
    }

    public Map<String, List<String>> getRequestCookies() {
        return valuePairToMap(requestCookies);
    }

    public List<NameValuePair> getRequestCookieList() {
        return this.requestCookies;
    }

    public List<String> getRequestCookies(String cookieName) {
        return requestCookies.stream()
                .filter((pair) -> pair.getName().equals(cookieName))
                .map(NameValuePair::getValue)
                .collect(Collectors.toList());
    }

    public String getRequestCookie(String cookieName) {
        return requestCookies.stream()
                .filter((pair) -> pair.getName().equals(cookieName))
                .findFirst()
                .map(NameValuePair::getValue)
                .orElse(null);
    }

    public Map<String, List<String>> getRequestHeaders() {
        return valuePairToMap(requestHeaders);
    }

    public String getRequestBody() {
        return requestBody;
    }

    public List<String> getRequestHeaders(String header) {
        return requestHeaders.stream()
                .filter((pair) -> pair.getName().equals(header))
                .map(NameValuePair::getValue)
                .collect(Collectors.toList());
    }

    public String getRequestHeader(String header) {
        return requestHeaders.stream()
                .filter((pair) -> pair.getName().equals(header))
                .findFirst()
                .map(NameValuePair::getValue)
                .orElseGet(null);
    }

    public ObjectMapper getObjectMapper() {
        return jacksonObjectMapper;
    }

    public String getContentType() {
        return getRequestHeader("Content-Type");
    }

    public Jurl contentType(String contentType) {
        return header("Content-Type", contentType);
    }

    public Jurl timeout(long timeout) {
        this.timeout = timeout;
        return this;
    }

    public Jurl body(String body) {
        this.requestBody = body;
        return this;
    }

    public Jurl bodyJson(Object object) {
        header("Content-Type", "application/json");
        try {
            body(DEFAULT_OBJECT_MAPPER.writeValueAsString(object));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    public URL getUrlWithParams() {
        String urlWithParamsStr = this.url.toString();
        final String query = this.url.getQuery();
        if ((query == null || query.isEmpty()) &&
                GET.equals(method) || (requestBody != null && !requestBody.isEmpty())) {
            String queryString = getQueryString();
            if (!queryString.isEmpty()) {
                urlWithParamsStr += '?' + queryString;
            }
        }
        try {
            return new URL(urlWithParamsStr);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    protected String getQueryString() {
        try {
            final String query = builder.build().getQuery();
            return query != null ? query : EMPTY;
        } catch (URISyntaxException e) {
            return EMPTY;
        }
    }

    protected String getEffectiveRequestBody() {
        if (requestBody == null || (requestBody.isEmpty() && !parameters.isEmpty())) {
            return getQueryString();
        } else {
            return requestBody;
        }
    }

    private boolean hasGone() {
        return gone;
    }

    private void assertGone() {
        if (!gone) {
            throw new RuntimeException("Must call go() first.");
        }
    }

    public <S> S getResponseJsonObject(Class<S> clazz) {
        assertGone();
        if (clazz == null) {
            return null;
        }
        try {
            return jacksonObjectMapper.readValue(responseBody, clazz);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Map<String, Object> getResponseJsonMap() {
        assertGone();
        try {
            return jacksonObjectMapper.readValue(responseBody, new TypeReference<Map<String, Object>>() {
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public <S> S getResponseJsonObject(TypeReference<S> type) {
        assertGone();
        try {
            return jacksonObjectMapper.readValue(responseBody, type);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public <S> List<S> getResponseJsonList(Class<S> clazz) {
        assertGone();
        if (clazz == null) {
            return null;
        }
        CollectionType listType = jacksonObjectMapper.getTypeFactory().constructCollectionType(List.class, clazz);
        try {
            return jacksonObjectMapper.readValue(responseBody, listType);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public <S> S getResponseXmlObject(Class<S> clazz) {
        assertGone();
        if (clazz == null) {
            return null;
        }
        try {
            return jacksonXmlMapper.readValue(responseBody, clazz);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String getResponseBody() {
        assertGone();
        return responseBody;
    }

    public List<HttpCookie> getResponseCookies() {
        assertGone();
        return responseCookies;
    }

    public List<HttpCookie> getResponseCookies(String cookieName) {
        assertGone();
        List<HttpCookie> matched = new ArrayList<>();
        for (HttpCookie cookie : responseCookies) {
            if (cookie.getName().equals(cookieName)) {
                matched.add(cookie);
            }
        }
        return matched;
    }

    public HttpCookie getResponseCookie(String cookieName) {
        List<HttpCookie> matched = getResponseCookies(cookieName);
        return matched.isEmpty() ? null : matched.get(0);
    }

    public Map<String, List<String>> getResponseHeaders() {
        assertGone();
        return valuePairToMap(responseHeaders);
    }

    public List<String> getResponseHeaders(String header) {
        assertGone();
        return responseHeaders.stream()
                .filter((pair) -> pair.getName().equals(header))
                .map(NameValuePair::getValue)
                .collect(Collectors.toList());
    }

    public String getResponseHeader(String header) {
        assertGone();
        return responseHeaders.stream()
                .filter((pair) -> pair.getName().equals(header))
                .findFirst()
                .map(NameValuePair::getValue)
                .orElseGet(null);
    }

    public int getResponseCode() {
        assertGone();
        return responseCode;
    }

    public String toCurl() {
        StringBuilder sb = new StringBuilder();
        sb.append("curl");

        sb.append(" -X ");
        sb.append(getMethod());

        if (followRedirects) {
            sb.append(" -L");
        }

        if (!requestCookies.isEmpty()) {
            sb.append(" --cookie ");
            sb.append(String.format("\"%s\"", getCookieString()));
        }

        Map<String, List<String>> headers = getRequestHeaders();
        if (!headers.isEmpty()) {
            for (String headerName : headers.keySet()) {
                for (String headerValue : headers.get(headerName)) {
                    if (headerValue != null) {
                        sb.append(" -H ");
                        sb.append(String.format("\"%s: %s\"", headerName, headerValue));
                    }
                }
            }
        }

        if (maySendResource()) {
            String body = getEffectiveRequestBody();
            if (body != null && !body.equals(EMPTY)) {
                sb.append(String.format(" --data '%s'", body.replaceAll("'", "\\'")));
            }

            if (getRequestBody() != null) {
                sb.append(String.format(" '%s'", getUrlWithParams()));
            } else {
                sb.append(String.format(" '%s'", getUrl()));
            }
        } else {
            sb.append(String.format(" '%s'", getUrlWithParams()));
        }
        return sb.toString();
    }

    protected void onBeforeGo() {
    }

    protected void onBeforeAttempt() {
    }

    protected void onAfterAttempt() {
    }

    protected void onAfterGo() {
    }

    protected String getCookieString() {
        final StringBuilder cookieString = new StringBuilder();
        for (NameValuePair cookie : requestCookies) {
            cookieString.append(cookie.getName());
            cookieString.append('=');
            cookieString.append(cookie.getValue());
            cookieString.append(';');
        }
        if (cookieString.length() > 1) {
            cookieString.setLength(cookieString.length() - 1);
        }
        return cookieString.toString();
    }

    public Jurl go() {
        onBeforeGo();
        for (int i = 1; i <= maxAttempts; i++) {
            onBeforeAttempt();

            final HttpClientBuilder httpClientBuilder = HttpClientBuilder.create();
            if (followRedirects) {
                httpClientBuilder.setRedirectStrategy(new FollowAllRedirectStrategy());
            }
            final CloseableHttpClient httpClient = httpClientBuilder
                    .build();
            try {
                final HttpUriRequest httpRequest = getRequest();

                for (NameValuePair header : requestHeaders) {
                    httpRequest.addHeader(header.getName(), header.getValue());
                }

                if (!requestCookies.isEmpty()) {
                    httpRequest.setHeader("Cookie", getCookieString());
                }
                httpRequest.setHeader("Accept", "application/json");
                httpRequest.setHeader("Cache-Control", "no-cache");

                if (httpRequest instanceof HttpEntityEnclosingRequest) {
                    final HttpEntityEnclosingRequest entityRequest = (HttpEntityEnclosingRequest) httpRequest;
                    final StringEntity entity = new StringEntity(requestBody);
                    entityRequest.setEntity(entity);
                }

                final CloseableHttpResponse response = httpClient.execute(httpRequest);
                responseCode = response.getStatusLine().getStatusCode();

                for (Header header : response.getAllHeaders()) {
                    responseHeaders.add(new BasicNameValuePair(header.getName(), header.getValue()));

                    if (header.getName().equalsIgnoreCase("Set-Cookie")) {
                    	try {
                    		responseCookies.addAll(HttpCookie.parse(header.getValue()));
                    	} catch (IllegalArgumentException e) {
                    		if (e.getMessage().equals("Invalid cookie name-value pair")) {
                    			List<String> nameValues = Arrays.asList(header.getValue().split(";"));
                    			String newHeader = "";
                    			for (String nameValue : nameValues) {
                    				if (nameValue.contains("=")) {
                    					newHeader += nameValue + ";";
                    				}
                    			}
                    			if (newHeader.length() > 0) {
                    				newHeader = newHeader.substring(0, newHeader.length() - 1);
                    			}
                    			responseCookies.addAll(HttpCookie.parse(newHeader));
                    		} else {
                    			throw e;
                    		}
                    	}
                    }
                }

                HttpEntity responseEntity = response.getEntity();
                if (responseEntity != null) {
                    responseBody = EntityUtils.toString(responseEntity);
                }
                response.close();
                onAfterAttempt();

                boolean retryable = responseCode >= 500 && responseCode < 600;
                if (!retryable) {
                    break;
                }
            } catch (IOException e) {
                if (i == maxAttempts) {
                    throw new RuntimeException(e);
                }
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            } finally {
                try {
                    httpClient.close();
                } catch (IOException e) {
                    // Silently close
                }
            }
            if (timeBetweenAttempts > 0) {
                try {
                    Thread.sleep(timeBetweenAttempts);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        gone = true;
        onAfterGo();
        if ((responseCode < 200 || responseCode >= 300) && throwOnNon200) {
            throw new JurlHttpStatusCodeException(this);
        }
        return this;
    }

    private HttpUriRequest getRequest() throws URISyntaxException {
        final URI uri = builder.build();
        HttpUriRequest request;
        switch (method) {
            case GET:
                request = new HttpGet(uri);
                break;
            case POST:
                request = new HttpPost(uri);
                break;
            case PATCH:
                request = new HttpPatch(uri);
                break;
            case PUT:
                request = new HttpPut(uri);
                break;
            case DELETE:
                request = new HttpDelete(uri);
                break;
            default:
                throw new RuntimeException("Unsupported HTTP Method: " + method);
        }
        return request;
    }

    public Future<Jurl> goAsync() {
        return backgroundExecutor.submit(() -> this.go());
    }

    public Jurl newWithCookies() {
        Jurl jurl = new Jurl();
        for (NameValuePair requestCookie : requestCookies) {
            jurl.cookie(requestCookie.getName(), requestCookie.getValue());
        }

        for (HttpCookie cookie : responseCookies) {
            jurl.cookie(cookie.getName(), cookie.getValue());
        }
        return jurl;
    }

    private Map<String, List<String>> valuePairToMap(List<NameValuePair> pairs) {
        final HashMap<String, List<String>> map = new HashMap<>();
        for (NameValuePair pair : pairs) {
            final String name = pair.getName();
            final String value = pair.getValue();

            List<String> values = map.computeIfAbsent(name, k -> new ArrayList<>());
            values.add(value);
        }
        return map;
    }

    @Contract(threading = ThreadingBehavior.IMMUTABLE)
    private static class FollowAllRedirectStrategy extends DefaultRedirectStrategy {
        @Override
        protected boolean isRedirectable(String method) {
            return true;
        }
    }
}
