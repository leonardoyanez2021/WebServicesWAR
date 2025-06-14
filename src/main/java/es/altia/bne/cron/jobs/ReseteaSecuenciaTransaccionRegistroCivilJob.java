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

import es.altia.bne.comun.util.date.DateUtils;
import es.altia.bne.model.entities.dto.BneAuditoriaIntegracionDto;
import es.altia.bne.model.entities.dto.auditoria.AuditoriaReseteaSecuenciaTransaccionRegistroCivilDto;
import es.altia.bne.model.entities.enumerados.AccionAuditoriaIntegracionEnum;
import es.altia.bne.model.entities.enumerados.ResultadoIntegracionEnum;
import es.altia.bne.model.exception.DataAccessException;
import es.altia.bne.service.ITareasAdministrativasQuartzService;

@Component("jobReseteSecuenciaTransaccionRegistroCivil")
@Scope("prototype")
@DisallowConcurrentExecution
public class ReseteaSecuenciaTransaccionRegistroCivilJob implements Job {

    @Override
    public void execute(final JobExecutionContext context) throws JobExecutionException {
        ResultadoIntegracionEnum resultado = ResultadoIntegracionEnum.ERROR;
        AuditoriaReseteaSecuenciaTransaccionRegistroCivilDto xmlAuditoria = null;
        try {
            LOGGER.info("Reiniciando secuencia para el ID de transacciones en las consultas al registro civil.");
            xmlAuditoria = this.tareasQuartz.resetSecuenciaTransaccionesRegistroCivil();
            resultado = ResultadoIntegracionEnum.OK;
        } catch (final DataAccessException e) {
            final Throwable rootCause = Throwables.getRootCause(e);
            ReseteaSecuenciaTransaccionRegistroCivilJob.LOGGER
                    .error("Error al reiniciar la secuencia para transacciones del registro civil.");
            LOGGER.error("Excepción:", rootCause);
            xmlAuditoria = new AuditoriaReseteaSecuenciaTransaccionRegistroCivilDto();
            xmlAuditoria.setTrazaExcepcion(rootCause);
            resultado = ResultadoIntegracionEnum.ERROR;
        } finally {
            // Grabamos auditoría
            final BneAuditoriaIntegracionDto<AuditoriaReseteaSecuenciaTransaccionRegistroCivilDto> a = new BneAuditoriaIntegracionDto<>();
            a.setPersistable(true);
            a.setAccion(AccionAuditoriaIntegracionEnum.RESETEA_SECUENCIA_TRANSACCION_RC);
            a.setFecha(DateUtils.now());
            a.setResultado(resultado);
            a.setDatosXml(xmlAuditoria);
            context.setResult(a);
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(ReseteaSecuenciaTransaccionRegistroCivilJob.class);

    @Autowired
    private ITareasAdministrativasQuartzService tareasQuartz;
}
