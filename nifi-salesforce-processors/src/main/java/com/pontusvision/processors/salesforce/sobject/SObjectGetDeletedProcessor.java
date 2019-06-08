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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.SeeAlso;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.expression.ExpressionLanguageScope;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.ProcessorInitializationContext;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.io.OutputStreamCallback;
import org.apache.nifi.processor.util.StandardValidators;

import com.pontusvision.nifi.salesforce.SalesforceUserPassAuthentication;
import com.pontusvision.processors.salesforce.base.AbstractSalesforceRESTOperation;

@Tags({"salesforce", "deleted", "sobject"})
@CapabilityDescription("Retrieves the list of individual records that have been deleted within the given timespan for the specified object. " +
        "SObject Get Deleted is available in API version 29.0 and later")
@SeeAlso({SObjectGetUpdatedProcessor.class})
public class SObjectGetDeletedProcessor
    extends AbstractSalesforceRESTOperation {

    //https://developer.salesforce.com/docs/atlas.en-us.api_rest.meta/api_rest/resources_getdeleted.htm

    private static final String SALESFORCE_OP = "sobject";

    public static final PropertyDescriptor SOBJECT_NAME = new PropertyDescriptor
            .Builder().name("SObject that will be interrogated for the records")
            .description("Salesforce SObject name that we are looking for.")
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
            .required(true)
            .build();

    public static final PropertyDescriptor START_DATE = new PropertyDescriptor
            .Builder().name("SObject start date")
            .description("ISO 8601 formatted start date for looking for the records.")
            .required(true)
            .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
            .build();

    public static final PropertyDescriptor END_DATE = new PropertyDescriptor
            .Builder().name("SObject end date")
            .description("ISO 8601 formatted end date for looking for the records.")
            .required(true)
            .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
            .build();

    @Override
    protected void init(final ProcessorInitializationContext context) {
        super.init(context);
        final List<PropertyDescriptor> descriptors = new ArrayList<>(this.descriptors);
        descriptors.add(SOBJECT_NAME);
        descriptors.add(START_DATE);
        descriptors.add(END_DATE);
        this.descriptors = Collections.unmodifiableList(descriptors);
    }

    @Override
    protected String getEndPoint(ProcessContext context, FlowFile flowFile)
    {
        return SALESFORCE_OP + "/" + context.getProperty(SOBJECT_NAME).evaluateAttributeExpressions().getValue() + "/deleted/?start="
            + context.getProperty(START_DATE).evaluateAttributeExpressions().getValue() + "&end=" + context.getProperty(END_DATE).evaluateAttributeExpressions().getValue();

    }


}
