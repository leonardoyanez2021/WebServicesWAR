package es.altia.bne.cron.jobs;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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

import es.altia.bne.comun.util.date.DateUtils;
import es.altia.bne.model.entities.dto.BneAuditoriaIntegracionDto;
import es.altia.bne.model.entities.dto.auditoria.AuditoriaConsultaRegistroCivilDto;
import es.altia.bne.model.entities.dto.custom.RegistroCivilPersonaDto;
import es.altia.bne.model.entities.enumerados.AccionAuditoriaIntegracionEnum;
import es.altia.bne.model.entities.enumerados.ResultadoIntegracionEnum;
import es.altia.bne.model.entities.regcivil.RcIntercambioBasico;
import es.altia.bne.service.IActualizarInfoRegCivilService;
import es.altia.bne.service.exception.RegistroCivilMaxDailyTransactionsExceededException;
import es.altia.bne.service.registrocivil.IRegistroCivilService;

/**
 * Job que permite lanzar un intercambio de información con el RC a través de la tabla RC_INTERCAMBIO_BASICO
 *
 */
@Scope("prototype")
@Component("jobConsultaRegistroCivil")
@DisallowConcurrentExecution
public class ConsultaMasivaRegistroCivilJob implements Job {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    IActualizarInfoRegCivilService actualizarInfoRegCivilService;

    @Autowired
    IRegistroCivilService rcServiceProxy;

    private static final int PUNTO_CORTE_ACTUALIZACION_BD = 50;

    @Value("${bne.mail.to.otrasOcupaciones}")
    private String destinatariosEmail;

    @Override
    public void execute(final JobExecutionContext context) throws JobExecutionException {
        // TODO Auto-generated method stub
        ResultadoIntegracionEnum resultado = ResultadoIntegracionEnum.ERROR;
        AuditoriaConsultaRegistroCivilDto xmlAuditoria = null;
        int totalPersonasOK = 0;
        int totalPersonasNOOK = 0;
        int numPersonasProcesadas = 0;

        final Stopwatch sw = Stopwatch.createStarted();
        this.logger.info("Iniciando job: Reporte Consulta de Registro Civil");

        try {
            int contadorLlamadasErroneas = 0;
            int contadorPersonasNoActualizadas = 0;
            final LinkedList<String> rutsConProblemasTransaccion = new LinkedList<>();
            final LinkedList<String> rutsNoActualizados = new LinkedList<>();

            this.logger.info("Comenzando actualizacion datos registro civil.");
            List<RcIntercambioBasico> personasIB;
            List<RcIntercambioBasico> personasIBActualizacionParcial = new ArrayList<>();
            personasIB = this.actualizarInfoRegCivilService.getPersonasActualizarInfoIntercambioBasicoRC();

            if (personasIB != null && personasIB.size() > 0) {

                numPersonasProcesadas = personasIB.size();
                final Long ultimoIDdeLalista = personasIB.get(personasIB.size() - 1).getId();

                for (final RcIntercambioBasico pIB : personasIB) {
                    try {
                        final Stopwatch swRc = Stopwatch.createStarted();
                        this.logger.info("Antes de llamar al WS para rut - {} ", pIB.getRut());
                        Optional<RegistroCivilPersonaDto> respuestaServiceProxy = null;

                        try {
                            respuestaServiceProxy = this.rcServiceProxy.getInformacionPersonal(Integer.parseInt(pIB.getRut()),
                                    pIB.getDigitoVerificador(), Optional.ofNullable(pIB.getFechaNacimiento()), true);
                        } catch (final RegistroCivilMaxDailyTransactionsExceededException e) {
                            this.logger.error(
                                    "RegistroCivilMaxDailyTransactionsExceededException - " + pIB.getRut() + " - " + e.getMessage(), e);
                            rutsConProblemasTransaccion.add(pIB.getRut() + "-" + pIB.getDigitoVerificador());
                            contadorLlamadasErroneas++;

                        } catch (final Exception e) {
                            this.logger.error("Error al tratar persona - " + pIB.getRut() + " -  " + e.getMessage(), e);
                            contadorPersonasNoActualizadas++;
                            rutsNoActualizados.add(pIB.getRut() + "-" + pIB.getDigitoVerificador());
                        }

                        swRc.stop();
                        this.logger.info("Llamada al RC para {} realizada en {}ms", pIB.getRut(), swRc.elapsed(TimeUnit.MILLISECONDS));
                        if (respuestaServiceProxy != null && respuestaServiceProxy.isPresent()) {
                            final RegistroCivilPersonaDto respuestaServiceProxyDto = respuestaServiceProxy.get();

                            this.logger.info("-- Actualizamos en BD - ");
                            final RcIntercambioBasico personaIBUpdateTemp = RcIntercambioBasico
                                    .completeRcPersonaIBFromInformacionPersonal(pIB, respuestaServiceProxyDto);
                            personasIBActualizacionParcial.add(personaIBUpdateTemp);

                            // Si llegamos al limite de la lista para actualizacion invocamos
                            if (personasIBActualizacionParcial.size() == PUNTO_CORTE_ACTUALIZACION_BD || ultimoIDdeLalista == pIB.getId()) {
                                final Map<String, Object> ejemplo = this.actualizarInfoRegCivilService
                                        .procesarListaTemporalUpdate(personasIBActualizacionParcial);
                                contadorLlamadasErroneas = contadorLlamadasErroneas
                                        + (Integer) ejemplo.getOrDefault("contadorLlamadasErroneas", 0);
                                contadorPersonasNoActualizadas = contadorPersonasNoActualizadas
                                        + (Integer) ejemplo.getOrDefault("contadorPersonasNoActualizadas", 0);
                                if ((LinkedList<String>) ejemplo.get("rutsNoActualizados") != null) {
                                    rutsNoActualizados.addAll((LinkedList<String>) ejemplo.get("rutsNoActualizados"));
                                }
                                if ((LinkedList<String>) ejemplo.get("rutsConProblemasTransaccion") != null) {
                                    rutsConProblemasTransaccion.addAll((LinkedList<String>) ejemplo.get("rutsConProblemasTransaccion"));
                                }

                                // Reseteamos la ista
                                this.logger.info("-- Despues de Actualizar en BD por Lista - Identificador donde se corta : " + pIB.getRut()
                                        + "-" + personaIBUpdateTemp.getDigitoVerificador());
                                personasIBActualizacionParcial = new ArrayList<>();
                            }

                        } else {
                            this.logger.error(
                                    "No se obtenido una respuesta desde Ws o se ha presentado un error y el objeto de repsuesta esta a null");
                            contadorPersonasNoActualizadas++;
                        }
                    } catch (final Exception e) {
                        // Log y seguimos con el siguiente bloque.
                        this.logger.error("Error al tratar bloque de personas", e);
                        contadorPersonasNoActualizadas += personasIBActualizacionParcial.size();
                        final List<String> noActualizados = personasIBActualizacionParcial.stream().map(r -> r.getRut())
                                .collect(Collectors.toList());
                        rutsNoActualizados.addAll(noActualizados);
                    }
                }
            }
            // Escritura de Xml de Auditoria
            /*
             * Se debe de generar de momento una auditoria con los dos campos a ceros
             */
            totalPersonasOK = (numPersonasProcesadas) - (contadorLlamadasErroneas + contadorPersonasNoActualizadas);
            totalPersonasNOOK = (contadorLlamadasErroneas + contadorPersonasNoActualizadas);
            xmlAuditoria = new AuditoriaConsultaRegistroCivilDto(totalPersonasOK, totalPersonasNOOK);
            this.logger.info("Proceso de envio de la Consulta de Registro Civil- Finaliza correctamente");
            resultado = ResultadoIntegracionEnum.OK;

        } catch (final Exception e) {
            this.logger.info("Proceso de envio de la Consulta de Registro Civil - Finaliza con error no controlado");
            xmlAuditoria = new AuditoriaConsultaRegistroCivilDto(totalPersonasOK, totalPersonasNOOK);
            xmlAuditoria.setTrazaExcepcion(e);
            xmlAuditoria.setDescripcionResultado(e.getMessage());
            resultado = ResultadoIntegracionEnum.ERROR;

        } finally {
            final BneAuditoriaIntegracionDto<AuditoriaConsultaRegistroCivilDto> a = new BneAuditoriaIntegracionDto<>();
            a.setPersistable(true);
            a.setAccion(AccionAuditoriaIntegracionEnum.ENVIO_CONSULTA_REGISTRO_CIVIL);
            a.setFecha(DateUtils.now());
            a.setResultado(resultado);
            a.setDatosXml(xmlAuditoria);
            context.setResult(a);

            sw.stop();
            final long elapsed = sw.elapsed(TimeUnit.MILLISECONDS);
            this.logger.info("Finalizado job: Reporte Consulta de Registro Civil. Tiempo total: {}s; Tiempo por persona: {}ms",
                    sw.elapsed(TimeUnit.SECONDS), (elapsed > 0 && numPersonasProcesadas > 0) ? (elapsed / numPersonasProcesadas) : "N/A");
        }
    }

}
