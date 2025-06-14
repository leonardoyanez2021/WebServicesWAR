package es.altia.bne.cron.jobs;

import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import javax.mail.MessagingException;

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

import es.altia.bne.comun.util.date.DateUtils;
import es.altia.bne.model.entities.dto.BneAuditoriaIntegracionDto;
import es.altia.bne.model.entities.dto.InformacionTablaDto;
import es.altia.bne.model.entities.dto.auditoria.AuditoriaLecturaTamanoTablasDto;
import es.altia.bne.model.entities.enumerados.AccionAuditoriaIntegracionEnum;
import es.altia.bne.model.entities.enumerados.ResultadoIntegracionEnum;
import es.altia.bne.model.exception.DataAccessException;
import es.altia.bne.service.IMailService;
import es.altia.bne.service.ITamanoTablasService;

@DisallowConcurrentExecution
@Component("jobLecturaTamanoTablas")
@Scope("prototype")
public class LecturaTamanoTablasJob implements Job {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Value("${bne.mail.to.reporteTablas}")
    private String destinatariosEmail;

    @Autowired
    private ITamanoTablasService tamanoTablasService;

    @Autowired
    private IMailService mailService;

    @Override
    public void execute(final JobExecutionContext context) throws JobExecutionException {
        ResultadoIntegracionEnum resultado = ResultadoIntegracionEnum.ERROR;
        AuditoriaLecturaTamanoTablasDto xmlAuditoria = null;
        try {
            final Calendar calendar = Calendar.getInstance();
            // final SimpleDateFormat sdf = new SimpleDateFormat("dd-M-yyyy");
            // final String dateInString = "14-11-2017";
            // final Date date = sdf.parse(dateInString);
            // calendar.setTime(date);

            calendar.setTime(DateUtils.getTodayMidnight());
            final Date today = calendar.getTime();
            calendar.add(Calendar.DATE, -1);
            final Date yesterday = calendar.getTime();
            calendar.add(Calendar.DATE, +2);
            final Date tomorrow = calendar.getTime();
            this.logger.info("Iniciando job: Reporte informacion tablas BBDD");
            List<InformacionTablaDto> informacionTablasDtoList;
            informacionTablasDtoList = this.tamanoTablasService.getDatosTablasByFecha(yesterday, today, tomorrow);
            this.mailService.enviarReporteJobInformacionTablasBBDD(informacionTablasDtoList, yesterday, today, this.destinatariosEmail);
            xmlAuditoria = new AuditoriaLecturaTamanoTablasDto(informacionTablasDtoList);
            this.logger.info("Proceso de envio de mail con información de las tablas de BBDD - Finaliza correctamente");
            resultado = ResultadoIntegracionEnum.OK;
        } catch (final IOException | MessagingException | DataAccessException e) {
            this.logger.info("Proceso de envio de mail con información de las tablas de BBDD - Finaliza con error no controlado");
            xmlAuditoria = new AuditoriaLecturaTamanoTablasDto();
            xmlAuditoria.setTrazaExcepcion(e);
            xmlAuditoria.setDescripcionResultado(e.getMessage());
            resultado = ResultadoIntegracionEnum.ERROR;
        } finally {
            final BneAuditoriaIntegracionDto<AuditoriaLecturaTamanoTablasDto> a = new BneAuditoriaIntegracionDto<>();
            a.setPersistable(true);
            a.setAccion(AccionAuditoriaIntegracionEnum.EVIA_MAIL_REPORTE_TAMANO_TABLAS);
            a.setFecha(DateUtils.now());
            a.setResultado(resultado);
            a.setDatosXml(xmlAuditoria);
            context.setResult(a);
        }

    }

}
