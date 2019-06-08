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

package com.pontusvision.processors.salesforce.sobject;

import com.pontusvision.processors.salesforce.base.AbstractSalesforceRESTOperation;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.SeeAlso;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.expression.ExpressionLanguageScope;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessorInitializationContext;
import org.apache.nifi.processor.util.StandardValidators;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

@Tags({ "salesforce", "generic", "REST", "sobject" })
@CapabilityDescription("Generic REST API for Salesforce (provides a way to specify the URL suffix after  /services/data/<version>.")
@SeeAlso({ SObjectBasicInfoProcessor.class, SObjectDescribeProcessor.class })
public class SObjectGenericRESTProcessor
    extends AbstractSalesforceRESTOperation
{
  //https://developer.salesforce.com/docs/atlas.en-us.api_rest.meta/api_rest/resources_describeGlobal.htm

  public static final PropertyDescriptor REST_API_URL_SUFFIX = new PropertyDescriptor
      .Builder().name("SObject REST API")
                .description(
                    "Salesforce REST API entry after /services/data/<version>.  This must never start with a forward slash (/).")
                .addValidator(StandardValidators.createRegexMatchingValidator(Pattern.compile("^(?!/).*")))
                .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
                .required(true)
                .build();

  @Override
  protected void init(final ProcessorInitializationContext context)
  {
    super.init(context);
    final List<PropertyDescriptor> descriptors = new ArrayList<>(this.descriptors);
    descriptors.add(REST_API_URL_SUFFIX);
    this.descriptors = Collections.unmodifiableList(descriptors);
  }

  @Override
  protected String getEndPoint(ProcessContext context, FlowFile flowFile)
  {
    return context.getProperty(REST_API_URL_SUFFIX).evaluateAttributeExpressions(flowFile).getValue();
  }

}
