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

package com.pontusvision.processors.salesforce.base;

import com.force.api.ForceApi;
import com.force.api.ResourceRepresentation;
import com.pontusvision.nifi.salesforce.SalesforceUserPassAuthentication;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.expression.ExpressionLanguageScope;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.*;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.util.StandardValidators;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Created by jdyer on 8/4/16.
 */
public class AbstractSalesforceRESTOperation
    extends AbstractProcessor
{

  public static final PropertyDescriptor SALESFORCE_AUTH_SERVICE = new PropertyDescriptor
      .Builder().name("Salesforce.com Authentication Controller Service")
                .description("Your Salesforce.com authentication service for authenticating against Salesforce.com")
                .required(true)
                .identifiesControllerService(SalesforceUserPassAuthentication.class)
                .build();

  public static final Relationship REL_SUCCESS = new Relationship.Builder()
      .name("success")
      .description("Operation completed successfully")
      .build();

  public static final Relationship REL_ORIGINAL = new Relationship.Builder()
      .name("original")
      .description("Original FlowFile")
      .build();

  public static final Relationship REL_FAILURE = new Relationship.Builder()
      .name("failure")
      .description("Operation failed")
      .build();



  protected List<PropertyDescriptor> descriptors;

  protected Set<Relationship> relationships;

  protected SalesforceUserPassAuthentication sfAuthService = null;

  protected String getEndPoint(ProcessContext context, FlowFile flowFile)
  {
    return "";
  }

  @Override
  public void onPropertyModified(final PropertyDescriptor descriptor, final String oldValue, final String newValue)
  {
    sfAuthService = null;
  }

  @Override
  public Set<Relationship> getRelationships()
  {
    return this.relationships;
  }

  @Override
  public final List<PropertyDescriptor> getSupportedPropertyDescriptors()
  {
    return descriptors;
  }

  @Override
  public void onTrigger(final ProcessContext context, final ProcessSession session) throws ProcessException
  {
    final FlowFile flowFile = session.get();
    if (flowFile == null)
    {
      return;
    }

    if (sfAuthService == null)
    {
      sfAuthService = context.getProperty(SALESFORCE_AUTH_SERVICE)
                             .asControllerService(
                                 SalesforceUserPassAuthentication.class);
    }

    getLogger().info("Call Salesforce.com REST API.");
    try
    {
      ForceApi api = sfAuthService.getForceApi();

      ResourceRepresentation resp = api.get(getEndPoint(context, flowFile));

      String responseJson = resp.asString();

      FlowFile ff = session.write(flowFile, outputStream -> outputStream.write(responseJson.getBytes()));
      session.transfer(ff, REL_SUCCESS);
    }
    catch (Exception ex)
    {
      getLogger().error(ex.getMessage());
      session.transfer(flowFile, REL_FAILURE);
    }
  }

  @Override
  protected void init(final ProcessorInitializationContext context)
  {
    final List<PropertyDescriptor> descriptors = new ArrayList<PropertyDescriptor>();
    descriptors.add(SALESFORCE_AUTH_SERVICE);
    //    descriptors.add(SALESFORCE_URL_BASE);

    this.descriptors = Collections.unmodifiableList(descriptors);

    final Set<Relationship> relationships = new HashSet<Relationship>();
    relationships.add(REL_SUCCESS);
    relationships.add(REL_FAILURE);
    relationships.add(REL_ORIGINAL);
    this.relationships = Collections.unmodifiableSet(relationships);
  }





}
