package com.keyur.dockermqpoco;


import com.ibm.msg.client.jms.JmsConnectionFactory;
import com.ibm.msg.client.jms.JmsFactoryFactory;
import com.ibm.msg.client.wmq.WMQConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.*;
import java.util.Arrays;
import java.util.Random;

/**
 * A minimal and simple application for Point-to-point messaging.
 * <p>
 * Application makes use of fixed literals, any customisations will require
 * re-compilation of this source file. Application assumes that the named queue
 * is empty prior to a run.
 * <p>
 * Notes:
 * <p>
 * API type: JMS API (v2.0, simplified domain)
 * <p>
 * Messaging domain: Point-to-point
 * <p>
 * Provider type: IBM MQ
 * <p>
 * Connection mode: Client connection
 * <p>
 * JNDI in use: No
 */
public class Application {
    private static final Logger logger = LoggerFactory.getLogger(Application.class);

    // Create variables for the connection to MQ
    private static final String LOCALHOST = "localhost"; // Host name or IP address
    private static final int PORT = 1414; // Listener port for your queue manager
    private static final String CHANNEL = "DEV.APP.SVRCONN"; // Channel name
    private static final String QMGR = "QM1"; // Queue manager name
    private static final String APP_USER = "app"; // User name that application uses to connect to MQ
    private static final String APP_PASSWORD = "passw0rd"; // Password that the application uses to connect to MQ
    private static final String QUEUE_NAME = "DEV.QUEUE.1"; // Queue that the application uses to put and get messages to and from

    private static final String PUB = "pub";
    private static final String SUB = "sub";
    private static final int MAX_PUB_MSG_COUNT = 100;
    public static final String MSG_PROP_SHARD = "shard";
    public static final String MSG_PROP_TASK_PRIORITY = "task_priority";
    public static final int[] SHARDS = {1, 2};

    // System exit status value (assume unset value to be 1)
    private static int status = 1;


    /**
     * Main method
     *
     * @param args
     */
    public static void main(String[] args) {
        logger.info("CMD Line Params = {}", Arrays.toString(args));

        String host = LOCALHOST;
        String role = SUB;
        int shard = 1;
        int priority = 0;

        for (int x = 0; x < args.length; x++) {
            String arg = args[x];
            if (("role".equalsIgnoreCase(arg) || "r".equalsIgnoreCase(arg)) && args.length > x + 1) {
                role = args[x + 1];
            } else if (("shard".equalsIgnoreCase(arg) || "s".equalsIgnoreCase(arg)) && args.length > x + 1) {
                shard = Integer.parseInt(args[x + 1]);
            } else if (("priority".equalsIgnoreCase(arg) || "p".equalsIgnoreCase(arg)) && args.length > x + 1) {
                priority = Integer.parseInt(args[x + 1]);
            }
        }

        //  Take Host from Env if available
        if (System.getenv().containsKey("QMGR_HOST"))
            host = System.getenv("QMGR_HOST");

        //  Role Validation
        if (!(PUB.equalsIgnoreCase(role) || SUB.equalsIgnoreCase(role))) {
            logger.error("Invalid role {}", role);
            System.exit(1);
        }

        try {
            // Create a connection factory
            JmsFactoryFactory ff = JmsFactoryFactory.getInstance(WMQConstants.WMQ_PROVIDER);
            JmsConnectionFactory cf = ff.createConnectionFactory();

            // Set the properties
            cf.setStringProperty(WMQConstants.WMQ_HOST_NAME, host);
            cf.setIntProperty(WMQConstants.WMQ_PORT, PORT);
            cf.setStringProperty(WMQConstants.WMQ_CHANNEL, CHANNEL);
            cf.setIntProperty(WMQConstants.WMQ_CONNECTION_MODE, WMQConstants.WMQ_CM_CLIENT);
            cf.setStringProperty(WMQConstants.WMQ_QUEUE_MANAGER, QMGR);
            cf.setStringProperty(WMQConstants.WMQ_APPLICATIONNAME, "Selective Listener");
            cf.setBooleanProperty(WMQConstants.USER_AUTHENTICATION_MQCSP, true);
            cf.setStringProperty(WMQConstants.USERID, APP_USER);
            cf.setStringProperty(WMQConstants.PASSWORD, APP_PASSWORD);

            // Create JMS objects
            final JMSContext context = cf.createContext();
            final Destination destination = context.createQueue("queue:///" + QUEUE_NAME);

            if (PUB.equalsIgnoreCase(role)) {
                logger.info("Starting Publisher");
                startPublisher(context, destination, priority);
            } else if (SUB.equalsIgnoreCase(role)) {
                startSubscriber(context, destination, shard, priority);
            }

        } catch (JMSException jmsex) {
            recordFailure(jmsex);
        }

        System.exit(status);

    } // end main()

    private static synchronized void startPublisher(JMSContext context, Destination destination, int priority) throws JMSException {
        int count = 0;
        Random random = new Random();
        while (true) {
            int shard = Application.SHARDS[random.nextInt(Application.SHARDS.length)];
            long uniqueNumber = System.currentTimeMillis() % 1000;
            TextMessage message = context.createTextMessage("Shard " + shard + " your lucky number today is " + uniqueNumber);
            message.setShortProperty(MSG_PROP_SHARD, (short) shard);
            message.setShortProperty(MSG_PROP_TASK_PRIORITY, (short) priority);
            JMSProducer producer = context.createProducer();
            producer.send(destination, message);
            System.out.println("Sent message #" + count++);
            System.out.println(message);
            System.out.println("\n***********************************************************************\n");
            try {
                Application.class.wait(5000);
            } catch (InterruptedException e) {
                logger.error("Interrupted.", e);
                break;
            }
        }
    }

    private static synchronized void startSubscriber(JMSContext context, Destination destination, int shard, int priority) {
        String selector = MSG_PROP_SHARD + " = " + shard + " and " + MSG_PROP_TASK_PRIORITY + " = " + priority;
        final JMSConsumer consumer = context.createConsumer(destination, selector, true); // auto closable
//        final JMSConsumer consumer = context.createConsumer(destination);
        while (true) {
//            String receivedMessage = consumer.receiveBody(String.class, 1000); // in ms or 15 seconds
            Message message = consumer.receive();
            String receivedMessage = null;
            try {
                receivedMessage = message.getBody(String.class);
            } catch (JMSException e) {
                logger.error("Failed to read message", e);
            }
            logger.info("{}", receivedMessage);
            try {
                Application.class.wait(5000);
            } catch (InterruptedException e) {
                logger.error("Interrupted.", e);
                break;
            }
        }
    }

    /**
     * Record this run as failure.
     *
     * @param ex
     */
    private static void recordFailure(Exception ex) {
        if (ex != null) {
            if (ex instanceof JMSException) {
                processJMSException((JMSException) ex);
            } else {
                System.out.println(ex);
            }
        }
        System.out.println("FAILURE");
        status = -1;
        return;
    }

    /**
     * Process a JMSException and any associated inner exceptions.
     *
     * @param jmsex
     */
    private static void processJMSException(JMSException jmsex) {
        System.out.println(jmsex);
        Throwable innerException = jmsex.getLinkedException();
        if (innerException != null) {
            System.out.println("Inner exception(s):");
        }
        while (innerException != null) {
            System.out.println(innerException);
            innerException = innerException.getCause();
        }
        return;
    }

}
