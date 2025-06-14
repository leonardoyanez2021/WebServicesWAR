package es.altia.bne.cron.jobs;

import java.util.Calendar;

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
import es.altia.bne.model.entities.dto.auditoria.AuditoriaGenerarCertificado21015Dto;
import es.altia.bne.model.entities.enumerados.AccionAuditoriaIntegracionEnum;
import es.altia.bne.model.entities.enumerados.ResultadoIntegracionEnum;
import es.altia.bne.service.api.common.certificados.anual.IGenerarCertificadoAnualService;

@DisallowConcurrentExecution
@Component("jobCertificadoAnual21015")
@Scope("prototype")
public class GenerarCertificados21015AnualJob implements Job {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private IGenerarCertificadoAnualService generarCertificadoAnualJobService;

    @Override
    public void execute(final JobExecutionContext context) throws JobExecutionException {
        ResultadoIntegracionEnum resultado = ResultadoIntegracionEnum.ERROR;
        AuditoriaGenerarCertificado21015Dto xmlAuditoria = null;

        try {

            final int year = Calendar.getInstance().get(Calendar.YEAR);
            this.generarCertificadoAnualJobService.obtenerOfertasAnualesByAno(year);
            resultado = ResultadoIntegracionEnum.OK;

        } catch (final Exception e) {
            this.logger.info("Proceso de envio de Excel BNE_AUDITORIA_GENERAR_CERTIFICADO_21015 - Finaliza con error no controlado");
            xmlAuditoria = new AuditoriaGenerarCertificado21015Dto();
            xmlAuditoria.setTrazaExcepcion(e);
            xmlAuditoria.setDescripcionResultado(e.getMessage());
            resultado = ResultadoIntegracionEnum.ERROR;

        } finally {
            final BneAuditoriaIntegracionDto<AuditoriaGenerarCertificado21015Dto> a = new BneAuditoriaIntegracionDto<>();
            a.setPersistable(true);
            a.setAccion(AccionAuditoriaIntegracionEnum.GENERAR_CERTIFICACION_21015);
            a.setFecha(DateUtils.now());
            a.setResultado(resultado);
            a.setDatosXml(xmlAuditoria);
            context.setResult(a);
        }

    }

}
