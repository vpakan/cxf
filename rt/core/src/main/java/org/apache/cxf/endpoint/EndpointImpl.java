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

package org.apache.cxf.endpoint;

import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.xml.namespace.QName;

import org.apache.cxf.Bus;
import org.apache.cxf.BusException;
import org.apache.cxf.BusFactory;
import org.apache.cxf.binding.Binding;
import org.apache.cxf.binding.BindingFactory;
import org.apache.cxf.binding.BindingFactoryManager;
import org.apache.cxf.common.i18n.Message;
import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.configuration.Configurable;
import org.apache.cxf.interceptor.AbstractAttributedInterceptorProvider;
import org.apache.cxf.interceptor.InFaultChainInitiatorObserver;
import org.apache.cxf.interceptor.MessageSenderInterceptor;
import org.apache.cxf.interceptor.OutFaultChainInitiatorObserver;
import org.apache.cxf.service.Service;
import org.apache.cxf.service.model.BindingInfo;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.transport.MessageObserver;

public class EndpointImpl extends AbstractAttributedInterceptorProvider implements Endpoint, Configurable {

    private static final Logger LOG = LogUtils.getL7dLogger(EndpointImpl.class);
    private static final ResourceBundle BUNDLE = LOG.getResourceBundle();

    private Service service;
    private Binding binding;
    private EndpointInfo endpointInfo;
    private Executor executor;
    private Bus bus;
    private MessageObserver inFaultObserver;
    private MessageObserver outFaultObserver;

    public EndpointImpl(Bus bus, Service s, QName endpointName) throws EndpointException {
        this(bus, s, s.getServiceInfo().getEndpoint(endpointName));
    }
    
    public EndpointImpl(Bus bus, Service s, EndpointInfo ei) throws EndpointException {
        if (ei == null) {
            throw new NullPointerException("EndpointInfo can not be null!");
        }
        
        if (bus == null) {
            this.bus = BusFactory.getDefaultBus();
        } else {
            this.bus = bus;
        }
        service = s;
        endpointInfo = ei;

        createBinding(endpointInfo.getBinding());
        
        inFaultObserver = new InFaultChainInitiatorObserver(bus);
        outFaultObserver = new OutFaultChainInitiatorObserver(bus);

        getOutInterceptors().add(new MessageSenderInterceptor());
        getOutFaultInterceptors().add(new MessageSenderInterceptor());
    }
    
    public String getBeanName() {
        return endpointInfo.getName().toString() + ".endpoint";
    }
    
   
    public EndpointInfo getEndpointInfo() {
        return endpointInfo;
    }

    public Service getService() {
        return service;
    }

    public Binding getBinding() {
        return binding;
    }

    public Executor getExecutor() {
        return executor == null ? service.getExecutor() : executor;
    }

    public void setExecutor(Executor e) {
        executor = e;
    }

    public Bus getBus() {
        return bus;
    }

    public void setBus(Bus bus) {
        this.bus = bus;
    }

    final void createBinding(BindingInfo bi) throws EndpointException {
        if (null != bi) {
            String namespace = bi.getBindingId();
            BindingFactory bf = null;
            try {
                bf = bus.getExtension(BindingFactoryManager.class).getBindingFactory(namespace);
                binding = bf.createBinding(bi);
            } catch (BusException ex) {
                throw new EndpointException(ex);
            }
            if (null == bf) {
                Message msg = new Message("NO_BINDING_FACTORY", BUNDLE, namespace);
                throw new EndpointException(msg);
            }
        }    
    }
    

    public MessageObserver getInFaultObserver() {
        return inFaultObserver;
    }

    public MessageObserver getOutFaultObserver() {
        return outFaultObserver;
    }

    public void setInFaultObserver(MessageObserver observer) {
        inFaultObserver = observer;        
    }

    public void setOutFaultObserver(MessageObserver observer) {
        outFaultObserver = observer;
        
    }
    
    /**
     * Utility method to make it easy to set properties from Spring.
     * 
     * @param properties
     */
    public void setProperties(Map<String, Object> properties) {
        this.putAll(properties);
    }
    
}
