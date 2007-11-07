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

package org.apache.cxf.tools.java2wsdl.generator.wsdl11.annotator;

import java.lang.annotation.Annotation;

import javax.xml.bind.annotation.XmlAttachmentRef;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlList;
import javax.xml.bind.annotation.XmlMimeType;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.apache.cxf.common.util.StringUtils;
import org.apache.cxf.tools.common.model.Annotator;
import org.apache.cxf.tools.common.model.JAnnotation;
import org.apache.cxf.tools.common.model.JAnnotationElement;
import org.apache.cxf.tools.common.model.JavaAnnotatable;
import org.apache.cxf.tools.common.model.JavaField;

public class WrapperBeanFieldAnnotator implements Annotator {

    public void annotate(final JavaAnnotatable field) {
        JavaField jField = null;
        if (field instanceof JavaField) {
            jField = (JavaField) field;
        } else {
            throw new RuntimeException("WrapperBeanFiledAnnotator expect JavaField as input");
        }
        String rawName = jField.getRawName();
        JAnnotation xmlElementAnnotation = new JAnnotation(XmlElement.class);
        xmlElementAnnotation.addElement(new JAnnotationElement("name", rawName));
        if (!StringUtils.isEmpty(jField.getTargetNamespace())) {
            xmlElementAnnotation.addElement(new JAnnotationElement("namespace", 
                                                                          jField.getTargetNamespace()));
        }

        jField.addAnnotation(xmlElementAnnotation);
        
        for (Annotation ann : jField.getJaxbAnnotaions()) {
            if (ann instanceof XmlMimeType) {
                JAnnotation mimeAnno = new JAnnotation(XmlMimeType.class);
                mimeAnno.addElement(new JAnnotationElement("value", ((XmlMimeType)ann).value()));
                jField.addAnnotation(mimeAnno);
            } else if (ann instanceof XmlJavaTypeAdapter) {           
                JAnnotation jaxbAnn = new JAnnotation(XmlJavaTypeAdapter.class);
                jaxbAnn.addElement(new JAnnotationElement("value", ((XmlJavaTypeAdapter)ann).value()));
                jaxbAnn.addElement(new JAnnotationElement("type", ((XmlJavaTypeAdapter)ann).type()));
                jField.addAnnotation(jaxbAnn);
            } else if (ann instanceof XmlAttachmentRef) {
                JAnnotation jaxbAnn = new JAnnotation(XmlJavaTypeAdapter.class);
                jField.addAnnotation(jaxbAnn);
            } else if (ann instanceof XmlList) {
                JAnnotation jaxbAnn = new JAnnotation(XmlList.class);
                jField.addAnnotation(jaxbAnn);
            }
        }
    }
}
