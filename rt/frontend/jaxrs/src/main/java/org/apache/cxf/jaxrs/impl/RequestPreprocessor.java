/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.cxf.jaxrs.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.apache.cxf.common.util.StringUtils;
import org.apache.cxf.jaxrs.ext.RequestHandler;
import org.apache.cxf.jaxrs.model.ProviderInfo;
import org.apache.cxf.jaxrs.model.wadl.WadlGenerator;
import org.apache.cxf.jaxrs.provider.ProviderFactory;
import org.apache.cxf.jaxrs.utils.HttpUtils;
import org.apache.cxf.message.Message;

public class RequestPreprocessor {
    
    private static final String ACCEPT_QUERY = "_type";
    private static final String CTYPE_QUERY = "_ctype";
    private static final String METHOD_QUERY = "_method";
    private static final String METHOD_HEADER = "X-HTTP-Method-Override";
    
    
    private static final Map<String, String> SHORTCUTS;
    static {
        SHORTCUTS = new HashMap<String, String>();
        SHORTCUTS.put("json", "application/json");
        SHORTCUTS.put("text", "text/*");
        SHORTCUTS.put("xml", "application/xml");
        SHORTCUTS.put("atom", "application/atom+xml");
        SHORTCUTS.put("html", "text/html");
        SHORTCUTS.put("wadl", "application/vnd.sun.wadl+xml");
        // more to come
    }
    
    private Map<Object, Object> languageMappings;
    private Map<Object, Object> extensionMappings;
    
    public RequestPreprocessor() {
        this(null, null);
    }
    
    public RequestPreprocessor(Map<Object, Object> languageMappings,
                           Map<Object, Object> extensionMappings) {
        this.languageMappings =
            languageMappings == null ? Collections.emptyMap() : languageMappings;
        this.extensionMappings = 
            extensionMappings == null ? Collections.emptyMap() : extensionMappings;
    }

    public String preprocess(Message m, UriInfo u) {
        handleExtensionMappings(m, u);
        handleLanguageMappings(m, u);
        
        MultivaluedMap<String, String> queries = u.getQueryParameters();
        handleTypeQuery(m, queries);
        handleCType(m, queries);
        handleMethod(m, queries, new HttpHeadersImpl(m));
        Response r = checkMetadataRequest(m, u);
        if (r != null) {
            m.getExchange().put(Response.class, r);
        }
        return new UriInfoImpl(m, null).getPath();
    }
    
    private void handleLanguageMappings(Message m, UriInfo uriInfo) {
        String path = uriInfo.getPath(false);
        for (Map.Entry<?, ?> entry : languageMappings.entrySet()) {
            if (path.endsWith("." + entry.getKey())) {
                updateAcceptLanguageHeader(m, entry.getValue().toString());
                updatePath(m, path, entry.getKey().toString());
                break;
            }    
        }
    }
    
    private void handleExtensionMappings(Message m, UriInfo uriInfo) {
        String path = uriInfo.getPath(false);
        for (Map.Entry<?, ?> entry : extensionMappings.entrySet()) {
            String key = entry.getKey().toString();
            if (path.endsWith("." + key)) {
                updateAcceptTypeHeader(m, entry.getValue().toString());
                updatePath(m, path, key);
                if ("wadl".equals(key)) {
                    // the path has been updated and Accept was not necessarily set to 
                    // WADL type (xml or json or html - other options)
                    String query = (String)m.get(Message.QUERY_STRING);
                    if (StringUtils.isEmpty(query)) {
                        query = WadlGenerator.WADL_QUERY;
                    } else if (!query.contains(WadlGenerator.WADL_QUERY)) {
                        query += "&" + WadlGenerator.WADL_QUERY;
                    }
                    m.put(Message.QUERY_STRING, query);
                }
                break;
            }
        }
        
    }
    
    @SuppressWarnings("unchecked")
    private void updateAcceptLanguageHeader(Message m, String anotherValue) {
        List<String> acceptLanguage =
            ((Map<String, List<String>>)m.get(Message.PROTOCOL_HEADERS)).get(HttpHeaders.ACCEPT_LANGUAGE);
        if (acceptLanguage == null) {
            acceptLanguage = new ArrayList<String>(); 
        }
        
        acceptLanguage.add(anotherValue);
        ((Map<String, List<String>>)m.get(Message.PROTOCOL_HEADERS))
            .put(HttpHeaders.ACCEPT_LANGUAGE, acceptLanguage);
    }
    
    private void updatePath(Message m, String path, String suffix) {
        String newPath = path.substring(0, path.length() - (suffix.length() + 1));
        HttpUtils.updatePath(m, newPath);
    }
    
    private void handleMethod(Message m, 
                              MultivaluedMap<String, String> queries,
                              HttpHeaders headers) {
        String method = queries.getFirst(METHOD_QUERY);
        if (method == null) {
            List<String> values = headers.getRequestHeader(METHOD_HEADER);
            if (values.size() == 1) {
                method = values.get(0);
            }
        }
        if (method != null) {
            m.put(Message.HTTP_REQUEST_METHOD, method);
        }
    }
    
    private void handleTypeQuery(Message m, MultivaluedMap<String, String> queries) {
        String type = queries.getFirst(ACCEPT_QUERY);
        if (type != null) {
            if (SHORTCUTS.containsKey(type)) {
                type = SHORTCUTS.get(type);
            }
            updateAcceptTypeHeader(m, type);
        }
    }
    
    private void handleCType(Message m, MultivaluedMap<String, String> queries) {
        String type = queries.getFirst(CTYPE_QUERY);
        if (type != null) {
            if (SHORTCUTS.containsKey(type)) {
                type = SHORTCUTS.get(type);
            }
            m.put(Message.CONTENT_TYPE, type);
        }
    }
    
    @SuppressWarnings("unchecked")
    private void updateAcceptTypeHeader(Message m, String acceptValue) {
        m.put(Message.ACCEPT_CONTENT_TYPE, acceptValue);
        ((Map<String, List<String>>)m.get(Message.PROTOCOL_HEADERS))
        .put(HttpHeaders.ACCEPT, Collections.singletonList(acceptValue));
    }
    
    /*
     * TODO : looks like QueryHandler is well suited for the purpose of serving
     * wadl/wsdl2 root requests with URIs which can not be used for selecting
     * ClassResourceInfo which is where RequestFilters invoked after the resource class
     * has been selected are handy. Consider implementing this method as part of the QueryHandler,
     * we will need to save the list of ClassResourceInfos on the EndpointInfo though
     */
    public Response checkMetadataRequest(Message m, UriInfo ui) {
        String originalRequestURI = (String)m.get(Message.REQUEST_URI);
        String query = (String)m.get(Message.QUERY_STRING);
        if (query != null && query.contains(WadlGenerator.WADL_QUERY)) {
            String requestURI = getValueWithoutSlash(originalRequestURI);
            String baseAddress = getValueWithoutSlash(HttpUtils.getBaseAddress(m));
            if (baseAddress.equals(requestURI)) {
                return handleMetadataRequest(m);
            }
        } else if (originalRequestURI != null && originalRequestURI.endsWith(".xsd")) {
            // trying WADLGenerator which may be caching schema resources won't
            // interfere with custom schema handlers if any
            return handleMetadataRequest(m);
        }
        return null;
    }
    
    private Response handleMetadataRequest(Message m) { 
        List<ProviderInfo<RequestHandler>> shs = ProviderFactory.getInstance(m).getRequestHandlers();
        // this is actually being tested by ProviderFactory unit tests but just in case
        // WadlGenerator, the custom or default one, must be the first one
        if (shs.size() > 0 && shs.get(0).getProvider() instanceof WadlGenerator) {
            return shs.get(0).getProvider().handleRequest(m, null);
        } else {
            return null;
        }
    }
    
    private static String getValueWithoutSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
    
    
}
