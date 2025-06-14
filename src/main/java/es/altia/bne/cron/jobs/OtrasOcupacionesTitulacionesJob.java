package es.altia.bne.cron.jobs;

import java.time.LocalDate;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

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

import es.altia.bne.comun.constantes.AccionesAuditoriaExtra;
import es.altia.bne.comun.util.date.DateUtils;
import es.altia.bne.model.entities.dto.BneAuditoriaDto;
import es.altia.bne.model.entities.dto.BneAuditoriaIntegracionDto;
import es.altia.bne.model.entities.dto.auditoria.AuditoriaOtrasOcupacionesDto;
import es.altia.bne.model.entities.enumerados.AccionAuditoriaIntegracionEnum;
import es.altia.bne.model.entities.enumerados.ResultadoIntegracionEnum;
import es.altia.bne.service.IAuditoriaService;
import es.altia.bne.service.IMailService;

@Scope("prototype")
@Component("jobOcupaciones")
@DisallowConcurrentExecution
public class OtrasOcupacionesTitulacionesJob implements Job {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Value("${bne.mail.to.otrasOcupaciones}")
    private String destinatariosEmail;

    @Value("${bne.job.otrasTitulacionesOcupaciones.tituloOcupaciones}")
    private String tituloOcupaciones;

    @Value("${bne.job.otrasTitulacionesOcupaciones.tituloTitulaciones}")
    private String tituloTitulaciones;

    @Value("${bne.job.otrasTitulacionesOcupaciones.tituloNuevaOfertaOcupaciones}")
    private String tituloNuevaOfertaOcupaciones;

    @Value("${bne.job.otrasTitulacionesOcupaciones.tituloNuevaOfertaTitulaciones}")
    private String tituloNuevaOfertaTitulaciones;

    @Autowired
    private IAuditoriaService auditoriaService;

    @Autowired
    private IMailService mailService;

    @Override
    public void execute(final JobExecutionContext context) throws JobExecutionException {
        ResultadoIntegracionEnum resultado = ResultadoIntegracionEnum.ERROR;
        AuditoriaOtrasOcupacionesDto xmlAuditoria = null;
        try {
            final Date firstDayOfLastMonth = DateUtils
                    .asDate(LocalDate.now().atTime(0, 0, 0, 0).with(ChronoField.DAY_OF_MONTH, 1).minus(1, ChronoUnit.MONTHS));
            final Date firstDayOfThisMonth = DateUtils.asDate(LocalDate.now().atTime(0, 0, 0, 0).with(ChronoField.DAY_OF_MONTH, 1));

            this.logger.info("Iniciando job: Reporte Excel BNE_AUDITORIA_EXTRA");
            final List<BneAuditoriaDto> bneAuditoriaTitulaciones, bneAuditoriaNuevaOfeOcu, bneAuditoriaNuevaOfeTitu,
                    bneAuditoriaOcupaciones, bneAuditoriaInfoLaboral;
            // Para las acciones EXPERIENCIA_LABORAL_OCUPACION_OTROS y INFO_LABORAL_OCUPACION_OTROS se utiliza los mismos excel por lo cual
            // solo se hace una ejecucion del metodo del MailService
            // Ocupaciones
            bneAuditoriaOcupaciones = this.auditoriaService.buscarAuditoria(firstDayOfLastMonth, firstDayOfThisMonth,
                    AccionesAuditoriaExtra.EXPERIENCIA_LABORAL_OCUPACION_OTROS.id());
            // info_laboral ocupacion
            bneAuditoriaInfoLaboral = this.auditoriaService.buscarAuditoria(firstDayOfLastMonth, firstDayOfThisMonth,
                    AccionesAuditoriaExtra.INFO_LABORAL_OCUPACION_OTROS.id());
            // mezcla de excel
            this.mailService.enviarReporteJobOtrasOcupacionesInfoLaboral(bneAuditoriaOcupaciones, bneAuditoriaInfoLaboral,
                    this.destinatariosEmail, this.tituloOcupaciones, "ocupaciones",
                    AccionesAuditoriaExtra.EXPERIENCIA_LABORAL_OCUPACION_OTROS.id(),
                    AccionesAuditoriaExtra.INFO_LABORAL_OCUPACION_OTROS.id());

            // Titulaciones
            bneAuditoriaTitulaciones = this.auditoriaService.buscarAuditoria(firstDayOfLastMonth, firstDayOfThisMonth,
                    AccionesAuditoriaExtra.NIVEL_EDUCACIONAL_OTRAS_TITULACIONES.id());
            this.mailService.enviarReporteJobOtrasTitulaciones(bneAuditoriaTitulaciones, this.destinatariosEmail, this.tituloTitulaciones,
                    "titulaciones", AccionesAuditoriaExtra.NIVEL_EDUCACIONAL_OTRAS_TITULACIONES.id());

            // Nuevas Ofertas Ocupaciones
            bneAuditoriaNuevaOfeOcu = this.auditoriaService.buscarAuditoria(firstDayOfLastMonth, firstDayOfThisMonth,
                    AccionesAuditoriaExtra.NUEVA_OFERTA_OCUPACION_OTROS.id());
            this.mailService.enviarReporteJobNuevasOfertas(bneAuditoriaNuevaOfeOcu, this.destinatariosEmail,
                    this.tituloNuevaOfertaOcupaciones, "ocupaciones - nueva oferta laboral",
                    AccionesAuditoriaExtra.NUEVA_OFERTA_OCUPACION_OTROS.id());

            // Nuevas Ofertas Titulaciones
            bneAuditoriaNuevaOfeTitu = this.auditoriaService.buscarAuditoria(firstDayOfLastMonth, firstDayOfThisMonth,
                    AccionesAuditoriaExtra.NUEVA_OFERTA_OTRAS_TITULACIONES.id());
            this.mailService.enviarReporteJobNuevasOfertas(bneAuditoriaNuevaOfeTitu, this.destinatariosEmail,
                    this.tituloNuevaOfertaTitulaciones, "titulaciones - nueva oferta laboral",
                    AccionesAuditoriaExtra.NUEVA_OFERTA_OTRAS_TITULACIONES.id());

            // Escritura de Xml de Auditoria
            xmlAuditoria = new AuditoriaOtrasOcupacionesDto(bneAuditoriaOcupaciones, bneAuditoriaInfoLaboral, bneAuditoriaTitulaciones,
                    bneAuditoriaNuevaOfeOcu, bneAuditoriaNuevaOfeTitu);

            this.logger.info("Proceso de envio de Excel BNE_AUDITORIA_EXTRA - Finaliza correctamente");
            resultado = ResultadoIntegracionEnum.OK;

        } catch (final Exception e) {
            this.logger.info("Proceso de envio de Excel BNE_AUDITORIA_EXTRA - Finaliza con error no controlado");
            xmlAuditoria = new AuditoriaOtrasOcupacionesDto();
            xmlAuditoria.setTrazaExcepcion(e);
            xmlAuditoria.setDescripcionResultado(e.getMessage());
            resultado = ResultadoIntegracionEnum.ERROR;

        } finally {
            final BneAuditoriaIntegracionDto<AuditoriaOtrasOcupacionesDto> a = new BneAuditoriaIntegracionDto<>();
            a.setPersistable(true);
            a.setAccion(AccionAuditoriaIntegracionEnum.ENVIA_MAIL_OTRAS_OCUPACIONES);
            a.setFecha(DateUtils.now());
            a.setResultado(resultado);
            a.setDatosXml(xmlAuditoria);
            context.setResult(a);
        }

    }

}
