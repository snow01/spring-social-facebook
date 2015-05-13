/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.social.facebook.connect;

import java.util.Collections;

import org.springframework.http.MediaType;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.social.oauth2.AccessGrant;
import org.springframework.social.oauth2.OAuth2Template;
//import org.springframework.social.support.ClientHttpRequestFactorySelector;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

/**
 * Facebook-specific extension of OAuth2Template to use a RestTemplate that recognizes form-encoded responses as "text/plain".
 * Facebook token responses are form-encoded results with a content type of "text/plain", which prevents the FormHttpMessageConverter
 * registered by default from parsing the results.
 * @author Craig Walls
 */
public class FacebookOAuth2Template extends OAuth2Template {

    private static final String AccessTokenUrl = "https://graph.facebook.com/v2.2/oauth/access_token";
    private static final String AuthorizeUrl = "https://www.facebook.com/v2.2/dialog/oauth";

    private final String clientId;
    private final String clientSecret;

	public FacebookOAuth2Template(String clientId, String clientSecret) {
		super(clientId, clientSecret, AuthorizeUrl, AccessTokenUrl);

        this.clientId = clientId;
        this.clientSecret = clientSecret;

        setUseParametersForClientAuthentication(true);
	}

	@Override
	protected RestTemplate createRestTemplate() {
		RestTemplate restTemplate = new RestTemplate(ClientHttpRequestFactorySelector.getRequestFactory());
		FormHttpMessageConverter messageConverter = new FormHttpMessageConverter() {
			public boolean canRead(Class<?> clazz, MediaType mediaType) {
				// always read as x-www-url-formencoded even though Facebook sets contentType to text/plain				
				return true;
			}
		};
		restTemplate.setMessageConverters(Collections.<HttpMessageConverter<?>>singletonList(messageConverter));
		return restTemplate;
	}

    public AccessGrant extendAccess(String refreshToken, String scope, MultiValueMap<String, String> additionalParameters) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<String, String>();
        params.set("client_id", clientId);
        params.set("client_secret", clientSecret);
        params.set("fb_exchange_token", refreshToken);

        if (scope != null) {
            params.set("scope", scope);
        }

        params.set("grant_type", "fb_exchange_token");
        if (additionalParameters != null) {
            params.putAll(additionalParameters);
        }

        return postForAccessGrant(AccessTokenUrl, params);
    }

    @Override
	@SuppressWarnings("unchecked")	
	protected AccessGrant postForAccessGrant(String accessTokenUrl, MultiValueMap<String, String> parameters) {
		MultiValueMap<String, String> response = getRestTemplate().postForObject(accessTokenUrl, parameters, MultiValueMap.class);
		String expires = response.getFirst("expires");
		return new AccessGrant(response.getFirst("access_token"), null, null, expires != null ? Long.valueOf(expires) : null);
	}
}
