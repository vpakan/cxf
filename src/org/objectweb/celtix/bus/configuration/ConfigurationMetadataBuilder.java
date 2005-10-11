package org.objectweb.celtix.bus.configuration;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import org.objectweb.celtix.configuration.ConfigurationException;
import org.objectweb.celtix.configuration.ConfigurationItemMetadata.LifecyclePolicy;
import org.objectweb.celtix.configuration.ConfigurationMetadata;

class ConfigurationMetadataBuilder  {

    private static final Logger LOG = Logger.getLogger(ConfigurationMetadataBuilder.class.getName());
    private static final String MEATADATA_NAMESPACE_URI = "http://celtix.objectweb.org/config-metadata";
    private static Validator metadataValidator;
    private static Map<String, Validator> typeValidators;
    private static Schema metadataSchema;
    private static Map<String, Schema> typeSchemas;
    private static Document metadataSchemaDocument;
    private static Map<String, Document> typeSchemaDocuments;
    private static ErrorHandler validatorErrorHandler;

    private Map<String, String> typeSchemaLocations;
    private Map<String, String> typeSchemaPackages;
    private ConfigurationMetadataImpl model;
    private URL url;
    
    private boolean validateAgainstComposite;
    
    public ConfigurationMetadataBuilder() {
        model = new ConfigurationMetadataImpl();
        typeSchemaLocations = new HashMap<String, String>();
        typeSchemaPackages = new HashMap<String, String>();
    }

    public ConfigurationMetadata build(URL u) throws ConfigurationException {
        url = u;
        String path = url.getFile();
        if (null == path) {
            throw new ConfigurationException(new ConfigurationMessage("IO_ERROR", url.toString()));
        }
        try {
            parseXML(new InputSource(new FileInputStream(path)));
        } catch (IOException ex) {
            throw new ConfigurationException(new ConfigurationMessage("IO_ERROR", path));
        }
        return model;
    }

    public ConfigurationMetadata build(InputSource is) throws ConfigurationException, IOException {
        parseXML(is);
        return model;
    }

    private void parseXML(InputSource is) throws ConfigurationException, IOException {

        // parse
        Document document = null;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true); 
            DocumentBuilder parser = factory.newDocumentBuilder();
            document = parser.parse(is);
        } catch (ParserConfigurationException ex) {
            throw new ConfigurationException(new ConfigurationMessage("PARSER_CONFIGURATION_ERROR", url
                .toString()), ex);
        } catch (SAXException ex) {
            throw new ConfigurationException(new ConfigurationMessage("PARSE_ERROR"), ex);
        }
        
        if (validateAgainstComposite) {
            deserializeImports(document);            
            validateAll(document);           
            
        } else  {
            try {
                Validator v = getMetadataValidator();
                v.validate(new DOMSource(document));
            } catch (SAXException ex) {
                throw new ConfigurationException(new ConfigurationMessage("METADATA_VALIDATION_ERROR", url
                                                                          .toString()), ex);
            }
            deserializeImports(document);
        }   
        deserializeConfigItems(document);
    }

    private void deserializeImports(Document document) throws ConfigurationException {
    
        for (Node nd = document.getDocumentElement().getFirstChild(); nd != null; nd = nd.getNextSibling()) {
            if (Node.ELEMENT_NODE == nd.getNodeType()
                && "configImport".equals(nd.getLocalName())
                && MEATADATA_NAMESPACE_URI.equals(nd.getNamespaceURI())) {             
                Element importElement = (Element)nd;
                String location = importElement.getAttribute("location");
                String namespaceURI = importElement.getAttribute("namespace");
                typeSchemaLocations.put(namespaceURI, location);
                addGlobalTypes(namespaceURI);
                addPackageType(namespaceURI);
            }
        } 
    }

    private void deserializeConfigItems(Document document) throws ConfigurationException {
        for (Node nd = document.getDocumentElement().getFirstChild(); nd != null; nd = nd.getNextSibling()) {
            if (Node.ELEMENT_NODE == nd.getNodeType()
                && "configItem".equals(nd.getLocalName())
                && MEATADATA_NAMESPACE_URI.equals(nd.getNamespaceURI())) {
                Element configItemElement = (Element)nd;
                deserializeConfigItem(configItemElement);
            }
        }
    }

    private void deserializeConfigItem(Element configItemElement) throws ConfigurationException {
        
        ConfigurationItemMetadataImpl item = new ConfigurationItemMetadataImpl();

        for (Node nd = configItemElement.getFirstChild(); nd != null; nd = nd.getNextSibling()) {
            if (Node.ELEMENT_NODE != nd.getNodeType()) {
                continue;
            } else if ("name".equals(nd.getLocalName())) {
                item.setName(getElementValue(nd));
            } else if ("type".equals(nd.getLocalName())) {
                String value = getElementValue(nd);
                QName type = stringToQName(configItemElement.getOwnerDocument(), value);
                item.setType(type); 
                String packageName = typeSchemaPackages.get(type.getNamespaceURI());
                item.setTypePackageName(packageName);
            } else if ("description".equals(nd.getLocalName())) {
                item.setDescription(getElementValue(nd));
            } else if ("lifecyclePolicy".equals(nd.getLocalName())) {
                String value = getElementValue(nd);
                if (null != value) {
                    if ("static".equals(value)) {
                        item.setLifecyclePolicy(LifecyclePolicy.STATIC);
                    } else if ("process".equals(value)) {
                        item.setLifecyclePolicy(LifecyclePolicy.PROCESS);
                    } else if ("bus".equals(value)) {
                        item.setLifecyclePolicy(LifecyclePolicy.BUS);
                    } else {
                        item.setLifecyclePolicy(LifecyclePolicy.DYNAMIC);
                    }
                }
            } else {
                // this must be the extension element holding the default value
                deserializeDefaultValue(item, (Element)nd);
            }
        }

        model.addItem(item);
    }   
    
    private void deserializeDefaultValue(ConfigurationItemMetadataImpl item, Element data)
        throws ConfigurationException {
        
        String localName = data.getLocalName();
        String namespaceURI = data.getNamespaceURI();
        QName type = new QName(namespaceURI, localName);
        if (!type.equals(item.getType())) {
            throw new ConfigurationException(new ConfigurationMessage("INVALID_TYPE_FOR_DEFAULT_VALUE",
                                                                      item.getType()));
        }        
        
        if (!validateAgainstComposite) {        
            Validator validator = getTypeValidator(type);
            try {
                validator.validate(new DOMSource(data));
            } catch (SAXException ex) {
                throw new ConfigurationException(new ConfigurationMessage("INVALID_DEFAULT_VALUE"), ex);
            } catch (IOException ex) {
                throw new ConfigurationException(new ConfigurationMessage("PARSE_DEFAULT_VALUE_ERROR", item
                    .getName()), ex);
            }
        }  
        
        unmarshalDefaultValue(item, data);
    }
    
    private void unmarshalDefaultValue(ConfigurationItemMetadataImpl item, Element data) 
        throws ConfigurationException {
        JAXBContext context = null;
        String packageName = item.getTypePackageName();
        // assert null != packageName : "text";
        Object obj = null;
        try {
            context = JAXBContext.newInstance(packageName);
            Unmarshaller u = context.createUnmarshaller();
            obj = u.unmarshal(data);
            if (obj instanceof JAXBElement<?>) {
                JAXBElement<?> el = (JAXBElement<?>)obj;
                if (el.getName().equals(item.getType())) {
                    obj = el.getValue();
                }
            }    
        } catch (JAXBException ex) {
            throw new ConfigurationException(new ConfigurationMessage("DEFAULE_VALUE_UNMARSHAL_ERROR",
                                                                      item.getName()), ex);
        }
        if (null != obj) {
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("Unmarshaled default value into object of type: " + obj.getClass().getName() 
                         + "    value: " + obj);
            }
            item.setDefaultValue(obj);        
        }
    }

    private QName stringToQName(Document document, String s) throws ConfigurationException {
        int index = s.indexOf(":");
        if (index < 0) {
            return new QName(s);
        } else if (index == 0) {
            throw new ConfigurationException(new ConfigurationMessage("ILLEGAL_QNAME", s));
        }
        String prefix = s.substring(0, index);
        
        // TODO what if the namespace prefix was not declared on the root element 
        String uri = document.getDocumentElement().getAttribute("xmlns:" + prefix);
        if (null == uri || "".equals(uri)) {
            throw new ConfigurationException(new ConfigurationMessage("ILLEGAL_PREFIX", s));
        }
        if (index >= (s.length() - 1)) {
            throw new ConfigurationException(new ConfigurationMessage("ILLEGAL_QNAME", s));
        }
        String localPart = s.substring(index + 1);
        QName type = new QName(uri, localPart);
        if (null == model.getType(type)) {            
            throw new ConfigurationException(new ConfigurationMessage("UNKNOWN_TYPE", s));
        }
        return new QName(uri, localPart);
    }
    
    private QName stringToQName(Element rootElement, Element element, String s)
        throws ConfigurationException {
        int index = s.indexOf(":");
        if (index < 0) {
            return new QName(s);
        } else if (index == 0) {
            throw new ConfigurationException(new ConfigurationMessage("ILLEGAL_QNAME", s));
        }
        String prefix = s.substring(0, index);

        String uri = element.getAttribute("xmlns:" + prefix);
        if (null == uri || "".equals(uri)) {
            uri = rootElement.getAttribute("xmlns:" + prefix);
        }
        if (null == uri || "".equals(uri)) {
            throw new ConfigurationException(new ConfigurationMessage("ILLEGAL_PREFIX", s));
        }
        if (index >= (s.length() - 1)) {
            throw new ConfigurationException(new ConfigurationMessage("ILLEGAL_QNAME", s));
        }
        String localPart = s.substring(index + 1);
        QName type = new QName(uri, localPart);
        return type;
    }
    
    private Document getMetadataSchemaDocument() throws ConfigurationException {
        if (null == metadataSchemaDocument) {
            String path = getSchemaLocation();  
            metadataSchemaDocument = getSchemaDocument(path);
        }
        return metadataSchemaDocument;
    }
    
    private Document getTypeSchemaDocument(String namespaceURI) throws ConfigurationException {
        if (null == typeSchemaDocuments) {
            typeSchemaDocuments = new HashMap<String, Document>();
        }
        Document document = typeSchemaDocuments.get(namespaceURI);
        if (null == document) {
            String location = typeSchemaLocations.get(namespaceURI);
            // assert null != location; 
            URI typesURI = null;
            try {
                typesURI = new URI(location);
            } catch (URISyntaxException ex) {
                throw new ConfigurationException(new ConfigurationMessage("SCHEMA_LOCATION_ERROR",
                                                                          location), ex);
            } 
            
            URL typesURL = null;
            
            if (typesURI.isAbsolute()) {
                try {
                    typesURL = typesURI.toURL();
                } catch (MalformedURLException ex) {
                    throw new ConfigurationException(new ConfigurationMessage("SCHEMA_LOCATION_ERROR",
                                                                              location), ex);
                }
            } else {
                typesURL = getClass().getResource(typesURI.getPath());
            }
            if (null == typesURL) {
                throw new ConfigurationException(new ConfigurationMessage("SCHEMA_LOCATION_ERROR",
                                                                          location));
            }
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("Effective location for schema: " + typesURL.getPath());
            }
            document = getSchemaDocument(typesURL.getPath());
            typeSchemaDocuments.put(namespaceURI, document);
        }
        return document;
    }
    
    private Document getSchemaDocument(String path) throws ConfigurationException {
        Document document = null;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true); 
            DocumentBuilder parser = factory.newDocumentBuilder();
            document = parser.parse(new InputSource(new FileInputStream(path)));
        } catch (ParserConfigurationException ex) {
            throw new ConfigurationException(new ConfigurationMessage("PARSER_CONFIGURATION_ERROR", 
                                                                      path), ex);
        } catch (SAXException ex) {
            throw new ConfigurationException(new ConfigurationMessage("PARSE_ERROR"), ex);
        } catch (IOException ex) {
            throw new ConfigurationException(new ConfigurationMessage("FILE_OPEN_ERROR", path), ex);
        }
        return document;
    }
    
    private Source getMetadataSchemaSource() throws ConfigurationException {
        Document document = getMetadataSchemaDocument();
        // assert null != document;
        return new DOMSource(document);
        /*
        if (null == metadataSchemaSource) {
            String path = getSchemaLocation();
            metadataSchemaSource = new StreamSource(new File(path));
        }
        return metadataSchemaSource;
        */     
    }
    
    private Source getTypeSchemaSource(String namespaceURI) throws ConfigurationException {
        Document document = getTypeSchemaDocument(namespaceURI);
        // assert null != document;
        return new DOMSource(document);
        /*
        if (null == typeSchemaSources) {
            typeSchemaSources = new HashMap<String, Source>();
        }
        Source source = typeSchemaSources.get(namespaceURI);

        if (null == source) {
            String location = typeSchemaLocations.get(namespaceURI);
            assert null != location;
            URI typesURI = null;
            try {
                typesURI = new URI(location);
            } catch (URISyntaxException ex) {
                throw new ConfigurationException(new ConfigurationMessage("TYPES_URI_ERROR"), ex);
            }
            if (!typesURI.isAbsolute()) {
                try {
                    typesURI = url.toURI().resolve(typesURI);
                } catch (URISyntaxException ex) {
                    throw new ConfigurationException(new ConfigurationMessage("TYPES_URI_ERROR"), ex);
                }
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.fine("Effective import location for schema " + namespaceURI + ": "
                             + typesURI.toString());
                }
            }
            source = new StreamSource(new File(typesURI.getPath()));
            typeSchemaSources.put(namespaceURI, source);
        }
        return source;  
        */   
    }

    private String getSchemaLocation() {
        URL u = ConfigurationException.class.getResource("config-metadata.xsd");
        if (null != u) {
            return u.getFile();
        }
        LOG.severe("Could not find configuration metadata schema resource");
        return null;
    }

    private Schema getMetadataSchema() {
        if (null == metadataSchema) {
            String path = getSchemaLocation();
            try {
                metadataSchema = getSchema(path);
            } catch (ConfigurationException ex) {
                // should never happen as metadata schema is immutable
                LOG.log(Level.SEVERE, "Could not create configuration metadata schema.", ex);
            }
        }
        return metadataSchema;
    }

    private Schema getTypeSchema(String namespaceURI) throws ConfigurationException {
        if (null == typeSchemas) {
            typeSchemas = new HashMap<String, Schema>();
        }
        Schema schema = typeSchemas.get(namespaceURI);
        if (null == schema) {
            String location = typeSchemaLocations.get(namespaceURI);
            URI typesURI = null;
            try {
                typesURI = new URI(location);
            } catch (URISyntaxException ex) {
                throw new ConfigurationException(new ConfigurationMessage("SCHEMA_CREATION_ERROR",
                                                                          namespaceURI), ex);
            }
            URL typesURL =  null;
            if (typesURI.isAbsolute()) {
                try {
                    typesURL = typesURI.toURL();
                } catch (MalformedURLException ex) {
                    throw new ConfigurationException(new ConfigurationMessage("SCHEMA_CREATION_ERROR",
                                                                              namespaceURI), ex);
                }
            } else {
                typesURL = System.class.getResource(typesURI.getPath());
                if (null == typesURI) {
                    throw new ConfigurationException(new ConfigurationMessage("SCHEMA_CREATION_ERROR",
                                                                              namespaceURI));   
                }
            }
            
           
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("Effective import location for schema " + namespaceURI + ": " + typesURL.getPath());
            }
    
            schema = getSchema(typesURL.getPath());
            typeSchemas.put(namespaceURI, schema);
        }
        return schema;
    }

    private Schema getSchema(String path) throws ConfigurationException {
        Source schemaFile = new StreamSource(new File(path));
        
        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        Schema schema = null;
        try {
            schema = factory.newSchema(schemaFile);
        } catch (SAXException ex) {
            throw new ConfigurationException(new ConfigurationMessage("SCHEMA_CREATION_ERROR", path), ex);
        }
        return schema;
    }

    private Validator getMetadataValidator() {
        if (null == metadataValidator) {
            Schema schema = getMetadataSchema();
            // assert null != schema;
            metadataValidator = schema.newValidator();
            if (null == validatorErrorHandler) {
                validatorErrorHandler = new ValidatorErrorHandler();
            }
            metadataValidator.setErrorHandler(validatorErrorHandler);
            // assert null != metadataValidator;
        }
        return metadataValidator;
    }

    private Validator getTypeValidator(QName type) throws ConfigurationException {
        if (null == typeValidators) {
            typeValidators = new HashMap<String, Validator>();
        }
        String namespaceURI = type.getNamespaceURI();
        Validator validator = typeValidators.get(namespaceURI);
        if (null == validator) {
            Schema schema = getTypeSchema(namespaceURI);
            // assert null != schema;
            validator = schema.newValidator();
            if (null == validatorErrorHandler) {
                validatorErrorHandler = new ValidatorErrorHandler();
            }
            validator.setErrorHandler(validatorErrorHandler);
            typeValidators.put(namespaceURI, validator);
        }
        return validator;
    }
    
    private String getElementValue(Node node) {
        for (Node nd = node.getFirstChild(); nd != null; nd = nd.getNextSibling()) {
            if (Node.TEXT_NODE == nd.getNodeType()) {
                return nd.getNodeValue();
            }
        }
        return null;
    }


    final class ValidatorErrorHandler implements ErrorHandler {

        public void error(SAXParseException exception) throws SAXException {
            throw exception;
        }

        public void fatalError(SAXParseException exception) throws SAXException {
            throw exception;
        }

        public void warning(SAXParseException exception) throws SAXException {
            throw exception;
        }

    }
    
    private void addGlobalTypes(String namespaceURI) throws ConfigurationException {
        Document document = getTypeSchemaDocument(namespaceURI);
        Element root = document.getDocumentElement();
        for (Node nd = root.getFirstChild(); nd != null; nd = nd.getNextSibling()) {
            if (Node.ELEMENT_NODE == nd.getNodeType()) {
                if ("element".equals(nd.getLocalName())) {
                    String elementName = ((Element)nd).getAttribute("name");
                    QName n = new QName(namespaceURI, elementName);
                    String elementType = ((Element)nd).getAttribute("type");
                    QName t = stringToQName(root, (Element)nd, elementType);
                    model.addType(n, t);
                }
            }
        }       
    }
    
    private void addPackageType(String namespaceURI) throws ConfigurationException {
        Document document = getTypeSchemaDocument(namespaceURI);
        Element root = document.getDocumentElement();
        Element annotationElement = null;
        for (Node nd = root.getFirstChild(); nd != null; nd = nd.getNextSibling()) {
            if (Node.ELEMENT_NODE == nd.getNodeType() && "annotation".equals(nd.getLocalName())) {
                annotationElement = (Element)nd;
                break;
            }
        }
        Element appInfoElement = null;
        if (null != annotationElement) {
            for (Node nd = annotationElement.getFirstChild(); nd != null; nd = nd.getNextSibling()) {
                if (Node.ELEMENT_NODE == nd.getNodeType() && "appinfo".equals(nd.getLocalName())) {
                    appInfoElement = (Element)nd;
                    break;
                }
            }
        }
        Element schemaBindingsElement = null;
        if (null != appInfoElement) {
            for (Node nd = appInfoElement.getFirstChild(); nd != null; nd = nd.getNextSibling()) {
                if (Node.ELEMENT_NODE == nd.getNodeType() && "schemaBindings".equals(nd.getLocalName())) {
                    schemaBindingsElement = (Element)nd;
                    break;
                }
            }
        }
        Element packageElement = null;
        if (null != schemaBindingsElement) {
            for (Node nd = schemaBindingsElement.getFirstChild(); nd != null; nd = nd.getNextSibling()) {
                if (Node.ELEMENT_NODE == nd.getNodeType() && "package".equals(nd.getLocalName())) {
                    packageElement = (Element)nd;
                    break;
                }
            }
        }
        String packageName = null;
        if (null != packageElement) {
            packageName = packageElement.getAttribute("name");
        } else {
            packageName = getTypeSchemaPackageName(namespaceURI);
        }
        
        if (null == packageName) {
            throw new ConfigurationException(new ConfigurationMessage("MISSING_PACKAGE_NAME", namespaceURI));
        }
        typeSchemaPackages.put(namespaceURI, packageName);    
    }
    
    private void validateAll(Document document) throws ConfigurationException, IOException {
        try {
            Source[] schemaSources = new Source[typeSchemaLocations.size() + 1];
            int i = 0;
            schemaSources[i++] = getMetadataSchemaSource();
            for (String namespaceURI : typeSchemaLocations.keySet()) {
                schemaSources[i++] = getTypeSchemaSource(namespaceURI);
            }
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            
            Schema schema = null;
            try {
                schema = factory.newSchema(schemaSources);
            } catch (SAXException ex) {
                throw new ConfigurationException(new ConfigurationMessage("SCHEMA_CREATION_ERROR", 
                                                                          getSchemaLocation()), ex);
            }
            Validator validator = schema.newValidator();

            if (null == validatorErrorHandler) {
                validatorErrorHandler = new ValidatorErrorHandler();
            }
            
            validator.validate(new DOMSource(document));
        } catch (SAXException ex) {
            throw new ConfigurationException(new ConfigurationMessage("METADATA_VALIDATION_ERROR", url
                .toString()), ex);
        }
    }
    
    /** 
     * Gets the package name for jaxb schema bindings of the schema identified by the namespaceURI
     * from system property <code>org.objectweb.celtix.configuration.schemaBindings</code>, which
     * is a list of namespaceURI - packagename pairs.
     * 
     * @param namespaceURI the namespaceURI identifying the schema.
     * @return the jaxb schema binding package name or null if the property is not defined or does 
     * not contain the corresponding namespaceURI - packagename pair.
     */
    private String getTypeSchemaPackageName(String namespaceURI) {
        String value = System.getProperty("org.objectweb.celtix.configuration.schemaBindings");
        if (value != null) {
            StringTokenizer st = new StringTokenizer(value);
            while (st.hasMoreTokens()) {
                String token = st.nextToken();
                if (token.equals(namespaceURI) && st.hasMoreTokens()) {
                    return st.nextToken();
                }
            }
        }
        return null;  
    }
}
