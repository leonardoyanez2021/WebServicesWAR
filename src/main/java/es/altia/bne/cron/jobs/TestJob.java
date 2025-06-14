package es.altia.bne.cron.jobs;

import java.util.Date;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import es.altia.bne.comun.util.date.DateUtils;
import es.altia.bne.model.entities.dto.BneAuditoriaIntegracionDto;
import es.altia.bne.model.entities.dto.auditoria.AuditoriaTestJobDto;
import es.altia.bne.model.entities.enumerados.AccionAuditoriaIntegracionEnum;
import es.altia.bne.model.entities.enumerados.ResultadoIntegracionEnum;

public class TestJob implements Job {

    protected final Logger log = LoggerFactory.getLogger(this.getClass());

    private String someParam1;
    private String someParam2;

    public String getSomeParam1() {
        return this.someParam1;
    }

    public void setSomeParam1(final String someParam1) {
        this.someParam1 = someParam1;
    }

    public String getSomeParam2() {
        return this.someParam2;
    }

    public void setSomeParam2(final String someParam2) {
        this.someParam2 = someParam2;
    }

    @Override
    public void execute(final JobExecutionContext context) throws JobExecutionException {
        final AuditoriaTestJobDto xmlAuditoria = new AuditoriaTestJobDto();
        final Date now = DateUtils.now();
        try {
            this.log.warn("Lanzando Test Job [{}, {}] a las {}", this.someParam1, this.someParam2, now);
            System.out.println("Lanzando Test Job [" + this.someParam1 + ", " + this.someParam2 + "] a las:" + now);

            // Añadimos auditoría a contexto
            xmlAuditoria.setParam1(this.someParam1);
            xmlAuditoria.setParam2(this.someParam2);
            xmlAuditoria.setParam3("Parametro 3");

        } catch (final Exception error) {

            xmlAuditoria.setTrazaExcepcion(error);
            xmlAuditoria.setResultado(ResultadoIntegracionEnum.ERROR);
            this.log.error(error.getMessage(), error);

        } finally {
            final BneAuditoriaIntegracionDto<AuditoriaTestJobDto> a = new BneAuditoriaIntegracionDto<>();
            a.setPersistable(false);
            a.setAccion(AccionAuditoriaIntegracionEnum.CARGA_FICHEROS_AFC);
            a.setFecha(now);
            a.setResultado(ResultadoIntegracionEnum.OK);
            a.setDatosXml(xmlAuditoria);
            a.setToReport(true);
            context.setResult(a);
        }

    }

}
