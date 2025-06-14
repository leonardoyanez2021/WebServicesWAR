package es.altia.bne.cron.jobs.ptp;

import java.io.IOException;

import javax.mail.MessagingException;

import org.dozer.MappingException;
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
import es.altia.bne.model.entities.dto.auditoria.ptp.AuditoriaPtpInformeSemanalDto;
import es.altia.bne.model.entities.enumerados.AccionAuditoriaIntegracionEnum;
import es.altia.bne.model.entities.enumerados.ResultadoIntegracionEnum;
import es.altia.bne.model.exception.DataAccessException;
import es.altia.bne.service.api.jobs.ptp.informe.IPtpInformeSemanalOfertasService;

@Scope("prototype")
@Component("ptpInformeSemanalJob")
@DisallowConcurrentExecution
public class PtpInformeSemanalJob implements Job {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private IPtpInformeSemanalOfertasService informesService;

    @Override
    public void execute(final JobExecutionContext context) throws JobExecutionException {
        ResultadoIntegracionEnum resultado = ResultadoIntegracionEnum.ERROR;
        final AuditoriaPtpInformeSemanalDto xmlAuditoria = new AuditoriaPtpInformeSemanalDto();
        ;
        try {
            this.logger.info("Iniciando job de informe semanal Prácticas Técnico profesionales");

            this.informesService.envioInformeSemanalOfertasALiceos(xmlAuditoria);
            this.informesService.envioInformeSemanalOfertasAEmpresas(xmlAuditoria);

            resultado = ResultadoIntegracionEnum.OK;

        } catch (final DataAccessException | MappingException | IOException | MessagingException e) {
            final Throwable rootCause = Throwables.getRootCause(e);
            this.logger.error("Error en job de informe semanal", rootCause);

            xmlAuditoria.setTrazaExcepcion(rootCause);
            xmlAuditoria.setDescripcionResultado(rootCause.getMessage());
            resultado = ResultadoIntegracionEnum.ERROR;

        } finally {
            // Grabamos auditoría
            final BneAuditoriaIntegracionDto<AuditoriaPtpInformeSemanalDto> a = new BneAuditoriaIntegracionDto<>();

            a.setAccion(AccionAuditoriaIntegracionEnum.PTP_INFORME_SEMANAL_REGISTRO_OFERTAS);
            a.setFecha(DateUtils.now());
            a.setResultado(resultado);
            a.setDatosXml(xmlAuditoria);
            context.setResult(a);
        }
    }

}
