package es.altia.bne.cron.jobs;

import java.io.IOException;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.google.common.base.Stopwatch;
import com.google.common.base.Throwables;

import es.altia.bne.comun.util.date.DateUtils;
import es.altia.bne.comun.util.ftp.ClienteFTP;
import es.altia.bne.comun.util.ftp.ClienteFTP.FtpConnectionInfo;
import es.altia.bne.model.entities.dto.BneAuditoriaIntegracionDto;
import es.altia.bne.model.entities.dto.auditoria.AuditoriaCargaFicherosAFCDto;
import es.altia.bne.model.entities.dto.auditoria.FicheroAFCDto;
import es.altia.bne.model.entities.enumerados.AccionAuditoriaIntegracionEnum;
import es.altia.bne.model.entities.enumerados.ResultadoIntegracionEnum;
import es.altia.bne.service.IAFCService;
import es.altia.bne.service.afcgiros.AfcGirosPerPersonasMigrantesUpdater;
import es.altia.bne.service.afcgiros.AfcGirosPerPersonasNacionalesUpdater;
import es.altia.bne.service.exception.AFCFileException;

@DisallowConcurrentExecution
@Component("jobCargaFicherosAFC")
@Scope("prototype")
public class CargaFicherosAFCJob implements Job {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private IAFCService afcService;

    @Autowired
    private AfcGirosPerPersonasMigrantesUpdater afcGirosPerPersonasMigrantesUpdater;

    @Autowired
    private AfcGirosPerPersonasNacionalesUpdater afcGirosPerPersonasNacionalesUpdater;

    @Value("${bne.integraciones.afc.fichero.giros.formatonombre}")
    private String formatoNombreFicheroAfcGiros;

    @Value("${bne.integraciones.afc.fichero.giros.encoding}")
    private String encodingFicheroAfcGiros;

    @Value("${bne.integraciones.afc.fichero.girosextra.formatonombre}")
    private String formatoNombreFicheroAfcGirosExtra;

    @Value("${bne.integraciones.afc.fichero.girosextra.encoding}")
    private String encodingFicheroAfcGirosExtra;

    @Value("${bne.integraciones.afc.fichero.pagos.formatonombre}")
    private String formatoNombreFicheroAfcPagos;

    @Value("${bne.integraciones.afc.fichero.pagos.encoding}")
    private String encodingFicheroAfcPagos;

    @Value("${bne.integraciones.afc.ftp.host}")
    private String host;

    @Value("${bne.integraciones.afc.ftp.puerto}")
    private Integer puerto;

    @Value("${bne.integraciones.afc.ftp.user}")
    private String user;

    @Value("${bne.integraciones.afc.ftp.password}")
    private String password;

    @Value("${bne.integraciones.afc.ftp.localdir}")
    private String localDir;

    @Value("${bne.integraciones.afc.ftp.remotedir}")
    private String remoteDir;

    @Override
    public void execute(final JobExecutionContext context) throws JobExecutionException {
        final AuditoriaCargaFicherosAFCDto xmlAuditoria = new AuditoriaCargaFicherosAFCDto();
        xmlAuditoria.setResultado(null); // TODO revisar para que el constructor tenga para todos los jobs el mismo valor
        try {
            final Stopwatch sw = Stopwatch.createStarted();
            this.logger.info("Iniciando JOB de carga de ficheros AFC");
            // Fichero AFC Giros
            FicheroAFCDto fichero = this.procesarAFCGiros();
            if (fichero.getResultado().equals(ResultadoIntegracionEnum.ERROR)) {
                xmlAuditoria.setResultado(ResultadoIntegracionEnum.ERROR);
            }
            xmlAuditoria.addFichero(fichero);

            // Fichero AFC Giros Extra
            fichero = this.procesarAFCGirosExtra();
            if (fichero.getResultado().equals(ResultadoIntegracionEnum.ERROR)) {
                xmlAuditoria.setResultado(ResultadoIntegracionEnum.ERROR);
            }
            xmlAuditoria.addFichero(fichero);

            // Fichero AFC Pagos
            fichero = this.procesarAFCPagos();
            if (fichero.getResultado().equals(ResultadoIntegracionEnum.ERROR)) {
                xmlAuditoria.setResultado(ResultadoIntegracionEnum.ERROR);
            }
            xmlAuditoria.addFichero(fichero);

            this.logger.info("Inicio llamada Preinscripción migrantes");
            this.afcGirosPerPersonasMigrantesUpdater.updatePerPersonasFromAfcGiros(xmlAuditoria);
            this.logger.info("Fin llamada Preinscripción migrantes");

            this.logger.info("Inicio llamada Preinscripción nacionales");
            this.afcGirosPerPersonasNacionalesUpdater.updatePerPersonasFromAfcGiros(xmlAuditoria);
            this.logger.info("Fin llamada Preinscripción nacionales");

            // Si nada se ha roto por el camino, todo ok:
            if (xmlAuditoria.getResultado() == null) {
                xmlAuditoria.setResultado(ResultadoIntegracionEnum.OK);
            }

            if (this.logger.isDebugEnabled()) {
                this.logger.info("Fin JOB de carga de ficheros AFC ({}ms)",
                        NumberFormat.getInstance().format(sw.elapsed(TimeUnit.MILLISECONDS)));
            }

        } finally {
            // Añadimos auditoría a contexto
            // El resultado debe venir de los pasos anteriores, pero hay casos de error en los que no se setea bien
            final BneAuditoriaIntegracionDto<AuditoriaCargaFicherosAFCDto> a = new BneAuditoriaIntegracionDto<>();
            a.setAccion(AccionAuditoriaIntegracionEnum.CARGA_FICHEROS_AFC);
            a.setFecha(DateUtils.now());
            a.setDatosXml(xmlAuditoria);
            a.setToReport(true);

            if (a.getResultado() == null) {
                a.setResultado(ResultadoIntegracionEnum.ERROR);
            }

            context.setResult(a);
        }

    }

    private FicheroAFCDto procesarAFCGiros() {
        final FicheroAFCDto fichero = new FicheroAFCDto();
        try {
            // Carga datos para procesado
            this.logger.info("Inicio procesado de AFC Giros");
            final Date fechaAyer = DateUtils.getTodayPlus(Calendar.DAY_OF_MONTH, -1);
            final String nombreFichero = DateUtils.getDateFormatted(fechaAyer, this.formatoNombreFicheroAfcGiros);

            // Descarga de fichero
            final String rutaFichero = this.descargarFicheroAFC(nombreFichero);

            // Procesado de fichero
            final int girosTratados;
            girosTratados = this.afcService.leerFicheroAFCGiros(rutaFichero, this.encodingFicheroAfcGiros);
            this.logger.info("Fin procesado de AFC Giros - OK");

            // Datos auditoria
            fichero.setRutaFichero(rutaFichero.concat(nombreFichero));
            fichero.setNumGirosTratados(girosTratados);
            fichero.setResultado(ResultadoIntegracionEnum.OK);
        } catch (final Exception e) {
            fichero.setResultado(ResultadoIntegracionEnum.ERROR);
            fichero.setDescripcionResultado("Error al procesar el fichero de la AFC");
            // fichero.setTrazaExcepcion(e); // TODO << ver si se serializa bien !!!!!!!!
            this.logger.error("Error al procesar fichero AFC giros");
            this.logger.error("Excepción", Throwables.getRootCause(e));
        }
        return fichero;
    }

    private FicheroAFCDto procesarAFCGirosExtra() {
        final FicheroAFCDto fichero = new FicheroAFCDto();
        try {
            this.logger.info("Inicio procesado de AFC Giros Extra");
            // Carga datos para procesado
            final Date fechaAyer = DateUtils.getTodayPlus(Calendar.DAY_OF_MONTH, -1);
            final String nombreFichero = DateUtils.getDateFormatted(fechaAyer, this.formatoNombreFicheroAfcGirosExtra);

            // Descarga de fichero
            final String rutaFichero = this.descargarFicheroAFC(nombreFichero);

            // Procesado de fichero
            final int numGirosTratados = this.afcService.leerFicheroAFCGirosExtra(rutaFichero, this.encodingFicheroAfcGirosExtra);

            // Datos auditoria
            fichero.setRutaFichero(rutaFichero.concat(nombreFichero));
            fichero.setNumGirosTratados(numGirosTratados);
            fichero.setResultado(ResultadoIntegracionEnum.OK);
            this.logger.info("Fin procesado de AFC Giros Extra - OK");
        } catch (final Exception e) {
            fichero.setResultado(ResultadoIntegracionEnum.ERROR);
            fichero.setDescripcionResultado("Error al procesar el fichero de la AFC");
            // fichero.setTrazaExcepcion(e); // TODO << ver si se serializa bien !!!!!!!!
            this.logger.error("Error al procesar fichero AFC giros extra");
            this.logger.error("Excepción", Throwables.getRootCause(e));
        }
        return fichero;
    }

    private FicheroAFCDto procesarAFCPagos() {
        final FicheroAFCDto fichero = new FicheroAFCDto();
        try {
            this.logger.info("Inicio procesado de AFC Pagos");
            final Date fechaAyer = DateUtils.getTodayPlus(Calendar.DAY_OF_MONTH, -1);
            final String nombreFichero = DateUtils.getDateFormatted(fechaAyer, this.formatoNombreFicheroAfcPagos);
            final String rutaFichero = this.descargarFicheroAFC(nombreFichero);
            final int numGirosTratados = this.afcService.leerFicheroAFCPagos(rutaFichero, this.encodingFicheroAfcPagos);

            // Datos auditoría
            fichero.setRutaFichero(rutaFichero.concat(nombreFichero));
            fichero.setNumGirosTratados(numGirosTratados);
            fichero.setResultado(ResultadoIntegracionEnum.OK);
            this.logger.info("Fin procesado de AFC Giros Pagos - OK");
        } catch (final Exception e) {
            fichero.setResultado(ResultadoIntegracionEnum.ERROR);
            fichero.setDescripcionResultado("Error al procesar el fichero de la AFC");
            // fichero.setTrazaExcepcion(e); // TODO << ver si se serializa bien !!!!!!!!
            this.logger.error("Error al procesar fichero AFC pagos");
            this.logger.error("Excepción", Throwables.getRootCause(e));
        }
        return fichero;
    }

    /**
     * Descarga del FTP el fichero AFC con el nombre pasado como parámetro. Devuelve el path al fichero descargado.
     *
     * @param nombreFichero
     *            nombre del fichero
     *
     * @return path al fichero descargado.
     *
     * @throws AFCFileException
     *             excepción si no ha sido posible descargar el fichero
     */
    private String descargarFicheroAFC(final String nombreFichero) throws AFCFileException {
        this.logger.debug("Descargando fichero {}", nombreFichero);
        final String rutaOrigen = this.remoteDir + "/" + nombreFichero;
        final String rutaLocal = this.localDir + "/" + nombreFichero;

        final FtpConnectionInfo connInfo = new FtpConnectionInfo(this.host, this.puerto, this.user, this.password);
        final ClienteFTP ftp = ClienteFTP.newClienteFTP();
        try {
            final Path path = ftp.descargaFichero(connInfo, rutaOrigen, rutaLocal);
            return path.toString();
        } catch (final IOException ioe) {
            this.logger.error("Error de IO con FTP AFC", ioe);
            throw new AFCFileException("No se ha podido descargar el fichero", ioe);
        }
    }

}
