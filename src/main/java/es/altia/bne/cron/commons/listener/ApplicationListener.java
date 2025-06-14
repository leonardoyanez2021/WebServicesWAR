package es.altia.bne.cron.commons.listener;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Date;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;
import org.springframework.stereotype.Component;

import com.google.common.base.Strings;

@Component
public class ApplicationListener implements ServletContextListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationListener.class);

    private static final String INIT_MESSAGE = "Contexto iniciado para la aplicación {} a las {}";

    private static final String SEPARATOR = "\n\t\t\t\t\t==========================================================\n\t\t\t\t\t";

    @Override
    public void contextInitialized(final ServletContextEvent sce) {
        LOGGER.warn("Contexto iniciado para bnewws a las {}", new Date());
        this.applicationStartedActions(sce, INIT_MESSAGE);
    }

    @Override
    public void contextDestroyed(final ServletContextEvent sce) {
        LOGGER.warn("Contexto destruido para bnews a las {}", new Date());
    }

    //@formatter:off
    /**
     * Método que llevará a cabo las comprobaciones necesarias al inicio de la aplicación y que deberían provocar la parada
     * de la aplicación en caso de no ser correctas:
     *  <ul>
     *      <li>Conexión a base de datos</li>
     *      <li>Acceso a las carpetas necesarias</li>
     *      <li>Comunicación con sistemas externos imprescindibles</li>
     *      <li>...</li>
     *  </ul>
     *
     * @param event
     * @param message
     */
    private void applicationStartedActions(final ServletContextEvent sce, final String message) {
        // Puente para java.util.logging
         this.addSLF4JBridgeHandler();

        // Prueba la conexión a BD
        this.testBneConnectionPool();
        this.testQuartzConnectionPool();

        // TODO Logar la versión de la aplicación que se despliega.

        // TODO ¿ALGO MAS???????
        // ¿WS AFC?
        // ¿Carpetas de disco para FTPs?

        final String msg = new StringBuilder().append(SEPARATOR).append(message).append(SEPARATOR).toString();
        final String appName = Strings.isNullOrEmpty(sce.getServletContext().getServletContextName()) ? "/" : sce.getServletContext().getServletContextName();
        LOGGER.info(msg, appName, new Date());
    }

    /**
     * Puente entre j.u.l y slf4j utilizando el listener de SLF4j.
     */
    private void addSLF4JBridgeHandler() {
        // http://www.slf4j.org/api/org/slf4j/bridge/SLF4JBridgeHandler.html
        if (!SLF4JBridgeHandler.isInstalled()) {
            // Optionally remove existing handlers attached to j.u.l root logger
            SLF4JBridgeHandler.removeHandlersForRootLogger(); // (since SLF4J 1.6.5)

            // add SLF4JBridgeHandler to j.u.l's root logger, should be done once during
            // the initialization phase of your application
            SLF4JBridgeHandler.install();
        }
    }


    /**
     * Comprueba si se puede obtener el pool de conexiones usado en la aplicación y la BD funciona correctamente.
     */
    private void testBneConnectionPool() {
        /** Uses JNDI and Datasource (preferred style). */
        // TODO ¿Datasource a properties?
        final String datasourceName = "java:comp/env/jdbc/bneDS";
        this.testDataSource(datasourceName);
    }


    private DataSource testDataSource(final String jndiName) {
        Connection conn = null;
        PreparedStatement ps = null;
        DataSource dataSource = null;
        try {
            final Context initialContext = new InitialContext();
            dataSource = (DataSource) initialContext.lookup(jndiName);
            if (dataSource != null) {
                conn = dataSource.getConnection();

                final String dbName = conn.getMetaData().getDatabaseProductName();
                final String dbVersion = conn.getMetaData().getDatabaseProductVersion();

                ps = conn.prepareStatement("SELECT GETDATE()");
                final ResultSet rs = ps.executeQuery();
                rs.next();
                final String result = rs.getString(1);

                final String connectedMessage = "TEST BD: Conectado correctamente al servidor de base de datos: {} ({})";
                LOGGER.info(connectedMessage, dbName, dbVersion);
                LOGGER.info("Hora del servidor: {}", result);
                return dataSource;
            } else {
                final Error err = new Error("No se ha podido obtener el dataSource [" + jndiName + "] del contexto");
                LOGGER.error(jndiName, err);
                throw err;
            }
        } catch (final Exception ex) {
            final Error err = new Error("Error al probar el dataSource [" + jndiName + "]", ex);
            LOGGER.error(jndiName, ex);
            throw err;
        } finally {
            try {
                conn.close();
            } catch (final Exception e) {
                // Do nothing
            }
            try {
                ps.close();
            } catch (final Exception e) {
                // Do nothing
            }
        }

    }

    private void testQuartzConnectionPool() {
        /** Uses JNDI and Datasource (preferred style). */
        // TODO ¿Datasource a properties?
        final String datasourceName = "java:comp/env/jdbc/bneQuartzDS";

        final String errMessage = "TEST BD: No se puede conectar a la base de datos";

        final DataSource dataSource = this.testDataSource(datasourceName);

        // Borramos el contenido de las tablas QRTZ_TRIGGERS y QRTZ_JOB_DETAILS para que se regeneren en cada
        // reinicio de la aplicación
        Connection conn = null;
        try {
            conn = dataSource.getConnection();
            conn.prepareStatement("delete from qrtz_triggers").executeUpdate();
            conn.prepareStatement("delete from qrtz_job_details").executeUpdate();
            conn.commit();
        } catch (final Exception ex) {
            final Error err = new Error("Error al vaciar las tablas QUARTZ de BBDD: " + datasourceName, ex);
            LOGGER.error(errMessage, ex);
            throw err;
        } finally {
            try {
                conn.close();
            } catch (final Exception e) {
                // Do nothing
            }
        }
    }

}
