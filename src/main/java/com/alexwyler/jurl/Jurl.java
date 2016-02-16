package com.alexwyler.jurl;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.xml.XmlMapper;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Created by alexwyler on 2/12/16.
 */
public class Jurl {

    public static final String GET = "GET";
    public static final String POST = "POST";

    public static ObjectMapper DEFAULT_OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.USE_LONG_FOR_INTS, true)
            .configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true)
            .configure(JsonParser.Feature.ALLOW_COMMENTS, true)
            .configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true)
            .configure(JsonParser.Feature.ALLOW_NUMERIC_LEADING_ZEROS, true);

    public static final XmlMapper DEFAULT_XML_MAPPER = (XmlMapper) new XmlMapper();

    public static ExecutorService backgroundExecutor = Executors.newFixedThreadPool(100);

    public static void setBackgroundExecutor(ExecutorService backgroundExecutor) {
        Jurl.backgroundExecutor = backgroundExecutor;
    }

    public static void setDefaultObjectMapper(ObjectMapper defaultObjectMapper) {
        Jurl.DEFAULT_OBJECT_MAPPER = defaultObjectMapper;
    }

    boolean gone;
    String method = GET;
    URL url = null;
    Map<String, List<String>> parameters = new HashMap<>();
    Map<String, List<String>> requestHeaders = new HashMap<>();
    Map<String, List<String>> responseHeaders = new HashMap<>();
    Map<String, List<String>> requestCookies = new HashMap<>();
    List<HttpCookie> responseCookies = new ArrayList<>();
    String requestBody = null;
    String responseBody = null;
    int responseCode;
    long timeout = TimeUnit.SECONDS.toMillis(60); // ms
    int maxAttempts = 1;
    long msBetweenAttempts = 0; // ms
    boolean throwOnNon200 = false;
    boolean followRedirects = true;
    ObjectMapper jacksonObjectMapper = DEFAULT_OBJECT_MAPPER;
    XmlMapper jacksonXmlMapper = DEFAULT_XML_MAPPER;

    private static <T, V> void addToMultiMap(Map<T, List<V>> map, T key, V value) {
        List<V> values = map.get(key);
        if (values == null) {
            values = new ArrayList<>();
            map.put(key, values);
        }
        values.add(value);
    }

    public Jurl maxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
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
        return this;
    }

    public Jurl method(String method) {
        this.method = method;
        return this;
    }

    public String getMethod() {
        return method;
    }

    public Jurl param(String key, String value) {
        addToMultiMap(parameters, key, value);
        return this;
    }

    public Jurl param(String key, int value) {
        addToMultiMap(parameters, key, String.valueOf(value));
        return this;
    }

    public Jurl param(String key, double value) {
        addToMultiMap(parameters, key, String.valueOf(value));
        return this;
    }

    public Jurl param(String key, boolean value) {
        addToMultiMap(parameters, key, String.valueOf(value));
        return this;
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
        addToMultiMap(requestHeaders, key, value);
        return this;
    }

    public Jurl cookie(String key, String value) {
        addToMultiMap(requestCookies, key, value);
        return this;
    }

    public Map<String, List<String>> getRequestCookies() {
        return this.requestCookies;
    }

    public Map<String, List<String>> getRequestHeaders() {
        return requestHeaders;
    }

    public String getRequestBody() {
        return requestBody;
    }

    public List<String> getRequestHeaders(String header) {
        List<String> headers = requestHeaders.get(header);
        return headers != null ? headers : new ArrayList<>();
    }

    public String getRequestHeader(String header) {
        List<String> headers = requestHeaders.get(header);
        if (headers == null || headers.isEmpty()) {
            return null;
        } else {
            return headers.get(0);
        }
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
        try {
            body(DEFAULT_OBJECT_MAPPER.writeValueAsString(object));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        this.contentType("application/json");
        return this;
    }

    public URL getUrlWithParams() {
        String urlWithParamsStr = this.url.toString();
        if (GET.equals(method) || (requestBody != null && !requestBody.isEmpty())) {
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
        String queryString = "";
        for (Map.Entry<String, List<String>> entry : parameters.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            for (String value : entry.getValue()) {
                if (!queryString.isEmpty()) {
                    queryString += "&";
                }
                try {
                    queryString += URLEncoder.encode(entry.getKey(), "UTF-8") + "=" +
                            URLEncoder.encode(value, "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        return queryString;
    }

    private void assertGone() {
        if (!gone) {
            throw new RuntimeException("Must call go() first.");
        }
    }

    public <S> S getResponseJsonObject(Class<S> clazz) {
        assertGone();
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
        CollectionType listType = jacksonObjectMapper.getTypeFactory().constructCollectionType(List.class, clazz);
        try {
            return jacksonObjectMapper.readValue(responseBody, listType);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public <S> S getResponseXmlObject(Class<S> clazz) {
        assertGone();
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
        return responseHeaders;
    }

    public List<String> getResponseHeaders(String header) {
        assertGone();
        List<String> headerValues = responseHeaders.get(header);
        return headerValues != null ? headerValues : new ArrayList<>();
    }

    public String getResponseHeader(String header) {
        assertGone();
        List<String> headerValues = responseHeaders.get(header);
        return (headerValues != null && !headerValues.isEmpty()) ? headerValues.get(0) : null;
    }

    public int getResponseCode() {
        assertGone();
        return responseCode;
    }

    protected void onBeforeAttempt() {
    }

    protected void onAfterAttempt() {
    }

    public Jurl go() {
        for (int i = 1; i <= maxAttempts; i++) {
            onBeforeAttempt();
            try {
                URL url = getUrlWithParams();
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setDoInput(true);
                connection.setInstanceFollowRedirects(followRedirects);
                connection.setUseCaches(false);
                connection.setRequestMethod(method);
                connection.setConnectTimeout((int) timeout);
                connection.setReadTimeout((int) timeout);

                for (Map.Entry<String, List<String>> entry : requestHeaders.entrySet()) {
                    for (String headerValue : entry.getValue()) {
                        connection.setRequestProperty(entry.getKey(), headerValue);
                    }
                }

                if (!requestCookies.isEmpty()) {
                    String cookieString = "";
                    for (Map.Entry<String, List<String>> entry : requestCookies.entrySet()) {
                        for (String cookieValue : entry.getValue()) {
                            if (cookieString.length() > 0) {
                                cookieString += "; ";
                            }
                            cookieString += entry.getKey() + '=' + cookieValue;
                        }
                    }
                    connection.setRequestProperty("Cookie", cookieString);
                }

                if (POST.equals(method)) {
                    connection.setRequestProperty("Content-Length", String.valueOf(getQueryString().length()));
                    connection.setRequestProperty("Connection", "keep-alive");
                    connection.setDoOutput(true);

                    if (requestBody == null || (requestBody.isEmpty() && !parameters.isEmpty())) {
                        this.body(getQueryString());
                    }
                    DataOutputStream wr = new DataOutputStream(connection.getOutputStream());
                    if (requestBody != null) {
                        wr.writeBytes(requestBody);
                    }
                    wr.flush();
                    wr.close();
                    connection.disconnect();
                }

                StringBuilder result = new StringBuilder();
                BufferedReader rd = null;
                responseCode = connection.getResponseCode();
                boolean useInputStream = responseCode >= 200 && responseCode < 300;
                if (useInputStream && connection.getInputStream() != null) {
                    rd = new BufferedReader(new InputStreamReader(
                            connection.getInputStream()));

                } else if (connection.getErrorStream() != null) {
                    rd = new BufferedReader(new InputStreamReader(
                            connection.getErrorStream()));
                }

                if (rd != null) {
                    char buffer[] = new char[1024];
                    int charsRead;
                    while ((charsRead = rd.read(buffer)) != -1) {
                        result.append(buffer, 0, charsRead);
                    }
                    rd.close();
                }

                responseHeaders = connection.getHeaderFields();
                List<String> cookieStrings = responseHeaders.get("Set-Cookie");
                if (cookieStrings == null) {
                    cookieStrings = responseHeaders.get("set-cookie");
                }
                if (cookieStrings != null) {
                    for (String cookie : cookieStrings) {
                        if (cookie != null && !cookie.equals("")) {
                            responseCookies.addAll(HttpCookie.parse(cookie));
                        }
                    }
                }

                responseBody = result.toString();
                onAfterAttempt();

                boolean retryable = responseCode >= 500 && responseCode < 600;
                if (!retryable) {
                    break;
                }
            } catch (IOException e) {
                if (i == maxAttempts) {
                    throw new RuntimeException(e);
                }
            }
            if (msBetweenAttempts > 0) {
                try {
                    Thread.sleep(msBetweenAttempts);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        gone = true;
        if ((responseCode < 200 || responseCode >= 300) && throwOnNon200) {
            throw new JurlHttpStatusCodeException(this);
        }
        return this;
    }

    public Future<Jurl> goAsync() {
        return backgroundExecutor.submit(() -> this.go());
    }

    public Jurl newWithCookies() {
        Jurl jurl = new Jurl();
        for (Map.Entry<String, List<String>> entry : requestCookies.entrySet()) {
            for (String cookieValue : entry.getValue()) {
                jurl.cookie(entry.getKey(), cookieValue);
            }
        }

        for (HttpCookie cookie : responseCookies) {
            jurl.cookie(cookie.getName(), cookie.getValue());
        }
        return jurl;
    }
}