package es.altia.bne.cron.jobs;

import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.List;

import javax.mail.MessagingException;
import javax.mail.internet.AddressException;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import es.altia.bne.comun.util.date.DateUtils;
import es.altia.bne.comun.util.mail.ClienteEmail;
import es.altia.bne.model.entities.dto.AfcPerGirosDto;
import es.altia.bne.model.entities.dto.BneAuditoriaIntegracionDto;
import es.altia.bne.model.entities.dto.auditoria.AuditoriaProcesaCertificacionesDto;
import es.altia.bne.model.entities.enumerados.AccionAuditoriaIntegracionEnum;
import es.altia.bne.model.entities.enumerados.ResultadoIntegracionEnum;
import es.altia.bne.service.IAFCCertificacionesService;
import es.altia.bne.service.exception.ServiceException;

@Component("jobProcesaCertificaciones")
@Scope("prototype")
public class ProcesaCertificacionesJob implements Job {

    @Autowired
    IAFCCertificacionesService afcCertificacionesService;

    private boolean periodoCertificacion;
    private int diaInicioPeriodoValidacion;
    private int mesInicioPeriodoValidacion;
    private int diaFinPeriodoValidacion;
    private int mesFinPeriodoValidacion;
    private int diaInicioPeriodoValidacionPrimerGiro;
    private int mesInicioPeriodoValidacionPrimerGiro;
    private int diaFinPeriodoValidacionPrimerGiro;
    private int mesFinPeriodoValidacionPrimerGiro;

    private Long idProceso;
    private int totalRegistrosAProcesar;
    private int totalRegistrosProcesados;
    private int totalRegistrosValidos;
    private int totalRegistrosRechazados;
    private int totalRegistrosErroneos;
    private int numeroRegistrosParaActualizar = 1;

    private String smtpHost;
    private int smtpPort;
    private String smtpUser;
    private String smtpPassword;
    private String smtpNtlmdomain;
    private String smtpAuthType;
    private String mailFrom;
    private String mailTo;

    private static final String AUTH_TYPE_USERPASS = "USERPASS";
    private static final String AUTH_TYPE_NTLM = "NTLM";

    private final String asuntoCorreo = "Ejecución del proceso de certificación";

    private Date fechaEjecucion; // Usado para pruebas

    protected final Logger log = LoggerFactory.getLogger(this.getClass());

    @Override
    public void execute(final JobExecutionContext context) throws JobExecutionException {

        this.log.info("execute job ProcesaCertificaciones INIT[]");
        long time_start = 0;
        long time_end = 0;
        String textoCorreo = "";

        ResultadoIntegracionEnum resultadoJob = ResultadoIntegracionEnum.ERROR;
        final AuditoriaProcesaCertificacionesDto xmlAuditoria = new AuditoriaProcesaCertificacionesDto();

        try {

            time_start = System.currentTimeMillis();
            this.log.info("FASE 1 - INICIALIZACION DEL PROCESO");

            if (this.fechaEjecucion != null) {
                this.log.warn("Se ha establecido como fecha de ejecucion del proceso: " + this.fechaEjecucion);
                this.afcCertificacionesService.establecerFechaDeEjecucion(this.fechaEjecucion);
            }

            this.idProceso = this.afcCertificacionesService.inicializarProcesoCertificaciones(this.diaInicioPeriodoValidacion,
                    this.diaFinPeriodoValidacion, this.mesInicioPeriodoValidacion, this.mesFinPeriodoValidacion);
            this.log.info("Inicio del proceso, identificador: " + this.idProceso);
            xmlAuditoria.setIdProceso(this.idProceso);

            this.log.info("FASE 2 - OBTENCION DE LOS GIROS A TRATAR");
            final List<AfcPerGirosDto> listaGirosAProcesar = this.afcCertificacionesService.obtenerGirosAProcesar(this.periodoCertificacion,
                    this.diaInicioPeriodoValidacion, this.diaFinPeriodoValidacion, this.mesInicioPeriodoValidacion,
                    this.mesFinPeriodoValidacion, this.diaInicioPeriodoValidacionPrimerGiro, this.diaFinPeriodoValidacionPrimerGiro,
                    this.mesInicioPeriodoValidacionPrimerGiro, this.mesFinPeriodoValidacionPrimerGiro);
            this.totalRegistrosAProcesar = listaGirosAProcesar.size();
            this.totalRegistrosProcesados = 0;
            this.totalRegistrosValidos = 0;
            this.totalRegistrosRechazados = 0;
            this.totalRegistrosErroneos = 0;
            this.afcCertificacionesService.actualizarProcesoCertificacion(this.idProceso, this.totalRegistrosAProcesar,
                    this.totalRegistrosProcesados, this.totalRegistrosValidos, this.totalRegistrosRechazados, this.totalRegistrosErroneos);
            this.log.info("TOTAL DE REGISTROS A PROCESAR: " + this.totalRegistrosAProcesar);

            this.log.info("FASE 3- PROCESAMIENTO DE LOS GIROS");

            if (listaGirosAProcesar != null && listaGirosAProcesar.size() > 0) {

                for (final AfcPerGirosDto giro : listaGirosAProcesar) {

                    final String resultado = this.afcCertificacionesService.procesarGiro(giro, this.idProceso, this.periodoCertificacion,
                            this.diaFinPeriodoValidacionPrimerGiro, this.diaFinPeriodoValidacionPrimerGiro, this.mesInicioPeriodoValidacion,
                            this.mesFinPeriodoValidacion);
                    switch (resultado) {

                    case IAFCCertificacionesService.GIRO_VALIDO:
                        this.totalRegistrosValidos = this.totalRegistrosValidos + 1;
                        break;
                    case IAFCCertificacionesService.GIRO_RECHAZADO:
                        this.totalRegistrosRechazados = this.totalRegistrosRechazados + 1;
                        break;
                    case IAFCCertificacionesService.GIRO_ERRONEO:
                        this.totalRegistrosErroneos = this.totalRegistrosErroneos + 1;
                        break;
                    }
                    this.totalRegistrosProcesados = this.totalRegistrosProcesados + 1;

                    if (this.totalRegistrosProcesados % this.numeroRegistrosParaActualizar == 0) {

                        try {
                            this.afcCertificacionesService.actualizarProcesoCertificacion(this.idProceso, this.totalRegistrosAProcesar,
                                    this.totalRegistrosProcesados, this.totalRegistrosValidos, this.totalRegistrosRechazados,
                                    this.totalRegistrosErroneos);
                            this.log.info("TOTAL DE REGISTROS PROCESADOS: " + this.totalRegistrosProcesados);
                        } catch (final Exception error) {
                            this.log.warn("No se ha podido actualizar el estado del proceso", error);
                        }
                    }
                }
            }

            this.log.info("FASE 6 - GENERACION DEL FICHERO DE RESULTADOS");

            this.afcCertificacionesService.generarFicheroResultado(this.idProceso, this.totalRegistrosProcesados,
                    this.totalRegistrosRechazados, this.totalRegistrosErroneos);

            this.log.info("FASE 5 - FINAL DEL PROCESO");
            this.afcCertificacionesService.finalizarProcesoCertificacion(this.idProceso, this.totalRegistrosAProcesar,
                    this.totalRegistrosProcesados, this.totalRegistrosValidos, this.totalRegistrosRechazados, this.totalRegistrosErroneos,
                    true);

            this.log.info("FINALIZACION DEL PROCESO");
            this.log.info("========================");
            this.log.info("TOTAL REGISTROS A PROCESAR : " + this.totalRegistrosAProcesar);
            this.log.info("TOTAL REGISTROS PROCESADOS : " + this.totalRegistrosProcesados);
            this.log.info("TOTAL REGISTROS VALIDOS    : " + this.totalRegistrosValidos);
            this.log.info("TOTAL REGISTROS RECHAZADOS : " + this.totalRegistrosRechazados);
            this.log.info("TOTAL REGISTROS ERRONEOS   : " + this.totalRegistrosErroneos);
            time_end = System.currentTimeMillis();
            this.log.info("TIEMPO DE EJECUCION DEL PROCESO [" + (time_end - time_start) + " ms" + "]");

            xmlAuditoria.setRegistrosAProcesar(this.totalRegistrosAProcesar);
            xmlAuditoria.setRegistrosErroneos(this.totalRegistrosErroneos);
            xmlAuditoria.setRegistrosProcesados(this.totalRegistrosProcesados);
            xmlAuditoria.setRegistrosRechazados(this.totalRegistrosRechazados);
            xmlAuditoria.setRegistrosValidos(this.totalRegistrosValidos);

            // TODO El texto debería ir en el mail service con plantillas
            textoCorreo = "<html><body><p>Se ha ejecutado correctamente el proceso de certificacion de giros</p>"
                    + "<p>El resultado ha sido</p>" + "<ul><li>TOTAL REGISTROS A PROCESAR: " + this.totalRegistrosAProcesar + "</li>"
                    + "<li>TOTAL REGISTROS PROCESADOS: " + this.totalRegistrosProcesados + "</li>" + "<li>TOTAL REGISTROS VALIDOS: "
                    + this.totalRegistrosValidos + "</li>" + "<li>TOTAL REGISTROS RECHAZADOS: " + this.totalRegistrosRechazados + "</li>"
                    + "<li>TOTAL REGISTROS ERRONEOS: " + this.totalRegistrosErroneos + "</li>" + "</ul>"
                    + "<p>TIEMPO DE EJECUCION DEL PROCESO [" + (time_end - time_start) + " ms" + "]</p>" + "</body></html>";
            resultadoJob = ResultadoIntegracionEnum.OK;
        } catch (final ServiceException e) {
            this.log.error("Error al ejecutar el procesos de certificaciones", e);
            try {
                if (this.idProceso != null) {
                    this.afcCertificacionesService.finalizarProcesoCertificacion(this.idProceso, this.totalRegistrosAProcesar,
                            this.totalRegistrosProcesados, this.totalRegistrosValidos, this.totalRegistrosRechazados,
                            this.totalRegistrosErroneos, false);
                }
            } catch (final Exception error) {
                this.log.warn("No se ha podido marcar el proceso como erroneo", error);
            }

            textoCorreo = "<html><body><p>Se ha producido un error durante la ejecución del proceso:[ " + e.getMessage()
                    + "], consulte el fichero de logs para más información. </p></body></html>";

            resultadoJob = ResultadoIntegracionEnum.ERROR;
            xmlAuditoria.setTrazaExcepcion(e);
            xmlAuditoria.setResultado(resultadoJob);

        } catch (final Exception error) {
            this.log.error("Error al ejecutar el procesos de certificaciones", error);
            try {
                this.afcCertificacionesService.finalizarProcesoCertificacion(this.idProceso, this.totalRegistrosAProcesar,
                        this.totalRegistrosProcesados, this.totalRegistrosValidos, this.totalRegistrosRechazados,
                        this.totalRegistrosErroneos, false);
            } catch (final Exception err) {
                this.log.warn("No se ha podido marcar el proceso como erroneo", err);
            }

            textoCorreo = "<p>Se ha producido un error durante la ejecución del proceso:[ " + error.getMessage() + "</p>";

            resultadoJob = ResultadoIntegracionEnum.ERROR;
            xmlAuditoria.setTrazaExcepcion(error);
            xmlAuditoria.setResultado(resultadoJob);

        } finally {

            try {
                this.enviarCorreo(textoCorreo);
            } catch (final Exception e) {
                this.log.error("Error al enviar el mail del proceso de certificaciones", e);
                // Do nothing else
            }

            // Grabamos auditoría
            final BneAuditoriaIntegracionDto<AuditoriaProcesaCertificacionesDto> a = new BneAuditoriaIntegracionDto<>();
            a.setAccion(AccionAuditoriaIntegracionEnum.IMPORTACION_OFERTAS_EMPLEO_CIVIL);
            a.setFecha(DateUtils.now());
            a.setResultado(resultadoJob);
            a.setDatosXml(xmlAuditoria);
            context.setResult(a);
        }

        this.log.info("execute job ProcesaCertificaciones END[]");

    }

    public void setAfcCertificacionesService(final IAFCCertificacionesService afcCertificacionesService) {
        this.afcCertificacionesService = afcCertificacionesService;
    }

    public Date getFechaEjecucion() {
        return this.fechaEjecucion;
    }

    public void setFechaEjecucion(final Date fechaEjecucion) {
        this.fechaEjecucion = fechaEjecucion;
    }

    public int getNumeroRegistrosParaActualizar() {
        return this.numeroRegistrosParaActualizar;
    }

    public void setNumeroRegistrosParaActualizar(final int numeroRegistrosParaActualizar) {
        this.numeroRegistrosParaActualizar = numeroRegistrosParaActualizar;
    }

    public int getDiaFinPeriodoValidacion() {
        return this.diaFinPeriodoValidacion;
    }

    public void setDiaFinPeriodoValidacion(final int diaFinPeriodoValidacion) {
        this.diaFinPeriodoValidacion = diaFinPeriodoValidacion;
    }

    public int getDiaInicioPeriodoValidacion() {
        return this.diaInicioPeriodoValidacion;
    }

    public void setDiaInicioPeriodoValidacion(final int diaInicioPeriodoValidacion) {
        this.diaInicioPeriodoValidacion = diaInicioPeriodoValidacion;
    }

    public String getSmtpHost() {
        return this.smtpHost;
    }

    public void setSmtpHost(final String smtpHost) {
        this.smtpHost = smtpHost;
    }

    public int getSmtpPort() {
        return this.smtpPort;
    }

    public void setSmtpPort(final int smtpPort) {
        this.smtpPort = smtpPort;
    }

    public String getSmtpUser() {
        return this.smtpUser;
    }

    public void setSmtpUser(final String smtpUser) {
        this.smtpUser = smtpUser;
    }

    public String getSmtpPassword() {
        return this.smtpPassword;
    }

    public void setSmtpPassword(final String smtpPassword) {
        this.smtpPassword = smtpPassword;
    }

    public String getMailFrom() {
        return this.mailFrom;
    }

    public void setMailFrom(final String mailFrom) {
        this.mailFrom = mailFrom;
    }

    public String getMailTo() {
        return this.mailTo;
    }

    public void setMailTo(final String mailTo) {
        this.mailTo = mailTo;
    }

    public void setSmtpNtlmdomain(final String smtpNtlmdomain) {
        this.smtpNtlmdomain = smtpNtlmdomain;
    }

    public void setSmtpAuthType(final String smtpAuthType) {
        this.smtpAuthType = smtpAuthType;
    }

    public boolean getPeriodoCertificacion() {
        return this.periodoCertificacion;
    }

    public void setPeriodoCertificacion(final boolean periodoCertificacion) {
        this.periodoCertificacion = periodoCertificacion;
    }

    public int getMesInicioPeriodoValidacion() {
        return this.mesInicioPeriodoValidacion;
    }

    public void setMesInicioPeriodoValidacion(final int mesInicioPeriodoValidacion) {
        this.mesInicioPeriodoValidacion = mesInicioPeriodoValidacion;
    }

    public int getMesFinPeriodoValidacion() {
        return this.mesFinPeriodoValidacion;
    }

    public void setMesFinPeriodoValidacion(final int mesFinPeriodoValidacion) {
        this.mesFinPeriodoValidacion = mesFinPeriodoValidacion;
    }

    public int getMesInicioPeriodoValidacionPrimerGiro() {
        return this.mesInicioPeriodoValidacionPrimerGiro;
    }

    public void setMesInicioPeriodoValidacionPrimerGiro(final int mesInicioPeriodoValidacionPrimerGiro) {
        this.mesInicioPeriodoValidacionPrimerGiro = mesInicioPeriodoValidacionPrimerGiro;
    }

    public int getMesFinPeriodoValidacionPrimerGiro() {
        return this.mesFinPeriodoValidacionPrimerGiro;
    }

    public void setMesFinPeriodoValidacionPrimerGiro(final int mesFinPeriodoValidacionPrimerGiro) {
        this.mesFinPeriodoValidacionPrimerGiro = mesFinPeriodoValidacionPrimerGiro;
    }

    public int getDiaInicioPeriodoValidacionPrimerGiro() {
        return this.diaInicioPeriodoValidacionPrimerGiro;
    }

    public void setDiaInicioPeriodoValidacionPrimerGiro(final int diaInicioPeriodoValidacionPrimerGiro) {
        this.diaInicioPeriodoValidacionPrimerGiro = diaInicioPeriodoValidacionPrimerGiro;
    }

    public int getDiaFinPeriodoValidacionPrimerGiro() {
        return this.diaFinPeriodoValidacionPrimerGiro;
    }

    public void setDiaFinPeriodoValidacionPrimerGiro(final int diaFinPeriodoValidacionPrimerGiro) {
        this.diaFinPeriodoValidacionPrimerGiro = diaFinPeriodoValidacionPrimerGiro;
    }

    private void enviarCorreo(final String mensaje) {

        ClienteEmail ce = null;

        if (this.smtpAuthType.equals(AUTH_TYPE_USERPASS)) {

            ce = ClienteEmail.newClienteEmail().servidorSmtp(this.smtpHost, this.smtpPort).usarConexionSegura()
                    .conCredenciales(this.smtpUser, this.smtpPassword).de(this.mailFrom).para(this.mailTo).asunto(this.asuntoCorreo)
                    .cuerpo().textoHTML(mensaje).build();
        } else {

            if (this.smtpAuthType.equals(AUTH_TYPE_NTLM)) {
                ce = ClienteEmail.newClienteEmail().servidorSmtp(this.smtpHost, this.smtpPort).usarConexionInsegura()
                        .conCredencialesEnDominioNtlm(this.smtpUser, this.smtpPassword, this.smtpNtlmdomain).de(this.mailFrom)
                        .para(this.mailTo).asunto(this.asuntoCorreo).cuerpo().textoHTML(mensaje).build();
            }
        }

        try {
            if (this.smtpAuthType.equals(AUTH_TYPE_NTLM) || this.smtpAuthType.equals(AUTH_TYPE_USERPASS)) {
                ce.enviar();
            }
        } catch (final AddressException e) {
            this.log.warn("Error al enviar el correo del fin del proceso", e);
        } catch (final UnsupportedEncodingException e) {
            this.log.warn("Error al enviar el corro del fin del proceso", e);
        } catch (final MessagingException e) {
            this.log.warn("Error al enviar el corro del fin del proceso", e);
        }
    }

}
