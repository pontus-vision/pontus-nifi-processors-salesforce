package com.pontusvision.processors.salesforce;

import com.pontusvision.nifi.salesforce.SalesforceUserPassAuthentication;
import com.pontusvision.processors.salesforce.base.AbstractSalesforceRESTOperation;
import com.salesforce.emp.connector.BayeuxParameters;
import com.salesforce.emp.connector.EmpConnector;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.annotation.lifecycle.OnScheduled;
import org.apache.nifi.annotation.lifecycle.OnStopped;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.ProcessorInitializationContext;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.util.StandardValidators;
import org.cometd.client.BayeuxClient;
import org.cometd.client.transport.ClientTransport;
import org.cometd.client.transport.LongPollingTransport;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.json.JSONObject;

import javax.net.ssl.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

@Tags({ "salesforce", "streaming", "api", "PushTopic", "channel" })
//@TriggerWhenEmpty
//@InputRequirement(InputRequirement.Requirement.INPUT_FORBIDDEN)
@CapabilityDescription(
    "SalesforceStreamingAPI for subscribing to a Salesforce APEX PushTopic and receiving real time updates" +
        "based on the Salesforce defined SOQL setup in your Salesforce account.")
//@ReadsAttributes({ @ReadsAttribute(attribute = "", description = "") })
//@WritesAttributes({ @WritesAttribute(attribute = "", description = "") })
public class SalesforceStreamingTopicAPI
    extends AbstractSalesforceRESTOperation
{

  //  public static final PropertyDescriptor SF_PUSH_LONG_POLL_DURATION = new PropertyDescriptor
  //      .Builder().name("Salesforce Push Long Poll Duration")
  //                .description("Number of seconds for the Salesforce.com Push Long Poll Duration")
  //                .required(true)
  //                .defaultValue("30")
  //                .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
  //                .build();

  public static final PropertyDescriptor SF_PUSH_TOPIC = new PropertyDescriptor
      .Builder().name("Salesforce CometD Push Topic")
                .description("The Salesforce.com Push Topic to describe to")
                .defaultValue("AccountActivity")
                .required(true)
                .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
                .build();

  //  public static final PropertyDescriptor SF_PUSH_ENDPOINT = new PropertyDescriptor
  //      .Builder().name("Salesforce CometD Push Server Endpoint")
  //                .description("The Salesforce.com URL that is appended to your Salesforce Server instance")
  //                .defaultValue("/cometd/46.0")
  //                .required(true)
  //                .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
  //                .build();

  public static final Relationship REL_SUCCESS = new Relationship.Builder()
      .name("success")
      .description("SOQL was successfully created")
      .build();

  public static final Relationship REL_FAILURE = new Relationship.Builder()
      .name("failure")
      .description("failed to perform Salesforce API Streaming")
      .build();

  protected EmpConnector conn;

  private final BlockingQueue<JSONObject> jsonQueue = new LinkedBlockingQueue<>(10);

  protected ExecutorService eventLoop = Executors.newFixedThreadPool(1);

  protected volatile boolean timeToQuit = false;

  protected volatile boolean isRunning = false;

  @Override
  protected void init(final ProcessorInitializationContext context)
  {
    super.init(context);
    final List<PropertyDescriptor> descriptors = new ArrayList<>(this.descriptors);
    descriptors.add(SF_PUSH_TOPIC);
    //    descriptors.add(SF_PUSH_LONG_POLL_DURATION);
    //    descriptors.add(SF_PUSH_ENDPOINT);
    this.descriptors = Collections.unmodifiableList(descriptors);
    timeToQuit = false;
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
          throw new IllegalStateException(String.format("Unable to form URL for %s", sfAuthService.getApiConfig().getLoginEndpoint()));
        }
      }
    };

    HttpClient httpClient = new HttpClient(params.sslContextFactory());
    httpClient.getProxyConfiguration().getProxies().addAll(params.proxies());

    return new EmpConnector(params);
  }

  @OnScheduled
  public void onScheduled(final ProcessContext context)
  {

    if (sfAuthService == null)
    {
      sfAuthService = context.getProperty(SALESFORCE_AUTH_SERVICE)
                             .asControllerService(
                                 SalesforceUserPassAuthentication.class);
    }

    timeToQuit = false;
    if (!isRunning)
    {
      isRunning = true;
      eventLoop = Executors.newFixedThreadPool(1);

      //      int    timeoutValue        = context.getProperty(SF_PUSH_LONG_POLL_DURATION).asInteger() * 1000;
      //      String defaultPushEndpoint = context.getProperty(SF_PUSH_ENDPOINT).getValue();
      String topic = context.getProperty(SF_PUSH_TOPIC).getValue();

      eventLoop.execute(() -> {
            conn = getEmpConnector();
            if (!conn.isConnected())
            {
              conn.start();
            }
            Consumer<Map<String, Object>> consumer = event -> jsonQueue.offer(new JSONObject(event));

            conn.subscribeTip(topic, consumer);

            while (!timeToQuit){
              try
              {
                Thread.sleep(1000);
              }
              catch (InterruptedException e)
              {
                e.printStackTrace();
                timeToQuit = true;
                isRunning = false;
              }
            }

          }
      );
      //      eventLoop.execute(() -> {
      //        try
      //        {
      //          int    timeoutValue        = context.getProperty(SF_PUSH_LONG_POLL_DURATION).asInteger() * 1000;
      //          String defaultPushEndpoint = context.getProperty(SF_PUSH_ENDPOINT).getValue();
      //          String topic               = context.getProperty(SF_PUSH_TOPIC).getValue();
      //
      //          if (this.sfAuthService == null)
      //          {
      //            this.sfAuthService = context.getProperty(SALESFORCE_AUTH_SERVICE)
      //                                        .asControllerService(
      //                                            SalesforceUserPassAuthentication.class);
      //            setNoValidation();
      //
      //          }
      //
      //          this.bayeuxClient = getClient(timeoutValue, defaultPushEndpoint);
      //          this.bayeuxClient.handshake();
      //
      //          getLogger().info("Waiting for Salesforce.com CometD handshake");
      //          waitForHandshake(this.bayeuxClient, 60 * 1000, 1000);
      //
      //          getLogger().info("Subscribing to Topic: " + topic);
      //
      //          this.bayeuxClient.getChannel("/topic/" + topic).subscribe(new ClientSessionChannel.MessageListener()
      //          {
      //            @Override
      //            public void onMessage(ClientSessionChannel channel, Message message)
      //            {
      //              try
      //              {
      //                JSONObject jsonObject = new JSONObject(new JSONTokener(message.getJSON()));
      //                getLogger().info(jsonObject.toString(2));
      //                while (!jsonQueue.offer(jsonObject)){
      //                  try
      //                  {
      //                    Thread.sleep(100);
      //                  }
      //                  catch (InterruptedException e)
      //                  {
      //                  }
      //                }
      //              }
      //              catch (JSONException e)
      //              {
      //                e.printStackTrace();
      //              }
      //            }
      //          });
      //          getLogger().info("Waiting for streamed data from Force.com...");
      //          while (!timeToQuit)
      //          {
      //            // This infinite loop is for demo only, to receive streamed events
      //            // on the specified topic from Salesforce.com
      //            Thread.sleep(100);
      //
      //          }
      //
      //        }
      //        catch (Exception ex)
      //        {
      //          getLogger().error(ex.getMessage());
      //          //session.transfer(flowFile, REL_LOGIN_FAILURE);
      //        }
      //      }
      //
      //      );

    }
  }

  @Override
  public void onTrigger(final ProcessContext context, final ProcessSession session) throws ProcessException
  {

    final FlowFile flowFile = session.get();
    if (flowFile == null)
    {
      return;
    }

    getLogger().info("Draining JSONObject queue");
    final List<JSONObject> objects = new ArrayList<>(10);
    jsonQueue.drainTo(objects, 10);
    if (objects.isEmpty())
    {
      session.transfer(flowFile, REL_SUCCESS);
      return;
    }
    else
    {
      //Process all of the files.
      for (final JSONObject obj : objects)
      {
        FlowFile ff = session.write(session.create(), outputStream -> outputStream.write(obj.toString().getBytes()));

        session.transfer(ff, REL_SUCCESS);
      }
    }
  }

  @OnStopped
  public void stopped()
  {

    timeToQuit = true;
    isRunning = false;
    if (this.conn != null && this.conn.isConnected())
    {
      this.conn.stop();
    }

    eventLoop.shutdown();
  }


}