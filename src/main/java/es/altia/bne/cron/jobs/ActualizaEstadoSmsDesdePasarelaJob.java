package es.altia.bne.cron.jobs;

import java.util.concurrent.TimeUnit;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.google.common.base.Stopwatch;
import com.google.common.base.Throwables;

import es.altia.bne.comun.exception.SituacionAnomalaException;
import es.altia.bne.comun.util.date.DateUtils;
import es.altia.bne.model.entities.dto.BneAuditoriaIntegracionDto;
import es.altia.bne.model.entities.dto.auditoria.AuditoriaConsultaEstadoSMSDto;
import es.altia.bne.model.entities.enumerados.AccionAuditoriaIntegracionEnum;
import es.altia.bne.model.entities.enumerados.ResultadoIntegracionEnum;
import es.altia.bne.model.exception.DataAccessException;
import es.altia.bne.service.sms.ISmsGatewayService;

@Component("jobActualizaEstadoSms")
@Scope("prototype")
@DisallowConcurrentExecution
public class ActualizaEstadoSmsDesdePasarelaJob implements Job {

    @Override
    public void execute(final JobExecutionContext context) {

        ResultadoIntegracionEnum resultado = ResultadoIntegracionEnum.ERROR;
        final BneAuditoriaIntegracionDto<AuditoriaConsultaEstadoSMSDto> audit = new BneAuditoriaIntegracionDto<>();
        audit.setPersistable(true);
        audit.setAccion(AccionAuditoriaIntegracionEnum.ACTUALIZA_ESTADO_SMS_PASARELA);
        AuditoriaConsultaEstadoSMSDto xmlResultado = null;

        try {
            LOGGER.info("Iniciando proceso de actualización del estado de los mensajes enviados a la pasarela SMS");
            final Stopwatch sw = Stopwatch.createStarted();

            // Ejecución principal del job
            xmlResultado = this.smsGatewayService.actualizaEstadoSmsDesdePasarela();
            audit.setDatosXml(xmlResultado);

            LOGGER.info("Finalizado proceso de actualización del estado de los mensajes enviados a la pasarela SMS ({}ms)",
                    sw.elapsed(TimeUnit.MILLISECONDS));
            resultado = ResultadoIntegracionEnum.OK;

        } catch (final SituacionAnomalaException | DataAccessException e) {
            xmlResultado = new AuditoriaConsultaEstadoSMSDto();
            xmlResultado.setDescripcionResultado(e.getMessage());
            xmlResultado.setTrazaExcepcion(e);

            audit.setDatosXml(xmlResultado);

            LOGGER.error("Error al actualizar el estado de los SMS desde la pasarela.");
            final Throwable rootCause = Throwables.getRootCause(e);
            LOGGER.error("Excepción: ", rootCause);

        } finally {
            // Genera la auditoría al contexto auditoría
            audit.setFecha(DateUtils.now());
            audit.setResultado(resultado);
            context.setResult(audit);

        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(ActualizaEstadoSmsDesdePasarelaJob.class);

    @Autowired
    private ISmsGatewayService smsGatewayService;

}
