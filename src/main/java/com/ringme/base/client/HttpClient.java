package com.ringme.base.client;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.Map;

public interface HttpClient {
    <T> T get(String url,
              Map<String, String> headers,
              Class<T> responseType);

    <T> T get(String url,
              Map<String, String> headers,
              ParameterizedTypeReference<T> responseType);

    <T> T post(String url,
               Object body,
               Map<String, String> headers,
               Class<T> responseType);

    <T> T post(String url,
               Object body,
               Map<String, String> headers,
               ParameterizedTypeReference<T> responseType);

    <T> T exchange(String url,
                   HttpMethod method,
                   Object body,
                   Map<String, String> headers,
                   Class<T> responseType);

    <T> T exchange(String url,
                   HttpMethod method,
                   Object body,
                   Map<String, String> headers,
                   ParameterizedTypeReference<T> responseType);

    <T> ResponseEntity<T> exchangeRaw(String url,
                                      HttpMethod method,
                                      Object body,
                                      Map<String, String> headers,
                                      Class<T> responseType);

    <T> ResponseEntity<T> exchangeRaw(String url,
                                      HttpMethod method,
                                      Object body,
                                      Map<String, String> headers,
                                      ParameterizedTypeReference<T> responseType);
}
