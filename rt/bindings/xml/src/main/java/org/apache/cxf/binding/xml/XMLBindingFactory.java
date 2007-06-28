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
package org.apache.cxf.binding.xml;

import javax.xml.namespace.QName;

import org.apache.cxf.binding.AbstractBindingFactory;
import org.apache.cxf.binding.Binding;
import org.apache.cxf.binding.xml.interceptor.XMLFaultInInterceptor;
import org.apache.cxf.binding.xml.interceptor.XMLFaultOutInterceptor;
import org.apache.cxf.binding.xml.interceptor.XMLMessageInInterceptor;
import org.apache.cxf.binding.xml.interceptor.XMLMessageOutInterceptor;
import org.apache.cxf.interceptor.AttachmentInInterceptor;
import org.apache.cxf.interceptor.DocLiteralInInterceptor;
import org.apache.cxf.interceptor.StaxInInterceptor;
import org.apache.cxf.interceptor.StaxOutInterceptor;
import org.apache.cxf.interceptor.URIMappingInterceptor;
import org.apache.cxf.service.model.BindingInfo;
import org.apache.cxf.service.model.BindingOperationInfo;
import org.apache.cxf.service.model.OperationInfo;
import org.apache.cxf.service.model.ServiceInfo;

public class XMLBindingFactory extends AbstractBindingFactory {

    public static final String XML_PARSER_DISABLED = "xml.parser.disabled";
    public static final String ATTACHMENT_PARSER_DISABLED = "xml.parser.disabled";
    
    public Binding createBinding(BindingInfo binding) {
        XMLBinding xb = new XMLBinding(binding);
        if (!Boolean.TRUE.equals(binding.getProperty(ATTACHMENT_PARSER_DISABLED))) {
            xb.getInInterceptors().add(new AttachmentInInterceptor());
        }
        if (!Boolean.TRUE.equals(binding.getProperty(XML_PARSER_DISABLED))) {
            xb.getInInterceptors().add(new StaxInInterceptor());
        }
        xb.getInFaultInterceptors().add(new XMLFaultInInterceptor());
        
        xb.getOutInterceptors().add(new StaxOutInterceptor());
        
        if (!Boolean.TRUE.equals(binding.getProperty(DATABINDING_DISABLED))) {
            xb.getInInterceptors().add(new URIMappingInterceptor());
            xb.getOutInterceptors().add(new XMLMessageOutInterceptor());
            xb.getInInterceptors().add(new DocLiteralInInterceptor());
            xb.getInInterceptors().add(new XMLMessageInInterceptor());
        }
        
        xb.getOutFaultInterceptors().add(new StaxOutInterceptor());
        xb.getOutFaultInterceptors().add(new XMLFaultOutInterceptor());
        
        return xb;
    } 
    
    public BindingInfo createBindingInfo(ServiceInfo service, String namespace, Object config) {
        BindingInfo info = new BindingInfo(service, "http://cxf.apache.org/bindings/xformat");        
        info.setName(new QName(service.getName().getNamespaceURI(), 
                               service.getName().getLocalPart() + "XMLBinding"));

        for (OperationInfo op : service.getInterface().getOperations()) {                       
            BindingOperationInfo bop = 
                info.buildOperation(op.getName(), op.getInputName(), op.getOutputName());
            info.addOperation(bop);
        }
        
        return info;
    }

}
