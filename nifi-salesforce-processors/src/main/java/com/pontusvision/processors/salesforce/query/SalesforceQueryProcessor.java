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

package com.pontusvision.processors.salesforce.query;

import com.force.api.ForceApi;
import com.force.api.QueryResult;
import com.pontusvision.nifi.salesforce.SalesforceUserPassAuthentication;
import com.pontusvision.processors.salesforce.base.AbstractSalesforceRESTOperation;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.expression.ExpressionLanguageScope;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.ProcessorInitializationContext;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.util.StandardValidators;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Tags({ "salesforce", "soql", "sobject", "query" })
@CapabilityDescription("Executes the specified SOQL query")
public class SalesforceQueryProcessor
    extends AbstractSalesforceRESTOperation
{

  //https://developer.salesforce.com/docs/atlas.en-us.api_rest.meta/api_rest/resources_query.htm

  public static final PropertyDescriptor SOQL = new PropertyDescriptor
      .Builder().name("SOQL Query")
                .description("Salesforce.com SOQL that will be perform on the Salesforce.com instance.")
                .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
                .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
                .required(true)
                .build();


  @Override
  protected void init(final ProcessorInitializationContext context)
  {
    super.init(context);
    final List<PropertyDescriptor> descriptors = new ArrayList<>(this.descriptors);
    descriptors.add(SOQL);
    this.descriptors = Collections.unmodifiableList(descriptors);
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

    try
    {
      ForceApi api = sfAuthService.getForceApi();

      String queryStr = context.getProperty(SOQL).evaluateAttributeExpressions(flowFile)
                               .getValue();
      QueryResult<String> resp = api.query(queryStr,String.class);
      getLogger().info("Called Salesforce.com Query API with the following query:" + queryStr);

      FlowFile ff = flowFile;

      do
      {
        final QueryResult<String> queryResult = resp;
        ff = session.write(ff, outputStream -> outputStream.write(queryResult.getRawResults().getBytes()));

        ff = session.putAttribute(ff,"salesforce_api_url", sfAuthService.getApiEndpoint());
        ff = session.putAttribute(ff,"salesforce_api_user", sfAuthService.getApiConfig().getUsername());
        ff = session.putAttribute(ff,"salesforce_api_client_id", sfAuthService.getApiConfig().getClientId());


        session.transfer(ff, REL_SUCCESS);

        if (!resp.isDone())
        {
          resp = api.queryMore(resp.getNextRecordsUrl(), String.class);
        }

      }while (!resp.isDone());
    }
    catch (Exception ex)
    {
      getLogger().error(ex.getMessage());
      session.transfer(flowFile, REL_FAILURE);
    }
  }

}
