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
import org.apache.nifi.annotation.lifecycle.OnEnabled;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.components.ValidationResult;
import org.apache.nifi.components.Validator;
import org.apache.nifi.controller.ConfigurationContext;
import org.apache.nifi.expression.ExpressionLanguageScope;
import org.apache.nifi.reporting.InitializationException;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Tags({ "Salesforce.com", "username-password", "oauth", "authentication", "secret files" })
@CapabilityDescription("Service to provide authentication services against Salesforce.com")
public class SalesforceUserPassAuthenticationServiceSecretFiles
    extends SalesforceUserPassAuthenticationService
{

  public final static Validator FILE_VALIDATOR = (subject, input, context) -> {

    boolean isValid = Paths.get(input).toFile().canRead();
    String explanation = isValid ?
        "Able to read from file" :
        "Failed to read file " + input + " for " + subject;
    ValidationResult.Builder builder = new ValidationResult.Builder();
    return builder.input(input).subject(subject).valid(isValid).explanation(explanation).build();
  };

  //TODO: create a custom validator. Make sure the user is entering a URL and it is using HTTPS which is required by Salesforce.

  public static final PropertyDescriptor FILE_CLIENT_ID = new PropertyDescriptor
      .Builder().name("Salesforce.com ClientID (Consumer Key)")
                .description("File pointing to the 'Consumer Key' from the connected app definition.")
                .required(true)
                .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
                .addValidator(FILE_VALIDATOR)
                .defaultValue("/run/secrets/SALESFORCE_CLIENT_ID")
                .build();

  public static final PropertyDescriptor FILE_CLIENT_SECRET = new PropertyDescriptor
      .Builder().name("Salesforce.com ClientSecret (Consumer Secret)")
                .description("File pointing to the 'Consumer Secret' from the connected app definition.")
                .required(true)
                .sensitive(true)
                .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
                .addValidator(FILE_VALIDATOR)
                .defaultValue("/run/secrets/SALESFORCE_CLIENT_SECRET")

                .build();

  public static final PropertyDescriptor FILE_USERNAME = new PropertyDescriptor
      .Builder().name("Salesforce.com Username")
                .description(
                    "File pointing to the end-user's username -- usually looks like an e-mail.  Note that this must be provided for Oauth2 to work with apps without user input.")
                .required(true)
                .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
                .addValidator(FILE_VALIDATOR)
                .defaultValue("/run/secrets/SALESFORCE_USERNAME")
                .build();

  public static final PropertyDescriptor FILE_PASSWORD = new PropertyDescriptor
      .Builder().name("Salesforce.com Password (plus security token appended).")
                .description(
                    "File pointing to the end-user's password.   Note that this must be provided for Oauth2 to " +
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
                .addValidator(FILE_VALIDATOR)
                .defaultValue("/run/secrets/SALESFORCE_PASSWORD")
                .build();

  static
  {
    final List<PropertyDescriptor> props = new ArrayList<>();
    props.add(AUTH_ENDPOINT);
    props.add(FILE_CLIENT_ID);
    props.add(FILE_CLIENT_SECRET);
    props.add(FILE_USERNAME);
    props.add(FILE_PASSWORD);
    props.add(SALESFORCE_VERSION);
    props.add(SALESFORCE_API_TIMEOUT_MS);
    properties = Collections.unmodifiableList(props);
  }

  public static String readDataFromFileProperty(ConfigurationContext context, PropertyDescriptor prop)
      throws IOException
  {
    return new String(
        Files.readAllBytes(Paths.get(context.getProperty(prop).evaluateAttributeExpressions().getValue())),
        Charset.defaultCharset());
  }

  /**
   * @param context the configuration context
   * @throws InitializationException if unable to create a database connection
   */
  @OnEnabled
  public void onEnabled(final ConfigurationContext context) throws InitializationException
  {

    try
    {
      this.apiConfig = new ApiConfig();

      this.apiConfig.setClientId(readDataFromFileProperty(context, FILE_CLIENT_ID));
      this.apiConfig.setClientSecret(readDataFromFileProperty(context, FILE_CLIENT_SECRET));
      this.apiConfig.setUsername(readDataFromFileProperty(context, FILE_USERNAME));
      this.apiConfig.setPassword(readDataFromFileProperty(context, FILE_PASSWORD));
      this.apiConfig.setLoginEndpoint(context.getProperty(AUTH_ENDPOINT).evaluateAttributeExpressions().getValue());
      this.apiConfig
          .setApiVersionString(context.getProperty(SALESFORCE_VERSION).evaluateAttributeExpressions().getValue());
      this.apiConfig
          .setRequestTimeout(context.getProperty(SALESFORCE_API_TIMEOUT_MS).evaluateAttributeExpressions().asInteger());
      this.forceApi = new ForceApi(this.apiConfig);

    }
    catch (Throwable t)
    {
      getLogger().error("Failed to start Salesforce API ", t);
      throw new InitializationException(t);
    }
  }

}
