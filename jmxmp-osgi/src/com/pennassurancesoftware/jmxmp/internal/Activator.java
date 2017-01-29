package com.pennassurancesoftware.jmxmp.internal;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import javax.management.MBeanServer;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jmx.remote.protocol.jmxmp.ServerProvider;

public class Activator implements BundleActivator {
   private State state = new State();

   public static class State {
      private final Optional<Agent.Running> agent;

      public State() {
         this( Optional.empty() );
      }

      public State with( Agent.Running agent ) {
         return new State( Optional.ofNullable( agent ) );
      }

      public State( Optional<Agent.Running> agent ) {
         this.agent = agent;
      }

      public State stop() {
         agent.ifPresent( a -> a.stop() );

         return this;
      }
   }

   /**
    * This exposes JMX access over JMXMP, suitable for high-security
    * environments, with support for going through firewalls as well as
    * encrypting and authenticating securely.
    * <p>
    * Listens on 11099 unless overridden by system property jmx.port.
    * <p>
    * Use the usual com.sun.management.jmxremote.ssl to enable both SSL _and_
    * authentication (setting jmx.ssl.authenticate false if you need
    * to disable authentication for some reason); unless you disable
    * client-side server authentication you will need to supply
    * jmx.ssl.keyStore, and similarly unless server-side client auth
    * is off you'll need the corresponding trustStore (both pointing to files
    * on the local file system).
    * <p>
    * Service comes up on: service:jmx:jmxmp://${HOSTNAME}:${PORT}
    * <p>
    * NB: To use JConsole with this endpoing, you need the jmxremote_optional
    * JAR, and the following command (even more complicated if using SSL): java
    * -classpath
    * $JAVA_HOME/lib/jconsole.jar:$HOME/.m2/repository/javax/management
    * /jmxremote_optional/1.0.1_04/jmxremote_optional-1.0.1_04.jar
    * sun.tools.jconsole.JConsole
    */
   public static abstract class Agent {
      private final static Logger LOG = LoggerFactory.getLogger( Agent.class );

      public static Default dflt() {
         return new Default();
      }

      public static FromContext from( BundleContext context ) {
         return new FromContext( context );
      }

      public static class Running {
         private final JMXConnectorServer connector;

         public Running( JMXConnectorServer connector ) {
            this.connector = connector;
         }

         public Running stop() {
            try {
               if( connector.isActive() ) {
                  connector.stop();
                  LOG.info( "JmxmpAgent stopped at: {}", connector.getAddress() );
               }
            }
            catch( Throwable exception ) {
               throw new RuntimeException(
                     String.format(
                           "Failed to start JMX Connector: %s",
                           connector.getAddress() ),
                     exception );
            }

            return this;
         }

         public Running start() {
            try {
               if( !connector.isActive() ) {
                  connector.start();
                  LOG.info( "JmxmpAgent active at: {}", connector.getAddress() );
               }
            }
            catch( Throwable exception ) {
               throw new RuntimeException(
                     String.format(
                           "Failed to start JMX Connector: %s",
                           connector.getAddress() ),
                     exception );
            }

            return this;
         }
      }

      public static class FromContext extends Agent {
         private final BundleContext context;

         public FromContext( BundleContext context ) {
            this.context = context;
         }

         private Config config() {
            return Config.from( context );
         }

         private Default from() {
            return Agent.dflt()
                  .withHost( config().host() )
                  .withPort( config().port() );
         }

         @Override
         public Running start() {
            return from().start();
         }
      }

      public static class Default extends Agent {
         private final Optional<Integer> port;
         private final Optional<String> host;

         public Default() {
            this( Optional.<String> empty(), Optional.<Integer> empty() );
         }

         public Default( Optional<String> host, Optional<Integer> port ) {
            this.host = host;
            this.port = port;
         }

         public Default withHost( String host ) {
            return new Default( Optional.ofNullable( host ), port );
         }

         public Default withPort( Integer port ) {
            return new Default( host, Optional.ofNullable( port ) );
         }

         private String host() {
            return host.orElseGet( ( ) -> defaults().host() );
         }

         private Config.Defaults defaults() {
            return Config.defaults();
         }

         private Integer port() {
            return port.orElse( defaults().port() );
         }

         // service:jmx:jmxmp://host:11099
         private JMXServiceURL url() {
            final String url = String.format( "service:jmx:jmxmp://%s:%s", host(), port() );
            try {
               return new JMXServiceURL( url );
            }
            catch( Throwable exception ) {
               throw new RuntimeException( String.format( "Failed to create JMX Service URL: %s", url ), exception );
            }
         }

         private Map<String, Object> env() {
            final Map<String, Object> env = new LinkedHashMap<String, Object>();
            env.put( JMXConnectorServerFactory.PROTOCOL_PROVIDER_CLASS_LOADER, getClass().getClassLoader() );

            //         Map<String, Object> env = new LinkedHashMap<String, Object>();
            //         propagate( properties, env,
            //               JMX_SERVER_ADDRESS_WILDCARD_PROPERTY, null );
            //
            //         if( asBoolean( properties, USE_SSL_PROPERTY, false, true ) ) {
            //            setSslEnvFromProperties( env, properties );
            //         }
            //         else {
            //            if( asBoolean( properties, AUTHENTICATE_CLIENTS_PROPERTY,
            //                  false, true ) ) {
            //               throw new IllegalStateException(
            //                     "Client authentication not supported when not using SSL" );
            //            }
            //         }

            return env;
         }

         private MBeanServer server() {
            return ManagementFactory.getPlatformMBeanServer();
         }

         private JMXConnectorServer connector() {
            try {
               ServerProvider.class.getName(); // OSGi HACK
               return JMXConnectorServerFactory.newJMXConnectorServer( url(), env(), server() );
            }
            catch( Throwable exception ) {
               throw new RuntimeException( "Failed to create JMX connector server factory", exception );
            }
         }

         @Override
         public Running start() {
            return new Running( connector() ).start();
         }
      }

      public abstract Running start();
   }

   public static abstract class Config {
      public static FromContext from( BundleContext context ) {
         return new FromContext( context );
      }

      public static Defaults defaults() {
         return new Defaults();
      }

      public static class Defaults extends Config {
         private final static Logger LOG = LoggerFactory.getLogger( Defaults.class );

         @Override
         public Integer port() {
            return 11099;
         }

         @Override
         public String host() {
            String result = "127.0.0.1";
            try {
               result = InetAddress.getLocalHost().getHostName();
            }
            catch( Throwable exception ) {
               LOG.error( "Misconfigured hostname when setting JmxmpAgent; reverting to 127.0.0.1", exception );
            }
            return result;
         }

      }

      public static class FromContext extends Config {
         private final BundleContext context;

         public FromContext( BundleContext context ) {
            this.context = context;
         }

         private Optional<ConfigurationAdmin> configAdmin() {
            final Optional<ServiceReference<ConfigurationAdmin>> ref = Optional.ofNullable( context.getServiceReference( ConfigurationAdmin.class ) );
            return ref.map( r -> context.getService( r ) );
         }

         private Optional<Configuration> conf() {
            final Function<ConfigurationAdmin, Configuration> toConf = new Function<ConfigurationAdmin, Configuration>() {
               @Override
               public Configuration apply( ConfigurationAdmin admin ) {
                  final String pid = "com.pennassurancesoftware.jmxmp.osgi";
                  try {
                     return admin.getConfiguration( pid );
                  }
                  catch( IOException exception ) {
                     throw new RuntimeException( String.format( "Failed to get configuration for service.pid: %s", pid ), exception );
                  }
               }
            };

            return configAdmin().map( toConf );
         }

         private Dictionary<String, Object> props() {
            return conf().map( c -> c.getProperties() ).orElse( new Hashtable<>() );
         }

         @Override
         public Integer port() {
            return Optional.ofNullable( ( String )props().get( "jmx.port" ) )
                  .map( s -> Integer.parseInt( s ) )
                  .orElseGet( ( ) -> defaults().port() );
         }

         @Override
         public String host() {
            return Optional.ofNullable( ( String )props().get( "jmx.host" ) )
                  .orElseGet( ( ) -> defaults().host() );
         }
      }

      public abstract Integer port();

      public abstract String host();
   }

   @Override
   public void start( BundleContext context ) throws Exception {
      state = state.with( Agent.from( context ).start() );
   }

   @Override
   public void stop( BundleContext context ) throws Exception {
      state = state.stop();
   }

}
