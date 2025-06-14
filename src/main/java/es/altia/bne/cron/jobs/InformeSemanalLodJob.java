package es.altia.bne.cron.jobs;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import es.altia.bne.comun.util.date.DateUtils;
import es.altia.bne.model.entities.dto.BneAuditoriaIntegracionDto;
import es.altia.bne.model.entities.dto.auditoria.AuditoriaInformeSemanalLod;
import es.altia.bne.model.entities.enumerados.AccionAuditoriaIntegracionEnum;
import es.altia.bne.model.entities.enumerados.ResultadoIntegracionEnum;
import es.altia.bne.service.api.jobs.lod.informe.IInformeSemanalLodService;

@DisallowConcurrentExecution
@Component("jobInformeSemanalLod")
@Scope("prototype")
public class InformeSemanalLodJob implements Job {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    IInformeSemanalLodService informeSemanalLodService;

    @Override
    public void execute(final JobExecutionContext context) throws JobExecutionException {
        ResultadoIntegracionEnum resultado = ResultadoIntegracionEnum.ERROR;
        AuditoriaInformeSemanalLod xmlAuditoria = null;

        try {

            final LocalDate currentDate = LocalDate.now();
            final LocalDate fechaUnaSemanaAntes = currentDate.minus(1, ChronoUnit.WEEKS);
            final Date date = Date.from(fechaUnaSemanaAntes.atStartOfDay(ZoneId.systemDefault()).toInstant());
            final Long numOfertasLod = this.informeSemanalLodService.getInformeLod(date);
            resultado = ResultadoIntegracionEnum.OK;
            xmlAuditoria = new AuditoriaInformeSemanalLod(numOfertasLod, DateUtils.now());

        } catch (final Exception e) {
            this.logger.error("Proceso de envio de Excel Informe Semanal LOD - Finaliza con error no controlado");
            xmlAuditoria = new AuditoriaInformeSemanalLod();
            xmlAuditoria.setTrazaExcepcion(e);
            xmlAuditoria.setDescripcionResultado(e.getMessage());
            resultado = ResultadoIntegracionEnum.ERROR;

        } finally {
            final BneAuditoriaIntegracionDto<AuditoriaInformeSemanalLod> a = new BneAuditoriaIntegracionDto<>();
            a.setPersistable(true);
            a.setAccion(AccionAuditoriaIntegracionEnum.INFORME_SEMANAL_LOD);
            a.setFecha(DateUtils.now());
            a.setResultado(resultado);
            a.setDatosXml(xmlAuditoria);
            context.setResult(a);
        }

    }
}
