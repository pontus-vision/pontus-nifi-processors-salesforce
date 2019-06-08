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

@Tags({"salesforce", "rows", "sobject"})
@CapabilityDescription("Accesses records based on the specified object ID. Retrieves, updates, or deletes records. This resource can also be used to " +
        "retrieve field values. Use the GET method to retrieve records or fields")
public class SObjectRowsProcessor
        extends AbstractSalesforceRESTOperation {

    private static final String SALESFORCE_OP = "sobjects";

    public static final PropertyDescriptor SOBJECT_NAME = new PropertyDescriptor
            .Builder().name("SObject that will be interrogated for records")
            .description("Salesforce SObject name that we are looking for.")
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
            .required(true)
            .build();

    public static final PropertyDescriptor SOBJECT_ROW_ID = new PropertyDescriptor
            .Builder().name("SObject row id")
            .description("SObject row id for the SObject row that this processor will interact with")
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
            .required(true)
            .build();

    @Override
    protected void init(final ProcessorInitializationContext context) {
        super.init(context);
        final List<PropertyDescriptor> descriptors = new ArrayList<>(this.descriptors);
        descriptors.add(SOBJECT_NAME);
        descriptors.add(SOBJECT_ROW_ID);
        this.descriptors = Collections.unmodifiableList(descriptors);
    }

    @Override
    protected String getEndPoint(ProcessContext context, FlowFile flowFile)
    {
        return SALESFORCE_OP + "/" + context.getProperty(SOBJECT_NAME).evaluateAttributeExpressions().getValue() + "/"
            + context.getProperty(SOBJECT_ROW_ID).evaluateAttributeExpressions().getValue();

    }

}
