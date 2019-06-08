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

import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.SeeAlso;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.ProcessorInitializationContext;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.io.OutputStreamCallback;
import org.apache.nifi.processor.util.StandardValidators;

import com.pontusvision.nifi.salesforce.SalesforceUserPassAuthentication;

@Tags({"salesforce", "updated", "sobject"})
@CapabilityDescription("Retrieves the list of individual records that have been updated (added or changed) within the given timespan for the specified object. " +
        "SObject Get Updated is available in API version 29.0 and later")
@SeeAlso({SObjectGetDeletedProcessor.class})
public class SObjectGetUpdatedProcessor
    extends SObjectGetDeletedProcessor {

    private static final String SALESFORCE_OP = "sobject";

    @Override
    protected String getEndPoint(ProcessContext context, FlowFile flowFile)
    {
        return SALESFORCE_OP + "/" + context.getProperty(SOBJECT_NAME).evaluateAttributeExpressions().getValue() + "/updated/?start="
            + context.getProperty(START_DATE).evaluateAttributeExpressions().getValue() + "&end=" + context.getProperty(END_DATE).evaluateAttributeExpressions().getValue();

    }


}
