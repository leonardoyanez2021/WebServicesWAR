package es.altia.bne.cron.jobs;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.google.common.base.Throwables;

import es.altia.bne.comun.exception.SituacionAnomalaException;
import es.altia.bne.comun.util.date.DateUtils;
import es.altia.bne.model.entities.dto.BneAuditoriaIntegracionDto;
import es.altia.bne.model.entities.dto.auditoria.AuditoriaActualizarEstadoEntrevistasDto;
import es.altia.bne.model.entities.enumerados.AccionAuditoriaIntegracionEnum;
import es.altia.bne.model.entities.enumerados.ResultadoIntegracionEnum;
import es.altia.bne.model.exception.DataAccessException;
import es.altia.bne.service.sms.ISmsGatewayService;

@Component("jobActualizarEstadoEntrevistas")
@Scope("prototype")
@DisallowConcurrentExecution
public class ActualizarEstadoEntrevistasJob implements Job {

    private static final Logger LOGGER = LoggerFactory.getLogger(ActualizarEstadoEntrevistasJob.class);

    @Autowired
    private ISmsGatewayService smsGatewayService;

    @Override
    public void execute(final JobExecutionContext context) throws JobExecutionException {

        LOGGER.info("Iniciando proceso de actualización del estado de las entrevistas que llevan 96 horas sin aceptacion o rechazo.");
        AuditoriaActualizarEstadoEntrevistasDto xmlAuditoria = null;
        ResultadoIntegracionEnum resultado = ResultadoIntegracionEnum.ERROR;

        try {

            xmlAuditoria = this.smsGatewayService.actualizaEstadoEntrevistas();
            resultado = ResultadoIntegracionEnum.OK;

        } catch (final SituacionAnomalaException | DataAccessException e) {

            final Throwable rootCause = Throwables.getRootCause(e);
            LOGGER.error("Error al actualizar el estado de las entrevistas.");
            LOGGER.error("Excepción:", rootCause);

            xmlAuditoria = new AuditoriaActualizarEstadoEntrevistasDto();
            xmlAuditoria.setTrazaExcepcion(rootCause);
            xmlAuditoria.setDescripcionResultado(rootCause.getMessage());

        } finally {

            final BneAuditoriaIntegracionDto<AuditoriaActualizarEstadoEntrevistasDto> auditoria = new BneAuditoriaIntegracionDto<>();
            auditoria.setPersistable(true);
            auditoria.setAccion(AccionAuditoriaIntegracionEnum.ACTUALIZA_ESTADO_ENTREVISTAS);
            auditoria.setDatosXml(xmlAuditoria);
            auditoria.setDatosXml(null);
            auditoria.setFecha(DateUtils.now());
            auditoria.setResultado(resultado);
            context.setResult(auditoria);
        }
    }
}
