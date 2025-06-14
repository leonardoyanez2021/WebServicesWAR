package es.altia.bne.cron.jobs;

import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import es.altia.bne.comun.util.date.DateUtils;
import es.altia.bne.model.dao.entities.impl.ActualizarInfoRegCivilDAO.InfoPersonaActualizarRC;
import es.altia.bne.model.entities.dto.BneAuditoriaIntegracionDto;
import es.altia.bne.model.entities.dto.auditoria.AuditoriaActualizarInfoRegCivilDto;
import es.altia.bne.model.entities.enumerados.AccionAuditoriaIntegracionEnum;
import es.altia.bne.model.entities.enumerados.ResultadoIntegracionEnum;
import es.altia.bne.model.exception.DataAccessException;
import es.altia.bne.service.IActualizarInfoRegCivilService;
import es.altia.bne.service.exception.RegistroCivilMaxDailyTransactionsExceededException;
import es.altia.bne.service.exception.TransaccionRegistroCivilException;

@DisallowConcurrentExecution
@Component("jobActualizarInfoRegCivil")
@Scope("prototype")
public class ActualizarInfoRegCivilJob implements Job {

    @Autowired
    IActualizarInfoRegCivilService actualizarInfoRegCivilService;
    private static final int NUM_HILOS = 30;
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Override
    public void execute(final JobExecutionContext context) throws JobExecutionException {
        final ExecutorService executor = Executors.newFixedThreadPool(NUM_HILOS);
        ResultadoIntegracionEnum resultado = ResultadoIntegracionEnum.ERROR;
        AuditoriaActualizarInfoRegCivilDto xmlAuditoria = null;
        try {

            // Extraigo el total pendiente de actualizar
            final int totalRegistros = this.actualizarInfoRegCivilService.getCountRutsActualizarInfoRC();
            final int registrosPorHilo = totalRegistros / NUM_HILOS;

            for (int i = 0; i < NUM_HILOS; i++) {
                // El último hilo maneja los registros restantes
                final int fin = i == NUM_HILOS - 1 ? totalRegistros : (i + 1) * registrosPorHilo;
                final int inicio = i * registrosPorHilo + 1;

                executor.execute(() -> {
                    try {
                        this.procesarRegistros(inicio, fin);
                    } catch (final DataAccessException e) {
                        this.logger.info("Hilo de proceso de actualizacion datos registro civil - Finaliza con error no controlado");
                    }
                });
            }

            this.logger.info("Proceso de actualizacion datos registro civil - Finalizado correctamente.");
            resultado = ResultadoIntegracionEnum.OK;

        } catch (final DataAccessException e) {

            this.logger.info("Proceso de actualizacion datos registro civil - Finaliza con error no controlado");
            xmlAuditoria = new AuditoriaActualizarInfoRegCivilDto();
            xmlAuditoria.setTrazaExcepcion(e);
            xmlAuditoria.setDescripcionResultado(e.getMessage());
            resultado = ResultadoIntegracionEnum.ERROR;

        } finally {

            final BneAuditoriaIntegracionDto<AuditoriaActualizarInfoRegCivilDto> a = new BneAuditoriaIntegracionDto<>();
            a.setPersistable(true);
            a.setAccion(AccionAuditoriaIntegracionEnum.ACTUALIZA_INFO_REGISTRO_CIVIL);
            a.setFecha(DateUtils.now());
            a.setResultado(resultado);
            a.setDatosXml(xmlAuditoria);
            context.setResult(a);
        }

    }

    @Async
    private void procesarRegistros(final int inicio, final int fin) throws DataAccessException {
        AuditoriaActualizarInfoRegCivilDto xmlAuditoria;
        final Date horaInicio;
        final Date horaFin;
        Integer numPersonasProcesadas = 0;
        final Integer tiempoMedioPersona;
        int contadorLlamadasErroneas = 0;
        int contadorPersonasNoActualizadas = 0;
        final LinkedList<String> rutsConProblemasTransaccion = new LinkedList<>();
        final LinkedList<String> rutsNoActualizados = new LinkedList<>();
        this.logger.info("Comenzando actualizacion datos registro civil desde registro " + inicio + " hasta " + fin);
        horaInicio = DateUtils.now();

        for (int i = inicio; i <= fin; i = i + 3000) {

            final List<InfoPersonaActualizarRC> personas = this.actualizarInfoRegCivilService.getRutsActualizarMasivoInfoRC(inicio, 3000);
            // final List<InfoPersonaActualizarRC> personas = this.actualizarInfoRegCivilService.getRutsActualizarInfoRC(1000);

            if (null != personas && !personas.isEmpty()) {

                numPersonasProcesadas = numPersonasProcesadas + personas.size();

                for (final InfoPersonaActualizarRC info : personas) {

                    try {
                        this.logger.debug("Actualizando datos RC de {}{}", info.getNumDocumento(), info.getDigitoVerificador());
                        this.actualizarInfoRegCivilService.actualizarPersonaRC(info);

                    } catch (final RegistroCivilMaxDailyTransactionsExceededException | TransaccionRegistroCivilException e) {
                        rutsConProblemasTransaccion.add(info.getNumDocumento() + "-" + info.getDigitoVerificador());
                        contadorLlamadasErroneas++;
                    } catch (final Exception e) {
                        rutsNoActualizados.add(info.getNumDocumento() + "-" + info.getDigitoVerificador());
                        contadorPersonasNoActualizadas++;
                    }

                }

            }
        }

        horaFin = DateUtils.now();
        tiempoMedioPersona = (int) (((horaFin.getTime() - horaInicio.getTime()) / numPersonasProcesadas));
        this.logger.info("FIN Actualizacion datos RC a las {}, numero personas procesadas {}", horaFin, numPersonasProcesadas);
        xmlAuditoria = new AuditoriaActualizarInfoRegCivilDto(horaInicio, horaFin, numPersonasProcesadas, tiempoMedioPersona,
                contadorPersonasNoActualizadas, rutsNoActualizados, contadorLlamadasErroneas, rutsConProblemasTransaccion);
    }

}
