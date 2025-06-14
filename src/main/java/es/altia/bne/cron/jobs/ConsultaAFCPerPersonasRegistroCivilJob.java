package es.altia.bne.cron.jobs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.mail.MessagingException;

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

import es.altia.bne.comun.constantes.AfcGirosEstado;
import es.altia.bne.comun.enumerados.EstadosValidacionRCEnum;
import es.altia.bne.comun.util.date.DateUtils;
import es.altia.bne.model.entities.dto.BneAuditoriaIntegracionDto;
import es.altia.bne.model.entities.dto.BneEstadosValidacionRCDto;
import es.altia.bne.model.entities.dto.PerPersonasDto;
import es.altia.bne.model.entities.dto.auditoria.AuditoriaConsultaRegistroCivilDto;
import es.altia.bne.model.entities.dto.custom.RegistroCivilPersonaDto;
import es.altia.bne.model.entities.enumerados.AccionAuditoriaIntegracionEnum;
import es.altia.bne.model.entities.enumerados.ResultadoIntegracionEnum;
import es.altia.bne.model.exception.DataAccessException;
import es.altia.bne.service.IActualizarInfoAFCRegCivilService;
import es.altia.bne.service.IMailService;
import es.altia.bne.service.IPostulantesService;
import es.altia.bne.service.exception.FechasNacimientoPostulanteNoCoincideException;
import es.altia.bne.service.exception.PostulanteFallecidoException;
import es.altia.bne.service.exception.RegistroCivilMaxDailyTransactionsExceededException;
import es.altia.bne.service.model.IAfcSolicitudService;
import es.altia.bne.service.model.IPerPersonasService;
import es.altia.bne.service.registrocivil.IRegistroCivilService;

/**
 * Job que permite lanzar un intercambio de información con el RC a través de la tabla RC_INTERCAMBIO_BASICO
 *
 */
@Scope("prototype")
@Component("jobConsultaPerPersonasRegistroCivil")
@DisallowConcurrentExecution
public class ConsultaAFCPerPersonasRegistroCivilJob implements Job {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    IActualizarInfoAFCRegCivilService actualizarInfoRegCivilService;

    @Autowired
    IPerPersonasService perpersonasService;

    @Autowired
    private IPostulantesService postulanteService;

    @Autowired
    private IRegistroCivilService rcServiceProxy;

    @Autowired
    private IMailService mailService;

    @Autowired
    private IAfcSolicitudService afcSolicitudService;

    @Value("${bne.mail.to.errorValidacionAFC}")
    private String destinatariosEmail;

    @Override
    public void execute(final JobExecutionContext context) throws JobExecutionException {
        // TODO Auto-generated method stub
        ResultadoIntegracionEnum resultado = ResultadoIntegracionEnum.ERROR;
        AuditoriaConsultaRegistroCivilDto xmlAuditoria = null;
        final int totalPersonasOK = 0;
        final int totalPersonasNOOK = 0;
        int numPersonasProcesadas = 0;

        final Stopwatch sw = Stopwatch.createStarted();
        this.logger.info("Iniciando job: Reporte Consulta de Registro Civil");

        try {

            this.logger.info("Comenzando actualizacion datos registro civil.");
            List<PerPersonasDto> personasSinValidacionRC = new ArrayList<>();

            /**
             * Se recogen todas las personas con estado "Sin validar AFC(10)" o "Error en llamada AFC(-10)"
             */

            final List<Integer> listaEstadosAFC = new ArrayList<>(
                    Arrays.asList(EstadosValidacionRCEnum.SIN_VALIDAR_AFC.getId(), EstadosValidacionRCEnum.ERROR_LLAMADA_AFC.getId()));
            personasSinValidacionRC = this.perpersonasService.getByEstadoAFC(listaEstadosAFC);
            numPersonasProcesadas = personasSinValidacionRC.size();

            final Map<String, String> mapErrorValidacion = new HashMap<>();

            if (personasSinValidacionRC != null && personasSinValidacionRC.size() > 0) {

                for (final PerPersonasDto personaDto : personasSinValidacionRC) {
                    this.validacionYenvioMail(mapErrorValidacion, personaDto, totalPersonasOK, totalPersonasNOOK, 0);
                }

                // Envío de mail informativo para reclamar rectificación a AFC
                if (mapErrorValidacion.size() > 0) {
                    this.mailService.enviarUsuariosNoValidados(mapErrorValidacion, this.destinatariosEmail);
                }

            }

            /**
             * Se recogen todas las personas con estado "Preinscrito (12)" para envío de correo de suscripción en AFC"
             */

            final List<Integer> listaEstadoPreinscrito = new ArrayList<>(Arrays.asList(EstadosValidacionRCEnum.PREINSCRITO_AFC.getId()));
            final List<PerPersonasDto> preinscritoDto = this.perpersonasService.getByEstadoAFC(listaEstadoPreinscrito);

            for (final PerPersonasDto personaDto : preinscritoDto) {
                this.validacionYenvioMail(mapErrorValidacion, personaDto, totalPersonasOK, totalPersonasNOOK,
                        EstadosValidacionRCEnum.PREINSCRITO_AFC.getId());
            }

            // Escritura de Xml de Auditoria
            /*
             * Se debe de generar de momento una auditoria con los dos campos a ceros
             */
            xmlAuditoria = new AuditoriaConsultaRegistroCivilDto(totalPersonasOK, totalPersonasNOOK);
            this.logger.info("Proceso de envio de la Consulta de Registro Civil para personas registradas con AFC- Finaliza correctamente");
            resultado = ResultadoIntegracionEnum.OK;

        } catch (final Exception e) {
            this.logger.info(
                    "Proceso de envio de la Consulta de Registro Civil para personas registradas con AFC - Finaliza con error no controlado");
            xmlAuditoria = new AuditoriaConsultaRegistroCivilDto(totalPersonasOK, totalPersonasNOOK);
            xmlAuditoria.setTrazaExcepcion(e);
            xmlAuditoria.setDescripcionResultado(e.getMessage());
            resultado = ResultadoIntegracionEnum.ERROR;

        } finally {
            final BneAuditoriaIntegracionDto<AuditoriaConsultaRegistroCivilDto> a = new BneAuditoriaIntegracionDto<>();
            a.setPersistable(true);
            a.setAccion(AccionAuditoriaIntegracionEnum.ENVIO_CONSULTA_AFC_REGISTRO_CIVIL);
            a.setFecha(DateUtils.now());
            a.setResultado(resultado);
            a.setDatosXml(xmlAuditoria);
            context.setResult(a);

            sw.stop();
            final long elapsed = sw.elapsed(TimeUnit.MILLISECONDS);
            this.logger.info(
                    "Finalizado job: Reporte Consulta de Registro Civil para personas registradas con AFC. Tiempo total: {}s; Tiempo por persona: {}ms",
                    sw.elapsed(TimeUnit.SECONDS), (elapsed > 0 && numPersonasProcesadas > 0) ? (elapsed / numPersonasProcesadas) : "N/A");
        }
    }

    private void validacionYenvioMail(final Map<String, String> mapErrorValidacion, final PerPersonasDto personaDto, int totalPersonasOK,
            int totalPersonasNOOK, final int estadoRC) throws DataAccessException {
        try {
            final Stopwatch swRc = Stopwatch.createStarted();
            this.logger.info("Antes de llamar al WS para rut - {} ", personaDto.getNumDocumentos());
            Optional<RegistroCivilPersonaDto> respuestaServiceProxy = null;

            try {
                respuestaServiceProxy = this.rcServiceProxy.getInformacionPersonalAFC(Integer.valueOf(personaDto.getNumDocumento()),
                        personaDto.getDigitoVerificador(), Optional.ofNullable(personaDto.getFecNac()), true);
                swRc.stop();
                this.logger.info("Llamada al RC para {} realizada en {}ms", personaDto.getNumDocumentos(),
                        swRc.elapsed(TimeUnit.MILLISECONDS));
            } catch (final RegistroCivilMaxDailyTransactionsExceededException e) {
                this.logger.error(
                        "RegistroCivilMaxDailyTransactionsExceededException - " + personaDto.getNumDocumentos() + " - " + e.getMessage(),
                        e);
                // Ponemos error en llamada para que se recoja en siguiente proceso automático
                personaDto.setBneEstadosValidacionRC(new BneEstadosValidacionRCDto(EstadosValidacionRCEnum.ERROR_LLAMADA_AFC.getId()));
                // Pasamos a la siguiente persona
                return;

            } catch (final FechasNacimientoPostulanteNoCoincideException | PostulanteFallecidoException | NumberFormatException e) {
                totalPersonasNOOK++;
                // Log y seguimos con el siguiente bloque.
                this.logger.error("AFC_Error al validar en registro civil", e);
                // Guardamos en el map para enviar por correo a la mesa de ayuda
                mapErrorValidacion.put(personaDto.getNumDocumento() + personaDto.getDigitoVerificador(), e.getMessage());
                // se setean las solicitudes pendientes de esa persona en estado error
                this.setEstadoSolicitud(personaDto.getId(), AfcGirosEstado.ERROR_REGISTRO_CIVIL_FALLECIDO);
            } catch (final Exception e) {
                // se setean las solicitudes pendientes de esa persona en estado error//se setean los giros pendientes de esa
                // persona en estado error
                this.setEstadoSolicitud(personaDto.getId(), AfcGirosEstado.ERROR_REGISTRO_CIVIL_SIN_DATOS);
                this.logger.error("Error al tratar persona - " + personaDto.getNumDocumentos() + " -  " + e.getMessage(), e);
            }

            if (respuestaServiceProxy != null && respuestaServiceProxy.isPresent()) {
                // Si tiene fecha de defuncion se añade el estado a los giros pendientes de esa personay pasamos al siguiente
                if (respuestaServiceProxy.get().getFechaMuertePresunta().isPresent()) {
                    totalPersonasNOOK++;
                    mapErrorValidacion.put(personaDto.getNumDocumento() + personaDto.getDigitoVerificador(),
                            "Fecha Muerte Presunta: " + respuestaServiceProxy.get().getFechaMuertePresunta());
                    this.setEstadoSolicitud(personaDto.getId(), AfcGirosEstado.ERROR_REGISTRO_CIVIL_PRESUNTAMENTE_FALLECIDO);
                    personaDto
                            .setBneEstadosValidacionRC(new BneEstadosValidacionRCDto(EstadosValidacionRCEnum.ERROR_VALIDACION_AFC.getId()));
                    this.perpersonasService.actualizarEstadoRCPerPersonaAFC(personaDto);
                } else if (respuestaServiceProxy.get().getFechaDefuncion().isPresent()) {
                    totalPersonasNOOK++;
                    mapErrorValidacion.put(personaDto.getNumDocumento() + personaDto.getDigitoVerificador(),
                            "Fecha defuncion: " + respuestaServiceProxy.get().getFechaDefuncion());
                    this.setEstadoSolicitud(personaDto.getId(), AfcGirosEstado.ERROR_REGISTRO_CIVIL_FALLECIDO);
                    personaDto
                            .setBneEstadosValidacionRC(new BneEstadosValidacionRCDto(EstadosValidacionRCEnum.ERROR_VALIDACION_AFC.getId()));
                    this.perpersonasService.actualizarEstadoRCPerPersonaAFC(personaDto);
                } else {
                    totalPersonasOK++;
                    this.logger.info("AFC_Envío correo confirmación ");

                    // Envío correos para solicitantes ya inscritos
                    if (estadoRC == EstadosValidacionRCEnum.PREINSCRITO_AFC.getId()) {
                        this.mailService.enviarBienvenidaPaso3JobAFC(personaDto.getNombreCompleto(), personaDto.getEmail());
                    } else {
                        // Envío de correo de alta en la cuenta BNE, revisalo ya que hay que poner la cuenta HABILITADA
                        this.postulanteService.solicitarMailConfirmacionRegistro(personaDto);
                        this.mailService.enviarBienvenidaPaso2JobAFC(personaDto.getNombreCompleto(), personaDto.getNombre(),
                                personaDto.getEmail(), UUID.randomUUID().toString());
                    }
                    personaDto.setBneEstadosValidacionRC(new BneEstadosValidacionRCDto(EstadosValidacionRCEnum.VALIDADO.getId()));
                    this.perpersonasService.actualizarEstadoRCPerPersonaAFC(personaDto);
                    this.setEstadoSolicitud(personaDto.getId(), AfcGirosEstado.TRATADO);
                }

            } else {
                totalPersonasNOOK++;
                this.logger.error("No se obtenido una respuesta desde Ws o se ha presentado un error y el objeto de repsuesta esta a null");
                personaDto.setBneEstadosValidacionRC(new BneEstadosValidacionRCDto(EstadosValidacionRCEnum.ERROR_VALIDACION_AFC.getId()));
                this.perpersonasService.actualizarEstadoRCPerPersonaAFC(personaDto);
                this.setEstadoSolicitud(personaDto.getId(), AfcGirosEstado.ERROR_REGISTRO_CIVIL);
            }
        } catch (final MessagingException e) {
            this.setEstadoSolicitud(personaDto.getId(), AfcGirosEstado.ERROR_EMAIL_AVISO);
            // Log y seguimos con el siguiente bloque.
            this.logger.error("AFC_Error al enviar el mail de confirmación al documento " + personaDto.getNumDocumento(), e);
        } catch (final Exception e) {
            this.setEstadoSolicitud(personaDto.getId(), AfcGirosEstado.ERROR_REGISTRO_CIVIL);
            totalPersonasNOOK++;
            // Log y seguimos con el siguiente bloque.
            this.logger.error("AFC_Error al tratar bloque de personas", e);
            // Se modifica el campo estado error en validación
            personaDto.setBneEstadosValidacionRC(new BneEstadosValidacionRCDto(EstadosValidacionRCEnum.ERROR_VALIDACION_AFC.getId()));
            this.perpersonasService.actualizarEstadoRCPerPersonaAFC(personaDto);

        }
    }

    private void setEstadoSolicitud(final Long idPersona, final AfcGirosEstado resultadoRegistroCivil) {
        this.afcSolicitudService.cambioEstadoSolicitudAFC(idPersona, resultadoRegistroCivil.estado());

    }

}
