/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.shindig.auth;

import org.apache.shindig.config.ContainerConfig;

/**
 * A special class of Token representing the anonymous viewer/owner
 *
 */
public class AnonymousSecurityToken implements SecurityToken {

  private final String container;
  private final long moduleId;
  private final String appUrl;

  public AnonymousSecurityToken() {
    this(ContainerConfig.DEFAULT_CONTAINER);
  }
  
  public AnonymousSecurityToken(String container) {
    this(container, 0L, "");
  }

  public AnonymousSecurityToken(String container, long moduleId, String appUrl) {
    this.container = container;
    this.moduleId = moduleId;
    this.appUrl = appUrl;
  }

  public boolean isAnonymous() {
    return true;
  }

  public String toSerialForm() {
    return "";
  }

  public String getOwnerId() {
    return "-1";
  }

  public String getViewerId() {
    return "-1";
  }

  public String getAppId() {
    return appUrl;
  }

  public String getDomain() {
    return "none";
  }

  public String getContainer() {
    return this.container;
  }

  public String getAppUrl() {
    return appUrl;
  }

  public long getModuleId() {
    return moduleId;
  }

  public String getUpdatedToken() {
    return "";
  }

  public String getAuthenticationMode() {
    return AuthenticationMode.UNAUTHENTICATED.name();
  }

  public String getTrustedJson() {
    return "";
  }

  public String getActiveUrl() {
    throw new UnsupportedOperationException("No active URL available");
  }
}