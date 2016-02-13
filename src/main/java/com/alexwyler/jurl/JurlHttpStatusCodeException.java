package com.alexwyler.jurl;

/**
 * Created by alexwyler on 2/13/16.
 */
public class JurlHttpStatusCodeException extends RuntimeException {

    public Jurl getJurlInstance() {
        return jurlInstance;
    }

    public void setJurlInstance(Jurl jurlInstance) {
        this.jurlInstance = jurlInstance;
    }

    private Jurl jurlInstance;

    public JurlHttpStatusCodeException(Jurl jurlInstance) {
        super(String.format("Received response code %s for %s to %s",
                jurlInstance.getResponseCode(), jurlInstance.getMethod(), jurlInstance.getUrlWithParams()));
        this.jurlInstance = jurlInstance;
    }
}
