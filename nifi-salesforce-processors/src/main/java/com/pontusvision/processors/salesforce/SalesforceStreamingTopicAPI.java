package com.pontusvision.processors.salesforce;

import com.pontusvision.nifi.salesforce.SalesforceUserPassAuthentication;
import com.pontusvision.processors.salesforce.base.AbstractSalesforceRESTOperation;
import com.salesforce.emp.connector.BayeuxParameters;
import com.salesforce.emp.connector.EmpConnector;
import org.apache.nifi.annotation.behavior.WritesAttribute;
import org.apache.nifi.annotation.behavior.WritesAttributes;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.annotation.lifecycle.OnScheduled;
import org.apache.nifi.annotation.lifecycle.OnStopped;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.distributed.cache.client.Deserializer;
import org.apache.nifi.distributed.cache.client.DistributedMapCacheClient;
import org.apache.nifi.distributed.cache.client.Serializer;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.ProcessorInitializationContext;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.util.StandardValidators;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.json.JSONObject;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

@Tags({ "salesforce", "streaming", "api", "PushTopic", "channel" })
//@TriggerWhenEmpty
//@InputRequirement(InputRequirement.Requirement.INPUT_FORBIDDEN)
@CapabilityDescription(
    "SalesforceStreamingAPI for subscribing to a Salesforce APEX PushTopic and receiving real time updates" +
        "based on the Salesforce defined SOQL setup in your Salesforce account.")
//@ReadsAttributes({ @ReadsAttribute(attribute = "", description = "") })
@WritesAttributes(
    {
        @WritesAttribute(attribute = "replayId", description = "The replayId for the salesforce stream "
            + "(used to pick up where we left if used in conjunction with a Distributed Map Cache")
        , @WritesAttribute(attribute = "changeType", description = "The type of change (CREATE, DELETE, UPDATE)")
        , @WritesAttribute(attribute = "entityName", description = "The name of the table/obj type being manipulated")
        , @WritesAttribute(attribute = "pg_origEventJson", description = "The JSON data from the original event")
        , @WritesAttribute(attribute = "pg_recordIds", description = "String in JSON Array format with the record "
        + "ids affected")
    }

)
public class SalesforceStreamingTopicAPI
    extends AbstractSalesforceRESTOperation
{

  public static final PropertyDescriptor SF_DISTRIB_MAP_CACHE = new PropertyDescriptor
      .Builder().name("Distributed Map Cache Client")
                .description("An optional distributed map cache that can retrieve the last replayId")
                .required(false)
                .identifiesControllerService(DistributedMapCacheClient.class)
                .build();

  public static final PropertyDescriptor SF_PUSH_TOPIC = new PropertyDescriptor
      .Builder().name("Salesforce CometD Push Topic")
                .description("The Salesforce.com Push Topic to describe to")
                .defaultValue("/data/ChangeEvents")
                .required(true)
                .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
                .build();

  protected DistributedMapCacheClient distributedMapCacheClient = null;

  protected EmpConnector conn;

  private final BlockingQueue<JSONObject> jsonQueue = new LinkedBlockingQueue<>(5000);

  //  protected ExecutorService eventLoop = Executors.newFixedThreadPool(1);

  protected volatile boolean isRunning = false;

  protected Serializer<String>   serializer   = (s, outputStream) -> outputStream.write(s.getBytes());
  protected Deserializer<String> deserializer = String::new;

  protected Consumer<Map<String, Object>> consumer = event -> {

    while (!jsonQueue.offer(new JSONObject(event)))
    {
      try
      {
        Thread.sleep(10);
      }
      catch (InterruptedException e)
      {
        throw new RuntimeException(e);
      }
    }
  };

  @Override
  protected void init(final ProcessorInitializationContext context)
  {
    super.init(context);
    final List<PropertyDescriptor> descriptors = new ArrayList<>(this.descriptors);
    descriptors.add(SF_DISTRIB_MAP_CACHE);
    descriptors.add(SF_PUSH_TOPIC);

    this.descriptors = Collections.unmodifiableList(descriptors);
    final Set<Relationship> relationships = new HashSet<Relationship>();
    relationships.add(REL_SUCCESS);
    relationships.add(REL_FAILURE);
    this.relationships = Collections.unmodifiableSet(relationships);

    isRunning = false;
  }

  public EmpConnector getEmpConnector()
  {

    BayeuxParameters params = new BayeuxParameters()
    {
      @Override
      public String bearerToken()
      {
        return sfAuthService.getSalesforceAccessToken();
      }

      @Override
      public SslContextFactory sslContextFactory()
      {
        return new SslContextFactory.Client();
      }

      @Override public URL host()
      {
        try
        {
          return new URL(sfAuthService.getApiEndpoint());
        }
        catch (MalformedURLException e)
        {
          throw new IllegalStateException(
              String.format("Unable to form URL for %s", sfAuthService.getApiConfig().getLoginEndpoint()));
        }
      }
    };

    //    HttpClient httpClient = new HttpClient(params.sslContextFactory());
    //    httpClient.getProxyConfiguration().getProxies().addAll(params.proxies());

    return new EmpConnector(params);
  }

  @OnScheduled
  public void onScheduled(final ProcessContext context) throws IOException
  {

    if (distributedMapCacheClient == null)
    {
      distributedMapCacheClient = context.getProperty(SF_DISTRIB_MAP_CACHE)
                                         .asControllerService(
                                             DistributedMapCacheClient.class);

    }

    if (sfAuthService == null)
    {
      sfAuthService = context.getProperty(SALESFORCE_AUTH_SERVICE)
                             .asControllerService(
                                 SalesforceUserPassAuthentication.class);
    }

    if (!isRunning)
    {
      isRunning = true;
      String topic = context.getProperty(SF_PUSH_TOPIC).getValue();

      conn = getEmpConnector();
      if (!conn.isConnected())
      {
        conn.start();

      }

      // LPPM - The conn / bayeux creates a separate thread under the covers.  The consumer receives
      // the messages as they arrive from Salesforce, and adds them into a Json queue.

      if (distributedMapCacheClient == null ||
          !(distributedMapCacheClient.containsKey("salesforce-replayId", serializer)))
      {
        conn.subscribeEarliest(topic, consumer);

      }
      else
      {
        String replayIdStr = distributedMapCacheClient.get("salesforce-replayId", serializer, deserializer);
        Long   replayId    = Long.parseLong(replayIdStr);
        conn.subscribe(topic, replayId, consumer);
      }

    }
  }

  @Override
  public void onTrigger(final ProcessContext context, final ProcessSession session) throws ProcessException
  {
    final FlowFile flowFile = session.get();



    getLogger().info("Draining JSONObject queue");
    final List<JSONObject> objects = new ArrayList<>(10000);
    jsonQueue.drainTo(objects, 10000);
    if (objects.isEmpty())
    {
      if (flowFile != null)
      {
        session.transfer(flowFile, REL_SUCCESS);
      }
      return;
    }
    else
    {
      Long lastReplayId = -1L;
      //Process all of the files.
      for (final JSONObject obj : objects)
      {

        Long       replayId          = obj.getJSONObject("event").getLong("replayId");
        JSONObject payload           = obj.getJSONObject("payload");
        JSONObject changeEventHeader = payload.getJSONObject("ChangeEventHeader");
        payload.remove("ChangeEventHeader");
        obj.put("ChangeEventHeader", changeEventHeader);
        String changeType = changeEventHeader.getString("changeType");
        String entityName = changeEventHeader.getString("entityName");
        String recordIds  = changeEventHeader.getJSONArray("recordIds").toString();

        // LPPM - for some reason, we are getting duplicate entries here.  Use this to dedupe for now.
        if (lastReplayId.longValue() != replayId)
        {
          lastReplayId = replayId;
          FlowFile ff = (flowFile == null) ? session.create() : session.create(flowFile);

          final String origEventStr = obj.toString();
          ff = session.write(ff, outputStream -> outputStream.write(origEventStr.getBytes()));
          ff = session.putAttribute(ff, "replayId", replayId.toString());
          ff = session.putAttribute(ff, "changeType", changeType);
          ff = session.putAttribute(ff, "entityName", entityName);
          ff = session.putAttribute(ff, "pg_recordIds", recordIds);
          ff = session.putAttribute(ff, "pg_origEventJson", origEventStr);

          session.transfer(ff, REL_SUCCESS);
        }
      }
    }
    if (flowFile != null)
    {
      session.remove(flowFile);
    }
  }

  @OnStopped
  public void stopped()
  {
    isRunning = false;
    if (this.conn != null && this.conn.isConnected())
    {
      this.conn.stop();
    }

    //    eventLoop.shutdown();
  }

}