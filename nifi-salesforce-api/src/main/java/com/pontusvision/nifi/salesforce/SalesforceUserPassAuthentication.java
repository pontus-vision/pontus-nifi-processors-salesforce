/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.pontusvision.nifi.salesforce;

import com.force.api.ApiConfig;
import com.force.api.ForceApi;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.controller.ControllerService;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.reporting.InitializationException;

@Tags({ "Salesforce.com UserPass OAUTH2 Authentication Controller Service" })
@CapabilityDescription("OAUTH2 authentication for Salesforce.com (see https://developer.salesforce.com/docs/atlas.en-us.218.0.api_rest.meta/api_rest/intro_understanding_username_password_oauth_flow.htm).")
public interface SalesforceUserPassAuthentication extends ControllerService
{

  public String getSalesforceAccessToken();

  public String getApiEndpoint();

  //  public String getResponseAttrib(String attrib) throws ProcessException;
//
//  public void authenticate() throws InitializationException;

  public ForceApi getForceApi();

  public ApiConfig getApiConfig();

}
