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
import com.force.api.Auth;
import com.force.api.ForceApi;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.annotation.lifecycle.OnDisabled;
import org.apache.nifi.annotation.lifecycle.OnEnabled;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.controller.AbstractControllerService;
import org.apache.nifi.controller.ConfigurationContext;
import org.apache.nifi.expression.ExpressionLanguageScope;
import org.apache.nifi.processor.util.StandardValidators;
import org.apache.nifi.reporting.InitializationException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Tags({ "Salesforce.com", "username-password", "oauth", "authentication" })
@CapabilityDescription("Service to provide authentication services against Salesforce.com")
public class SalesforceUserPassAuthenticationService
    extends AbstractControllerService implements SalesforceUserPassAuthentication
{

  //Salesforce.com Documentation around this authentication flow
  //https://developer.salesforce.com/docs/atlas.en-us.api_rest.meta/api_rest/intro_understanding_username_password_oauth_flow.htm

  protected final String     GRANT_TYPE = "password";
  protected       JSONObject sfResponse;

  //TODO: create a custom validator. Make sure the user is entering a URL and it is using HTTPS which is required by Salesforce.
  public static final PropertyDescriptor AUTH_ENDPOINT = new PropertyDescriptor
      .Builder().name("Salesforce REST Authentication Endpoint")
                .description("The URL for the authentication endpoint for Salesforce.com.   " +
                    "Note: the /services/oauth2/token suffix must NOT be addeed here.  " +
                    "If using a sandbox env, the URL will typically be https://test.salesforce.com")
                .required(true)
                .addValidator(StandardValidators.URL_VALIDATOR)
                .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
                .defaultValue("https://login.salesforce.com")
                .build();

  public static final PropertyDescriptor CLIENT_ID = new PropertyDescriptor
      .Builder().name("Salesforce.com ClientID (Consumer Key)")
                .description("The 'Consumer Key' from the connected app definition.")
                .required(true)
                .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
                .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
                .build();

  public static final PropertyDescriptor CLIENT_SECRET = new PropertyDescriptor
      .Builder().name("Salesforce.com ClientSecret (Consumer Secret)")
                .description("The 'Consumer Secret' from the connected app definition.")
                .required(true)
                .sensitive(true)
                .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
                .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
                .build();

  public static final PropertyDescriptor USERNAME = new PropertyDescriptor
      .Builder().name("Salesforce.com Username")
                .description(
                    "End-user's username -- usually looks like an e-mail.  Note that this must be provided for Oauth2 to work with apps without user input.")
                .required(true)
                .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
                .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
                .build();

  public static final PropertyDescriptor PASSWORD = new PropertyDescriptor
      .Builder().name("Salesforce.com Password (plus security token appended).")
                .description("End-user's password.   Note that this must be provided for Oauth2 to " +
                    "work with apps without user input.  You must append the user’s security " +
                    "token to their password A security token is an automatically-generated key " +
                    "from Salesforce. For example, if a user's password is mypassword, and their " +
                    "security token is XXXXXXXXXX, then the value provided for this parmeter must " +
                    "be mypasswordXXXXXXXXXX. For more information on security tokens see “Reset Your " +
                    "Security Token” in the online help.  Hint: you can also change your password, and " +
                    "Salesforce.com will send you an e-mail with the new security token.")
                .required(true)
                .sensitive(true)
                .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
                .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
                .build();

  public static final String             DEFAULT_SALESFORCE_VERSION = "v46.0";
  public static final PropertyDescriptor SALESFORCE_VERSION         = new PropertyDescriptor.Builder()
      .name("Salesforce API Version")
      .description("API Version")
      .required(true)
      .defaultValue(DEFAULT_SALESFORCE_VERSION)
      .addValidator(StandardValidators.NON_BLANK_VALIDATOR)
      .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
      .build();

  public static final String             DEFAULT_SALESFORCE_API_TIMEOUT_MS = "0";
  public static final PropertyDescriptor SALESFORCE_API_TIMEOUT_MS         = new PropertyDescriptor.Builder()
      .name("Salesforce API Timeout (ms)")
      .description("API Timeout in milliseconds; 0 means wait forever")
      .required(true)
      .defaultValue(DEFAULT_SALESFORCE_API_TIMEOUT_MS)
      .addValidator(StandardValidators.NON_NEGATIVE_INTEGER_VALIDATOR)
      .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
      .build();

  protected static List<PropertyDescriptor> properties;

  static
  {
    final List<PropertyDescriptor> props = new ArrayList<>();
    props.add(AUTH_ENDPOINT);
    props.add(CLIENT_ID);
    props.add(CLIENT_SECRET);
    props.add(USERNAME);
    props.add(PASSWORD);
    props.add(SALESFORCE_VERSION);
    props.add(SALESFORCE_API_TIMEOUT_MS);
    properties = Collections.unmodifiableList(props);
  }

  protected com.force.api.ApiConfig apiConfig;

  protected ForceApi forceApi;

  @Override
  protected List<PropertyDescriptor> getSupportedPropertyDescriptors()
  {
    return properties;
  }

  /**
   * @param context the configuration context
   * @throws InitializationException if unable to create a database connection
   */
  @OnEnabled
  public void onEnabled(final ConfigurationContext context) throws InitializationException
  {

    this.apiConfig = new ApiConfig();

    String clientId = context.getProperty(CLIENT_ID).evaluateAttributeExpressions().getValue();
    this.apiConfig.setClientId(clientId);

    String clientSecret = context.getProperty(CLIENT_SECRET).evaluateAttributeExpressions().getValue();
    this.apiConfig.setClientSecret(clientSecret);

    String userName = context.getProperty(USERNAME).evaluateAttributeExpressions().getValue();
    this.apiConfig.setUsername(userName);

    String password = context.getProperty(PASSWORD).evaluateAttributeExpressions().getValue();
    this.apiConfig.setPassword(password);

    String authEndpoint = context.getProperty(AUTH_ENDPOINT).evaluateAttributeExpressions().getValue();
    this.apiConfig.setLoginEndpoint(authEndpoint);

    String apiVersion = context.getProperty(SALESFORCE_VERSION).evaluateAttributeExpressions().getValue();
    this.apiConfig.setApiVersionString(apiVersion);

    int apiTimeout = context.getProperty(SALESFORCE_API_TIMEOUT_MS).evaluateAttributeExpressions().asInteger();
    this.apiConfig.setRequestTimeout(apiTimeout);

    this.forceApi = new ForceApi(this.apiConfig);

  }

  @OnDisabled
  public void shutdown()
  {
    try
    {
      Auth.revokeToken(apiConfig, forceApi.getSession().getAccessToken());
    }
    catch (Throwable t)
    {
      getLogger().error("Failed to revoke API token ", t);
    }
  }

  @Override
  public ForceApi getForceApi()
  {
    return forceApi;
  }

  @Override
  public ApiConfig getApiConfig()
  {
    return apiConfig;
  }

  @Override
  public String getSalesforceAccessToken()
  {
    return forceApi.getSession().getAccessToken();
  }

  public String getApiEndpoint()
  {
    return forceApi.getSession().getApiEndpoint();
  }

}
