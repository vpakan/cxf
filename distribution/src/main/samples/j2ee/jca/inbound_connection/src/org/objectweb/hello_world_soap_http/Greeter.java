/* Generated by WSDLToJava Compiler. */

package org.objectweb.hello_world_soap_http;

import java.rmi.RemoteException;

//import javax.jws.WebParam.Mode;
import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebResult;
import javax.jws.WebService;
//import javax.jws.soap.SOAPBinding.Style;
//import javax.jws.soap.SOAPBinding;
//import javax.jws.Oneway;


import javax.xml.ws.RequestWrapper;
import javax.xml.ws.ResponseWrapper;

/**
 * This class was generated by the Celtix 1.1-SNAPSHOT
 * Mon Jul 24 17:57:15 GMT+08:00 2006
 * Generated source version: 1.1-SNAPSHOT
 * 
 */

@WebService(wsdlLocation = "",
            targetNamespace = "http://objectweb.org/hello_world_soap_http",
            name = "Greeter")

public interface Greeter {

    @ResponseWrapper(targetNamespace = "http://objectweb.org/hello_world_soap_http/types",
                     className = "org.objectweb.hello_world_soap_http.types.SayHiResponse",
                     localName = "sayHiResponse")
    @RequestWrapper(targetNamespace = "http://objectweb.org/hello_world_soap_http/types",
                    className = "org.objectweb.hello_world_soap_http.types.SayHi",
                    localName = "sayHi")
    @WebResult(targetNamespace = "http://objectweb.org/hello_world_soap_http/types",
               name = "responseType")
    @WebMethod(operationName = "sayHi")
    java.lang.String sayHi() throws RemoteException;

    @ResponseWrapper(targetNamespace = "http://objectweb.org/hello_world_soap_http/types",
                     className = "org.objectweb.hello_world_soap_http.types.GreetMeResponse",
                     localName = "greetMeResponse")
    @RequestWrapper(targetNamespace = "http://objectweb.org/hello_world_soap_http/types",
                    className = "org.objectweb.hello_world_soap_http.types.GreetMe",
                    localName = "greetMe")
    @WebResult(targetNamespace = "http://objectweb.org/hello_world_soap_http/types",
               name = "responseType")
    @WebMethod(operationName = "greetMe")
    java.lang.String greetMe(
        @WebParam(targetNamespace = "http://objectweb.org/hello_world_soap_http/types",
                  name = "requestType")
        java.lang.String requestType
    ) throws RemoteException;
}
