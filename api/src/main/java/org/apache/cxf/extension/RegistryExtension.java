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

package org.apache.cxf.extension;

/**
 * 
 */
public interface RegistryExtension<K, T> {
    
    /**
     * Registers an object of type T with this registry. 
     *  
     * @param k the key under which rto register the object
     * @param t the object to register
     */
    void register(K k, T t);

    /**
     * Unregisters the object stored under the given key from this registry.
     *  
     * @param k the key 
     */
    void unregister(K k);

    /**
     * Returns the object stored under the given key.
     * @param k the  key
     * @return the object stored under the key
     */
    T get(K k);
}
