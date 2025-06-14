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
import es.altia.bne.model.entities.dto.auditoria.AuditoriaReseteaSecuenciaOfertasDto;
import es.altia.bne.model.entities.enumerados.AccionAuditoriaIntegracionEnum;
import es.altia.bne.model.entities.enumerados.ResultadoIntegracionEnum;
import es.altia.bne.model.exception.DataAccessException;
import es.altia.bne.service.ITareasAdministrativasQuartzService;

@Component("jobReseteSecuenciaOfertas")
@Scope("prototype")
@DisallowConcurrentExecution
public class ReseteaSecuenciaOfertasJob implements Job {

    @Override
    public void execute(final JobExecutionContext context) throws JobExecutionException {
        ResultadoIntegracionEnum resultado = ResultadoIntegracionEnum.ERROR;
        AuditoriaReseteaSecuenciaOfertasDto xmlAuditoria = null;
        try {
            LOGGER.info("Reiniciando secuencia para el ID de ofertas.");
            xmlAuditoria = this.tareasQuartz.reseteaSecuenciaOfertas();
            resultado = ResultadoIntegracionEnum.OK;

        } catch (final DataAccessException e) {
            final Throwable rootCause = Throwables.getRootCause(e);
            LOGGER.error("Error al reiniciar la secuencia para ofertas.");
            LOGGER.error("Excepción:", rootCause);
            xmlAuditoria = new AuditoriaReseteaSecuenciaOfertasDto();
            xmlAuditoria.setTrazaExcepcion(rootCause);
            xmlAuditoria.setDescripcionResultado(rootCause.getMessage());

        } finally {
            // Grabamos auditoría
            final BneAuditoriaIntegracionDto<AuditoriaReseteaSecuenciaOfertasDto> a = new BneAuditoriaIntegracionDto<>();
            a.setAccion(AccionAuditoriaIntegracionEnum.RESETEA_SECUENCIA_OFERTAS);
            a.setFecha(DateUtils.now());
            a.setResultado(resultado);
            a.setDatosXml(xmlAuditoria);
            context.setResult(a);
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(ReseteaSecuenciaOfertasJob.class);

    @Autowired
    private ITareasAdministrativasQuartzService tareasQuartz;
}
